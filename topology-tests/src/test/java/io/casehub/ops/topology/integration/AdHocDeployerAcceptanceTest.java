package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.desiredstate.api.DesiredStateEventTypes;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.MergedEventSource;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.runtime.DefaultActualStateAdapterRouter;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.desiredstate.runtime.DefaultNodeProvisionerRouter;
import io.casehub.desiredstate.runtime.FaultPolicyEngine;
import io.casehub.desiredstate.runtime.NoOpHumanNodeHandler;
import io.casehub.desiredstate.runtime.NoOpPendingApprovalHandler;
import io.casehub.desiredstate.runtime.NodeStepExecutor;
import io.casehub.desiredstate.runtime.ReconciliationLoop;
import io.casehub.desiredstate.runtime.SimpleTransitionExecutor;
import io.casehub.desiredstate.runtime.TransitionPlanner;
import io.casehub.ops.api.SpecHashStore;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyNodeSpec;
import io.casehub.ops.api.deployment.LlmDecision;
import io.casehub.ops.claudony.ClaudonyActualStateAdapter;
import io.casehub.ops.claudony.ClaudonyNodeProvisioner;
import io.casehub.ops.claudony.HttpClaudonyFleetClient;
import io.casehub.ops.service.adaptation.AdaptationRule;
import io.casehub.ops.service.adaptation.AdaptationRuleCompiler;
import io.casehub.ops.service.adaptation.LlmTriggerEvaluator;
import io.casehub.ops.service.adaptation.TenantAdaptationState;
import io.casehub.ops.testing.audit.AuditAssertions;
import io.casehub.ops.testing.audit.AuditCapture;
import io.casehub.ops.testing.audit.CapturingTransitionExecutor;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import io.casehub.ras.api.ActiveSituation;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-live")
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class AdHocDeployerAcceptanceTest extends TopologyTestBase {

    private static final String TENANT = "l12-acceptance";
    private static final String TOPOLOGY_PATH =
            "topologies/deployment/12-adhoc-llm-deployer/12-adhoc-llm-deployer.yaml";
    private static final String ADAPTATIONS_PATH =
            "topologies/deployment/12-adhoc-llm-deployer/12-adhoc-llm-deployer-adaptations.yaml";

    private static final List<Map<String, String>> CATALOG_ENTRIES = List.of(
            Map.of("name", "product-x-specialist", "role", "specialist",
                   "domain", "product-x", "archetype", "expert",
                   "knowledge-base", "kb-product-x"),
            Map.of("name", "product-y-specialist", "role", "specialist",
                   "domain", "product-y", "archetype", "expert",
                   "knowledge-base", "kb-product-y"),
            Map.of("name", "escalation-specialist", "role", "escalation",
                   "domain", "incident-response", "archetype", "coordinator",
                   "knowledge-base", "kb-escalation"));

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    @SuppressWarnings("unchecked")
    void yamlAdaptationRuleDeploysWorkingSpecialist() throws Exception {
        System.out.println("\n═══ Level 12: Ad-hoc LLM Deployer ═══");
        System.out.println("Deploying base topology with deployer agent...");

        ObjectMapper            mapper        = new ObjectMapper().registerModule(new JavaTimeModule());
        ObjectMapper            yamlMapper    = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        HttpClaudonyFleetClient fleetClient   = new HttpClaudonyFleetClient(claudonyUrl, mapper);
        var                     specHashStore = new SpecHashStore();
        var                     provisioner   = new ClaudonyNodeProvisioner(fleetClient, specHashStore);
        var                     actualAdapter = new ClaudonyActualStateAdapter(fleetClient, specHashStore);
        var                     auditCapture  = new AuditCapture();

        var planner           = new TransitionPlanner();
        var provisionerRouter = new DefaultNodeProvisionerRouter(List.of(provisioner));
        var nodeExecutor = new NodeStepExecutor(
                provisionerRouter,
                new NoOpHumanNodeHandler(),
                new NoOpPendingApprovalHandler(),
                (step, tid) -> new StepOutcome.Succeeded());
        var               executor          = new SimpleTransitionExecutor(nodeExecutor);
        var               capturingExecutor = new CapturingTransitionExecutor(executor, auditCapture);
        var               actualRouter      = new DefaultActualStateAdapterRouter(List.of(actualAdapter));
        MergedEventSource emptySource       = () -> Multi.createFrom().nothing();

        var registry = buildTypeRegistryFrom(ClaudonyNodeSpec.class);
        DesiredStateGraph baseGraph = compileSingleGraph(TOPOLOGY_PATH, registry);

        ReconciliationLoop loop = ReconciliationLoop.builder(
                                                            planner, capturingExecutor, actualRouter,
                                                            new FaultPolicyEngine(List.of()), emptySource)
                                                    .cloudEventSink(auditCapture::recordCloudEvent)
                                                    .debounceWindow(Duration.ofMillis(50))
                                                    .resyncInterval(Duration.ofMinutes(30))
                                                    .build();

        try {
            loop.start(TENANT, baseGraph);

            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            AuditAssertions.assertAllSucceeded(auditCapture);
            System.out.printf("✓ Base topology deployed — %d nodes provisioned%n", baseGraph.nodes().size());

            System.out.println("\nCompiling YAML adaptation rules...");
            Map<String, Object> rawYaml;
            try (InputStream is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(ADAPTATIONS_PATH)) {
                assertThat(is).as("Adaptations YAML must exist").isNotNull();
                rawYaml = yamlMapper.readValue(is, Map.class);
            }
            var crossDomainRegistry = Map.<String, Class<?>>of(
                    "claudony_app", ClaudonyAppNodeSpec.class);
            var compiler = new AdaptationRuleCompiler(mapper);
            List<AdaptationRule> rules = compiler.compile(rawYaml, crossDomainRegistry,
                    new DefaultDesiredStateGraphFactory());
            rules = rules.stream()
                    .map(r -> r.withCatalogEntries(CATALOG_ENTRIES))
                    .toList();
            assertThat(rules).hasSize(1);
            System.out.printf("  ✓ Compiled %d adaptation rule: %s%n",
                    rules.size(), rules.get(0).name());

            System.out.println("\nInjecting RAS situation — surge alert...");
            var evaluator = new LlmTriggerEvaluator(
                    (agentName, prompt, timeout) -> Optional.of(
                            "{\"action\": \"deploy\", \"entry\": \"product-x-specialist\"}"));

            var state = new TenantAdaptationState(baseGraph, rules, Map.of());
            var situation = new ActiveSituation("surge-alert", "_singleton", "test", 0.8,
                    Map.of(), Instant.now(), Instant.now(), 0);
            state.updateSituation(situation);
            System.out.println("  ✓ Situation injected: surge-alert (confidence=0.8)");

            System.out.println("\nDeployer evaluating situation...");
            LlmDecision decision = evaluator.evaluate(
                    (io.casehub.ops.api.deployment.LlmEvaluatedTrigger) rules.get(0).trigger(),
                    situation, CATALOG_ENTRIES).orElseThrow();
            state.cacheLlmDecision(rules.get(0).name(), situation.situationId(), decision);
            assertThat(decision.isDeploy()).isTrue();
            System.out.printf("  ✓ Decision: DEPLOY %s%n", decision.selectedCatalogEntry());

            DesiredStateGraph adaptedGraph = baseGraph;
            Map<String, LlmDecision> cached = state.getCachedDecisions(rules.get(0).name());
            for (var entry : cached.entrySet()) {
                if (entry.getValue().isDeploy()) {
                    adaptedGraph = rules.get(0).apply(adaptedGraph, situation,
                            entry.getValue().selectedCatalogEntry());
                }
            }

            assertThat(adaptedGraph.nodes())
                    .containsKey(NodeId.of("specialist-product-x-specialist"));
            var specialistNode = adaptedGraph.nodes()
                    .get(NodeId.of("specialist-product-x-specialist"));
            var specialistSpec = (ClaudonyAppNodeSpec) specialistNode.spec();
            System.out.printf("  ✓ Specialist node added to graph: %s (domain=%s)%n",
                    specialistSpec.name(), specialistSpec.config().get("llm.domain"));

            System.out.println("\nProvisioning specialist via reconciliation...");
            auditCapture.clear();
            loop.updateDesired(TENANT, adaptedGraph);
            loop.requestReconciliation(TENANT);
            awaitNextCycle(loop, auditCapture);

            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            AuditAssertions.assertNoFailures(auditCapture);
            System.out.println("  ✓ Specialist provisioned by reconciliation loop");

            System.out.println("\nExercising specialist...");
            FleetLlmExerciser.LlmResponse response =
                    FleetLlmExerciser.exercise(
                            specialistSpec.name(), specialistSpec.config());
            FleetLlmExerciser.assertSubstantive(response);
            System.out.printf("  ✓ %s responded (%s/%s, %d chars)%n",
                    response.appId(), response.backendKey(), response.instanceId(),
                    response.content().length());

            System.out.println("\nResolving situation — removing specialist...");
            state.clearSituation("surge-alert");
            auditCapture.clear();
            loop.updateDesired(TENANT, baseGraph);
            loop.requestReconciliation(TENANT);
            awaitNextCycle(loop, auditCapture);

            AuditAssertions.assertCloudEventEmitted(auditCapture, DesiredStateEventTypes.RECONCILIATION_COMPLETED);
            System.out.println("  ✓ Situation resolved — specialist deprovisioned");
            System.out.println("  ✓ Full lifecycle: situation → deploy → exercise → resolve → deprovision");

        } finally {
            loop.stop(TENANT);
            loop.shutdown();
        }

        System.out.println("\n═══ Level 12: PASSED ═══\n");
    }

    private static void awaitNextCycle(ReconciliationLoop loop,
                                       AuditCapture auditCapture) throws Exception {
        int baseline = auditCapture.cloudEventCount(DesiredStateEventTypes.RECONCILIATION_COMPLETED);
        loop.requestReconciliation(TENANT);
        long deadline = System.currentTimeMillis() + 15_000;
        while (auditCapture.cloudEventCount(DesiredStateEventTypes.RECONCILIATION_COMPLETED) <= baseline) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(
                        "Reconciliation cycle did not complete within 15s. " +
                        "Events captured: " + auditCapture.cloudEvents().size());
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
    }
}

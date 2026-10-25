package io.casehub.ops.topology.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.desiredstate.api.Dependency;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.HumanGating;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.deployment.LlmDecision;
import io.casehub.ops.service.adaptation.AdaptationRule;
import io.casehub.ops.service.adaptation.AdaptationRuleCompiler;
import io.casehub.ops.service.adaptation.LlmTriggerEvaluator;
import io.casehub.ops.service.adaptation.TenantAdaptationState;
import io.casehub.ras.api.ActiveSituation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class Level12YamlRuleIntegrationTest {

    private static final String ADAPTATIONS_PATH =
            "topologies/deployment/12-adhoc-llm-deployer/12-adhoc-llm-deployer-adaptations.yaml";

    private final DefaultDesiredStateGraphFactory graphFactory = new DefaultDesiredStateGraphFactory();
    private final ObjectMapper jsonMapper = new ObjectMapper().findAndRegisterModules();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();

    private DesiredStateGraph baseGraph;
    private List<AdaptationRule> rules;

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

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        Map<String, Object> rawYaml;
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(ADAPTATIONS_PATH)) {
            assertThat(is).as("YAML file must exist on classpath: " + ADAPTATIONS_PATH).isNotNull();
            rawYaml = yamlMapper.readValue(is, Map.class);
        }

        baseGraph = buildBaseGraph();

        var crossDomainRegistry = Map.<String, Class<?>>of(
                "claudony_app", ClaudonyAppNodeSpec.class);

        var compiler = new AdaptationRuleCompiler(jsonMapper);
        rules = compiler.compile(rawYaml, crossDomainRegistry, graphFactory);
        rules = rules.stream()
                .map(r -> r.withCatalogEntries(CATALOG_ENTRIES))
                .toList();
    }

    @Test
    void yamlParsesOneAdaptationRule() {
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).name()).isEqualTo("deploy-specialist-on-surge");
    }

    @Test
    void fullLifecycle_deployAndRemoveSpecialist() {
        var evaluator = new LlmTriggerEvaluator(
                (agentName, prompt, timeout) -> Optional.of(
                        "{\"action\": \"deploy\", \"entry\": \"product-x-specialist\"}"));

        var state = new TenantAdaptationState(baseGraph, rules, Map.of());
        var situation = situation("surge-alert", 0.8);

        state.updateSituation(situation);
        LlmDecision decision = evaluator.evaluate(
                (io.casehub.ops.api.deployment.LlmEvaluatedTrigger) rules.get(0).trigger(),
                situation, CATALOG_ENTRIES).orElseThrow();
        state.cacheLlmDecision(rules.get(0).name(), situation.situationId(), decision);

        DesiredStateGraph adapted = baseGraph;
        Map<String, LlmDecision> cached = state.getCachedDecisions(rules.get(0).name());
        for (var entry : cached.entrySet()) {
            if (entry.getValue().isDeploy()) {
                adapted = rules.get(0).apply(adapted, situation,
                        entry.getValue().selectedCatalogEntry());
            }
        }

        assertThat(adapted.nodes()).containsKey(NodeId.of("specialist-product-x-specialist"));
        var specialistNode = adapted.nodes().get(NodeId.of("specialist-product-x-specialist"));
        assertThat(specialistNode.spec()).isInstanceOf(ClaudonyAppNodeSpec.class);
        var spec = (ClaudonyAppNodeSpec) specialistNode.spec();
        assertThat(spec.name()).isEqualTo("specialist-product-x-specialist");
        assertThat(spec.displayName()).isEqualTo("product-x-specialist");
        assertThat(spec.description()).isEqualTo("Ad-hoc specialist for product-x");
        assertThat(spec.image()).isEqualTo("casehubio/my-llm-app:latest");
        assertThat(spec.config()).containsEntry("llm.role", "specialist");
        assertThat(spec.config()).containsEntry("llm.domain", "product-x");
        assertThat(spec.config()).containsEntry("llm.archetype", "expert");
        assertThat(spec.config()).containsEntry("llm.knowledge-base", "kb-product-x");
        assertThat(spec.config()).containsEntry("llm.identity-source", "eidos");
        assertThat(spec.config()).containsEntry("llm.deployed-by", "deployer");
        assertThat(spec.config()).containsEntry("llm.source-situation", "surge-alert");

        assertThat(adapted.dependencies()).anyMatch(d ->
                d.from().equals(NodeId.of("specialist-product-x-specialist"))
                && d.to().equals(NodeId.of("engine")));

        state.clearSituation("surge-alert");
        Map<String, LlmDecision> remaining = state.getCachedDecisions(rules.get(0).name());
        assertThat(remaining).isEmpty();
    }

    @Test
    void llmRejectsDeployment() {
        var evaluator = new LlmTriggerEvaluator(
                (agentName, prompt, timeout) -> Optional.of(
                        "{\"action\": \"reject\"}"));

        var state = new TenantAdaptationState(baseGraph, rules, Map.of());
        var situation = situation("minor-alert", 0.6);
        state.updateSituation(situation);

        LlmDecision decision = evaluator.evaluate(
                (io.casehub.ops.api.deployment.LlmEvaluatedTrigger) rules.get(0).trigger(),
                situation, CATALOG_ENTRIES).orElseThrow();
        state.cacheLlmDecision(rules.get(0).name(), situation.situationId(), decision);

        assertThat(decision.isReject()).isTrue();

        DesiredStateGraph adapted = baseGraph;
        Map<String, LlmDecision> cached = state.getCachedDecisions(rules.get(0).name());
        for (var entry : cached.entrySet()) {
            if (entry.getValue().isDeploy()) {
                adapted = rules.get(0).apply(adapted, situation,
                        entry.getValue().selectedCatalogEntry());
            }
        }

        assertThat(adapted.nodes()).doesNotContainKey(NodeId.of("specialist-product-x-specialist"));
        assertThat(adapted.nodes().keySet()).isEqualTo(baseGraph.nodes().keySet());
    }

    @Test
    void concurrentSituations_deployMultipleSpecialists() {
        AtomicInteger callNum = new AtomicInteger(0);
        var evaluator = new LlmTriggerEvaluator(
                (agentName, prompt, timeout) -> {
                    int n = callNum.incrementAndGet();
                    String entry = n == 1 ? "product-x-specialist" : "product-y-specialist";
                    return Optional.of(
                            "{\"action\": \"deploy\", \"entry\": \"" + entry + "\"}");
                });

        var state = new TenantAdaptationState(baseGraph, rules, Map.of());

        var sit1 = situation("surge-1", 0.8);
        state.updateSituation(sit1);
        LlmDecision d1 = evaluator.evaluate(
                (io.casehub.ops.api.deployment.LlmEvaluatedTrigger) rules.get(0).trigger(),
                sit1, CATALOG_ENTRIES).orElseThrow();
        state.cacheLlmDecision(rules.get(0).name(), sit1.situationId(), d1);

        var sit2 = situation("surge-2", 0.9);
        state.updateSituation(sit2);
        LlmDecision d2 = evaluator.evaluate(
                (io.casehub.ops.api.deployment.LlmEvaluatedTrigger) rules.get(0).trigger(),
                sit2, CATALOG_ENTRIES).orElseThrow();
        state.cacheLlmDecision(rules.get(0).name(), sit2.situationId(), d2);

        DesiredStateGraph adapted = baseGraph;
        Map<String, LlmDecision> cached = state.getCachedDecisions(rules.get(0).name());
        for (var entry : cached.entrySet()) {
            if (entry.getValue().isDeploy()) {
                ActiveSituation sit = entry.getKey().equals(sit1.situationId()) ? sit1 : sit2;
                adapted = rules.get(0).apply(adapted, sit,
                        entry.getValue().selectedCatalogEntry());
            }
        }

        assertThat(adapted.nodes()).containsKey(NodeId.of("specialist-product-x-specialist"));
        assertThat(adapted.nodes()).containsKey(NodeId.of("specialist-product-y-specialist"));

        var specX = (ClaudonyAppNodeSpec) adapted.nodes()
                .get(NodeId.of("specialist-product-x-specialist")).spec();
        assertThat(specX.config()).containsEntry("llm.domain", "product-x");

        var specY = (ClaudonyAppNodeSpec) adapted.nodes()
                .get(NodeId.of("specialist-product-y-specialist")).spec();
        assertThat(specY.config()).containsEntry("llm.domain", "product-y");
    }

    @Test
    void cachedDecisionReusedOnRecompile() {
        AtomicInteger invokeCount = new AtomicInteger(0);
        var evaluator = new LlmTriggerEvaluator(
                (agentName, prompt, timeout) -> {
                    invokeCount.incrementAndGet();
                    return Optional.of(
                            "{\"action\": \"deploy\", \"entry\": \"product-x-specialist\"}");
                });

        var state = new TenantAdaptationState(baseGraph, rules, Map.of());
        var situation = situation("surge", 0.8);

        state.updateSituation(situation);
        var trigger = (io.casehub.ops.api.deployment.LlmEvaluatedTrigger) rules.get(0).trigger();

        if (state.getCachedDecision(rules.get(0).name(), situation.situationId()).isEmpty()) {
            LlmDecision d = evaluator.evaluate(trigger, situation, CATALOG_ENTRIES).orElseThrow();
            state.cacheLlmDecision(rules.get(0).name(), situation.situationId(), d);
        }

        if (state.getCachedDecision(rules.get(0).name(), situation.situationId()).isEmpty()) {
            evaluator.evaluate(trigger, situation, CATALOG_ENTRIES);
        }

        assertThat(invokeCount.get()).isEqualTo(1);
    }

    @Test
    void situationBelowThresholdDoesNotQualify() {
        var state = new TenantAdaptationState(baseGraph, rules, Map.of());
        var situation = situation("low-alert", 0.3);
        state.updateSituation(situation);

        List<ActiveSituation> qualifying = state.allQualifyingSituations(rules.get(0));
        assertThat(qualifying).isEmpty();
    }

    private DesiredStateGraph buildBaseGraph() {
        List<DesiredNode> nodes = new ArrayList<>();
        List<Dependency> deps = new ArrayList<>();

        var manifest = new ClaudonyAppNodeSpec("manifest", "Manifest",
                "Manifest service", "casehubio/manifest:latest", Map.of());
        nodes.add(new DesiredNode(NodeId.of("manifest"), manifest, HumanGating.NONE));

        var eidos = new ClaudonyAppNodeSpec("eidos", "Eidos",
                "Identity service", "casehubio/eidos:latest", Map.of());
        nodes.add(new DesiredNode(NodeId.of("eidos"), eidos, HumanGating.NONE));
        deps.add(new Dependency(NodeId.of("eidos"), NodeId.of("manifest")));

        var engine = new ClaudonyAppNodeSpec("engine", "Engine",
                "Engine service", "casehubio/engine:latest", Map.of());
        nodes.add(new DesiredNode(NodeId.of("engine"), engine, HumanGating.NONE));
        deps.add(new Dependency(NodeId.of("engine"), NodeId.of("eidos")));

        var deployer = new ClaudonyAppNodeSpec("deployer", "Deployer LLM",
                "Autonomous deployer", "casehubio/my-llm-app:latest",
                Map.of("llm.agent-id", "deployer",
                       "llm.name", "Deployer",
                       "llm.role", "deployer",
                       "llm.capability", "deployment-decision",
                       "llm.archetype", "orchestrator",
                       "llm.domain", "fleet-management",
                       "llm.identity-source", "eidos",
                       "deployer.specialist-catalog", "specialist-catalog"));
        nodes.add(new DesiredNode(NodeId.of("deployer"), deployer, HumanGating.NONE));
        deps.add(new Dependency(NodeId.of("deployer"), NodeId.of("engine")));

        return graphFactory.of(nodes, deps);
    }

    private static ActiveSituation situation(String id, double confidence) {
        return new ActiveSituation(id, "_singleton", "test", confidence,
                Map.of(), Instant.now(), Instant.now(), 0);
    }
}

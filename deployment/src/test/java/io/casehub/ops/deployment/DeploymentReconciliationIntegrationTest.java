package io.casehub.ops.deployment;

import io.casehub.desiredstate.api.*;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.desiredstate.runtime.TransitionPlanner;
import io.casehub.ops.api.deployment.*;
import io.casehub.ops.deployment.drift.*;
import io.casehub.ops.deployment.handler.*;
import io.casehub.ops.testing.*;
import io.casehub.ras.api.ChainMode;
import io.casehub.ras.api.SituationRegistrar;
import io.casehub.ras.api.SituationRegistration;
import io.casehub.ras.api.TriggerAction;
import io.casehub.ras.api.TriggerMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentReconciliationIntegrationTest {

    private static final String TENANCY_ID = "tenant-1";

    private DeploymentGoalLoader goalLoader;
    private DeploymentGoalCompiler compiler;
    private DeploymentNodeProvisioner provisioner;
    private DeploymentActualStateAdapter adapter;
    private TransitionPlanner planner;
    private DefaultDesiredStateGraphFactory graphFactory;

    private StubAgentRegistry agentRegistry;
    private StubEndpointRegistry endpointRegistry;
    private StubSituationRegistrar situationRegistrar;

    @BeforeEach
    void setUp() {
        agentRegistry = new StubAgentRegistry();
        var channelStore = new StubChannelStore();
        var bindingStore = new StubChannelBindingStore();
        var channelOps = new StubChannelOperations(channelStore, TENANCY_ID);
        endpointRegistry = new StubEndpointRegistry();
        var providerConfigStore = new DeploymentProviderConfigStore();
        var caseTypeHandler = new CaseTypeProvisionHandler();
        var trustProvider = new DeploymentTrustRoutingPolicyProvider();
        situationRegistrar = new StubSituationRegistrar();

        var specHashStore = new SpecHashStore();

        var driftCheckers = List.<NodeDriftChecker>of(
                new AgentDriftChecker(agentRegistry),
                new ChannelDriftChecker(channelStore, bindingStore),
                new CaseTypeDriftChecker(caseTypeHandler),
                new TrustPolicyDriftChecker(trustProvider),
                new EndpointDriftChecker(endpointRegistry),
                new DetectionDriftChecker(situationRegistrar));

        goalLoader = new DeploymentGoalLoader();
        compiler = new DeploymentGoalCompiler();
        provisioner = new DeploymentNodeProvisioner(
                agentRegistry,
                providerConfigStore,
                new ChannelProvisionHandler(channelOps),
                caseTypeHandler,
                new TrustPolicyProvisionHandler(trustProvider),
                new EndpointProvisionHandler(endpointRegistry),
                new DetectionProvisionHandler(situationRegistrar),
                specHashStore,
                (node, action, tenancyId) -> new io.casehub.ops.api.approval.ApprovalDecision.AutoApproved(),
                new io.casehub.ops.api.approval.InMemoryPlanStore());
        adapter = new DeploymentActualStateAdapter(driftCheckers, specHashStore);
        planner = new TransitionPlanner();
        graphFactory = new DefaultDesiredStateGraphFactory();
    }

    @Test
    void greenField_yamlLoad_provisionAll_loopClosure() {
        var goals = loadGoalsWithDetection();
        var desired = ((CompilationResult.SingleGraph) compiler.compile(goals, graphFactory)).graph();
        assertThat(desired.nodes()).hasSize(6);

        var actual = adapter.readActual(desired, TENANCY_ID);
        for (var status : actual.statuses().values()) {
            assertThat(status).isEqualTo(NodeStatus.ABSENT);
        }

        var plan = planner.plan(desired, actual);
        assertThat(plan.additions()).isNotEmpty();

        for (var step : plan.additions()) {
            if (step.action() == StepAction.PROVISION) {
                var result = provisioner.provision(step.node(), new ProvisionContext(TENANCY_ID, desired));
                assertThat(result).as("provisioning %s", step.node().id()).isInstanceOf(ProvisionResult.Success.class);
            }
        }

        var actualAfter = adapter.readActual(desired, TENANCY_ID);
        for (var entry : actualAfter.statuses().entrySet()) {
            assertThat(entry.getValue()).as("node %s", entry.getKey()).isEqualTo(NodeStatus.PRESENT);
        }

        var secondPlan = planner.plan(desired, actualAfter);
        assertThat(secondPlan.additions()).isEmpty();
        assertThat(secondPlan.removals()).isEmpty();
    }

    @Test
    void driftRemediation_modifySpec_recompile_remediate() {
        var goals = loadGoalsWithDetection();
        var desired = ((CompilationResult.SingleGraph) compiler.compile(goals, graphFactory)).graph();
        provisionAll(desired);

        var modifiedGoals = withModifiedAgentName(goals, "Modified Agent Name");
        var modifiedDesired = ((CompilationResult.SingleGraph) compiler.compile(modifiedGoals, graphFactory)).graph();

        var driftActual = adapter.readActual(modifiedDesired, TENANCY_ID);
        assertThat(driftActual.statusOf(NodeId.of("recon-agent"))).contains(NodeStatus.DRIFTED);

        var remediationPlan = planner.plan(modifiedDesired, driftActual);
        assertThat(remediationPlan.additions()).isNotEmpty();

        for (var step : remediationPlan.additions()) {
            if (step.action() == StepAction.PROVISION) {
                var result = provisioner.provision(step.node(), new ProvisionContext(TENANCY_ID, modifiedDesired));
                assertThat(result).isInstanceOf(ProvisionResult.Success.class);
            }
        }

        var afterRemediation = adapter.readActual(modifiedDesired, TENANCY_ID);
        for (var entry : afterRemediation.statuses().entrySet()) {
            assertThat(entry.getValue()).as("node %s after remediation", entry.getKey()).isEqualTo(NodeStatus.PRESENT);
        }

        var closurePlan = planner.plan(modifiedDesired, afterRemediation);
        assertThat(closurePlan.additions()).isEmpty();
        assertThat(closurePlan.removals()).isEmpty();
    }

    @Test
    void nodeRemoval_removeDetection_deprovision_positiveAbsence() {
        var goals = loadGoalsWithDetection();
        var desired = ((CompilationResult.SingleGraph) compiler.compile(goals, graphFactory)).graph();
        provisionAll(desired);

        assertThat(situationRegistrar.exists("recon-detect")).isTrue();

        var reducedGoals = withoutDetections(goals);
        var reducedDesired = ((CompilationResult.SingleGraph) compiler.compile(reducedGoals, graphFactory)).graph();
        assertThat(reducedDesired.nodes()).hasSize(5);

        var fullActual = adapter.readActual(desired, TENANCY_ID);
        var removalPlan = planner.plan(reducedDesired, fullActual);
        assertThat(removalPlan.removals()).isNotEmpty();
        assertThat(removalPlan.removals()).anySatisfy(step -> {
            assertThat(step.node().id()).isEqualTo(NodeId.of("recon-detect"));
            assertThat(step.action()).isEqualTo(StepAction.DEPROVISION);
        });

        for (var step : removalPlan.removals()) {
            if (step.action() == StepAction.DEPROVISION) {
                var originalNode = desired.nodes().get(step.node().id());
                var result = provisioner.deprovision(originalNode, new DeprovisionContext(TENANCY_ID, reducedDesired));
                assertThat(result).isInstanceOf(DeprovisionResult.Success.class);
            }
        }

        assertThat(situationRegistrar.exists("recon-detect")).isFalse();

        var afterRemoval = adapter.readActual(reducedDesired, TENANCY_ID);
        for (var entry : afterRemoval.statuses().entrySet()) {
            assertThat(entry.getValue()).as("node %s still present", entry.getKey()).isEqualTo(NodeStatus.PRESENT);
        }
    }

    private DeploymentGoals loadGoalsWithDetection() {
        var yamlGoals = goalLoader.load("test-deployment/reconciliation-topology.yaml");
        var detection = new DetectionNodeSpec(
                "recon-detect", Set.of("test.event"),
                Duration.ofMinutes(5), null,
                new ChainMode.Streak("g1", 3),
                new TriggerAction.NotifyOnly(),
                new TriggerMode.FireOnce());
        return new DeploymentGoals(
                yamlGoals.agents(), yamlGoals.channels(), yamlGoals.caseTypes(),
                yamlGoals.trust(), yamlGoals.endpoints(),
                List.of(new GoalEntry<>(detection, List.of())),
                yamlGoals.adaptations());
    }

    private void provisionAll(DesiredStateGraph desired) {
        var actual = adapter.readActual(desired, TENANCY_ID);
        var plan = planner.plan(desired, actual);
        for (var step : plan.additions()) {
            if (step.action() == StepAction.PROVISION) {
                provisioner.provision(step.node(), new ProvisionContext(TENANCY_ID, desired));
            }
        }
    }

    private DeploymentGoals withModifiedAgentName(DeploymentGoals goals, String newName) {
        var modifiedAgents = goals.agents().stream().map(entry -> {
            var s = entry.spec();
            var modified = new AgentNodeSpec(
                    s.agentId(), newName, s.slot(), s.provider(),
                    s.modelFamily(), s.modelVersion(), s.version(),
                    s.weightsFingerprint(), s.domainVocabulary(), s.slotVocabulary(),
                    s.dispositionVocabulary(), s.styleVocabulary(), s.axisVocabularies(),
                    s.capabilities(), s.disposition(), s.jurisdiction(),
                    s.dataHandlingPolicy(), s.briefing(), s.providerConfigs());
            return new GoalEntry<>(modified, entry.dependsOn());
        }).toList();
        return new DeploymentGoals(modifiedAgents, goals.channels(), goals.caseTypes(),
                goals.trust(), goals.endpoints(), goals.detections(), goals.adaptations());
    }

    private DeploymentGoals withoutDetections(DeploymentGoals goals) {
        return new DeploymentGoals(goals.agents(), goals.channels(), goals.caseTypes(),
                goals.trust(), goals.endpoints(), List.of(), goals.adaptations());
    }

    static class StubSituationRegistrar implements SituationRegistrar {
        private final Map<String, SituationRegistration> registered = new ConcurrentHashMap<>();

        @Override
        public void register(SituationRegistration registration) {
            registered.put(registration.definition().situationId(), registration);
        }

        @Override
        public void deregister(String situationId) {
            registered.remove(situationId);
        }

        @Override
        public boolean exists(String situationId) {
            return registered.containsKey(situationId);
        }
    }
}

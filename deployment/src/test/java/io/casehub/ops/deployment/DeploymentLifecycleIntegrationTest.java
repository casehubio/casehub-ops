package io.casehub.ops.deployment;

import io.casehub.desiredstate.api.*;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.eidos.api.*;
import io.casehub.ops.api.deployment.*;
import io.casehub.ops.deployment.drift.*;
import io.casehub.ops.deployment.handler.*;
import io.casehub.ops.testing.*;
import io.casehub.platform.api.endpoints.*;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageType;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentLifecycleIntegrationTest {

    private DeploymentGoalCompiler compiler;
    private DeploymentNodeProvisioner provisioner;
    private DeploymentActualStateAdapter adapter;
    private DeploymentEventSource eventSource;
    private DeploymentFaultPolicy faultPolicy;
    private DefaultDesiredStateGraphFactory graphFactory;

    private StubAgentRegistry agentRegistry;
    private StubChannelOperations channelOps;
    private StubChannelStore channelStore;
    private StubChannelBindingStore bindingStore;
    private StubEndpointRegistry endpointRegistry;
    private CaseTypeProvisionHandler caseTypeHandler;
    private DeploymentTrustRoutingPolicyProvider trustProvider;
    private SpecHashStore specHashStore;
    private DeploymentProviderConfigStore providerConfigStore;

    private static final String TENANCY_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        // Create stubs
        agentRegistry = new StubAgentRegistry();
        channelStore = new StubChannelStore();
        bindingStore = new StubChannelBindingStore();
        channelOps = new StubChannelOperations(channelStore, TENANCY_ID);
        endpointRegistry = new StubEndpointRegistry();
        providerConfigStore = new DeploymentProviderConfigStore();
        caseTypeHandler = new CaseTypeProvisionHandler();
        trustProvider = new DeploymentTrustRoutingPolicyProvider();

        // Create shared spec hash store
        specHashStore = new SpecHashStore();

        // Create drift checkers that use the stubs
        var agentChecker = new AgentDriftChecker(agentRegistry);
        var channelChecker = new ChannelDriftChecker(channelStore, bindingStore);
        var caseTypeChecker = new CaseTypeDriftChecker(caseTypeHandler);
        var trustChecker = new TrustPolicyDriftChecker(trustProvider);
        var endpointChecker = new EndpointDriftChecker(endpointRegistry);

        // Wire everything
        compiler = new DeploymentGoalCompiler();
        provisioner = new DeploymentNodeProvisioner(
                agentRegistry,
                providerConfigStore,
                new ChannelProvisionHandler(channelOps),
                caseTypeHandler,
                new TrustPolicyProvisionHandler(trustProvider),
                new EndpointProvisionHandler(endpointRegistry),
                new io.casehub.ops.deployment.handler.DetectionProvisionHandler(new io.casehub.ras.api.SituationRegistrar() {
                    @Override public void register(io.casehub.ras.api.SituationRegistration r) {}
                    @Override public void deregister(String id) {}
                    @Override public boolean exists(String id) { return false; }
                }),
                specHashStore,
                (node, action, tenancyId) -> new io.casehub.ops.api.approval.ApprovalDecision.AutoApproved(),
                new io.casehub.ops.api.approval.InMemoryPlanStore());
        adapter = new DeploymentActualStateAdapter(
                List.of(agentChecker, channelChecker, caseTypeChecker, trustChecker, endpointChecker),
                specHashStore);
        eventSource = new DeploymentEventSource();
        faultPolicy = new DeploymentFaultPolicy();
        graphFactory = new DefaultDesiredStateGraphFactory();
    }

    @Test
    void fullLifecycle_declare_compile_provision_readState() {
        // Declare 5 nodes (one of each type including endpoint)
        var agentCap = new AgentCapability("cap-a", null, null, null, null, null, List.of(), List.of(), List.of(), Map.of(), null);
        var agentDisp = AgentDisposition.builder().delegation(false).build();
        var claudonyConfig = new ProviderConfig("claudony", Map.of("tools", "read,write"));
        var agentSpec = new AgentNodeSpec("agent-1", "Worker Agent", "worker", "anthropic", "claude", "4.6",
                "1.0", "fp1", "domain", "slot", "disp", null, Map.of(), List.of(agentCap), agentDisp, "US", "policy", "Reviews code quality", List.of(claudonyConfig));

        var channelSpec = new ChannelNodeSpec("dev/work", "desc", ChannelSemantic.APPEND,
                Set.of(MessageType.COMMAND), Set.of(), null, null, null, null, null, null, null, null, null);

        var caseTypeSpec = new CaseTypeNodeSpec("io.casehub.devtown", "pr-review", "1.0", "PR Review", "Automated", "test-case-defs/pr-review.yaml", null);

        var trustSpec = new TrustPolicyNodeSpec("cap-a", 0.8, 5, 0.1, 0.5, Map.of(), false);

        var endpointSpec = new EndpointNodeSpec(
                "test/kafka-stream",
                EndpointType.SERVICE,
                EndpointProtocol.KAFKA,
                Map.of(EndpointPropertyKeys.TOPIC, "test.events"),
                null,
                Set.of(EndpointCapability.RECEIVE));

        var deploymentGoals = new DeploymentGoals(
                List.of(new GoalEntry<>(agentSpec, List.of("test/kafka-stream"))),
                List.of(new GoalEntry<>(channelSpec, List.of())),
                List.of(new GoalEntry<>(caseTypeSpec, List.of())),
                List.of(new GoalEntry<>(trustSpec, List.of())),
                List.of(new GoalEntry<>(endpointSpec, List.of())),
                List.of(),
                List.of());

        // Compile
        var desired = ((CompilationResult.SingleGraph) compiler.compile(deploymentGoals, graphFactory)).graph();
        assertThat(desired.nodes()).hasSize(5);
        assertThat(desired.dependencies()).hasSize(1);

        // Verify cross-type dependency: agent → endpoint
        var dep = desired.dependencies().iterator().next();
        assertThat(dep.from()).isEqualTo(NodeId.of("agent-1"));
        assertThat(dep.to()).isEqualTo(NodeId.of("test/kafka-stream"));

        // Provision all
        var provisionContext = new ProvisionContext(TENANCY_ID, desired);
        for (var node : desired.nodes().values()) {
            var result = provisioner.provision(node, provisionContext);
            assertThat(result)
                    .as("provisioning %s", node.id())
                    .isInstanceOf(ProvisionResult.Success.class);
        }

        // Read actual state
        var actual = adapter.readActual(desired, TENANCY_ID);
        assertThat(actual.statuses()).hasSize(5);
        for (var status : actual.statuses().values()) {
            assertThat(status).isEqualTo(NodeStatus.PRESENT);
        }

        // Verify provider configs stored
        assertThat(providerConfigStore.forAgent("agent-1")).containsKey("claudony");
        var storedConfig = providerConfigStore.forAgent("agent-1").get("claudony");
        assertThat(storedConfig.providerName()).isEqualTo("claudony");
        assertThat(storedConfig.config().get("tools")).isEqualTo("read,write");

        // Verify case type definition payload resolved
        var caseTypeNode = desired.nodes().values().stream()
                .filter(n -> n.spec() instanceof CaseTypeNodeSpec)
                .findFirst()
                .orElseThrow();
        var resolvedSpec = (CaseTypeNodeSpec) caseTypeNode.spec();
        assertThat(resolvedSpec.definitionPayload()).isNotNull();
        assertThat(resolvedSpec.definitionPayload().get("namespace")).isEqualTo("io.casehub.devtown");
        assertThat(resolvedSpec.definitionPayload().get("name")).isEqualTo("pr-review");
    }

    @Test
    void driftDetection_specHashChangeReportsDrifted() {
        // Create and provision an agent
        var agentCap = new AgentCapability("cap-b", null, null, null, null, null, List.of(), List.of(), List.of(), Map.of(), null);
        var agentDisp = AgentDisposition.builder().delegation(false).build();
        var agentSpec = new AgentNodeSpec("agent-drift", "Original", "worker", "anthropic", "claude", "4.6",
                "1.0", "fp1", "domain", "slot", "disp", null, Map.of(), List.of(agentCap), agentDisp, "US", "policy", null, List.of());

        var deploymentGoals = new DeploymentGoals(
                List.of(new GoalEntry<>(agentSpec, List.of())),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        var desired = ((CompilationResult.SingleGraph) compiler.compile(deploymentGoals, graphFactory)).graph();
        var provisionContext = new ProvisionContext(TENANCY_ID, desired);
        var node = desired.nodes().values().iterator().next();
        var result = provisioner.provision(node, provisionContext);
        assertThat(result).isInstanceOf(ProvisionResult.Success.class);

        // Compile a modified agent (different name field = different spec hash)
        var modifiedSpec = new AgentNodeSpec("agent-drift", "Modified Name", "worker", "anthropic", "claude", "4.6",
                "1.0", "fp1", "domain", "slot", "disp", null, Map.of(), List.of(agentCap), agentDisp, "US", "policy", null, List.of());
        var modifiedGoals = new DeploymentGoals(
                List.of(new GoalEntry<>(modifiedSpec, List.of())),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        var modifiedDesired = ((CompilationResult.SingleGraph) compiler.compile(modifiedGoals, graphFactory)).graph();

        // Read actual state — should detect drift
        var actual = adapter.readActual(modifiedDesired, TENANCY_ID);
        var status = actual.statuses().get(NodeId.of("agent-drift"));
        assertThat(status).isEqualTo(NodeStatus.DRIFTED);
    }

    @Test
    void driftDetection_endpointPropertyChangeReportsDrifted() {
        // Create and provision a KAFKA endpoint
        var endpointSpec = new EndpointNodeSpec(
                "test/kafka-drift",
                EndpointType.SERVICE,
                EndpointProtocol.KAFKA,
                Map.of(EndpointPropertyKeys.TOPIC, "original.topic"),
                null,
                Set.of(EndpointCapability.RECEIVE));

        var deploymentGoals = new DeploymentGoals(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new GoalEntry<>(endpointSpec, List.of())),
                List.of(),
                List.of());

        var desired = ((CompilationResult.SingleGraph) compiler.compile(deploymentGoals, graphFactory)).graph();
        var provisionContext = new ProvisionContext(TENANCY_ID, desired);
        var node = desired.nodes().values().iterator().next();
        var result = provisioner.provision(node, provisionContext);
        assertThat(result).isInstanceOf(ProvisionResult.Success.class);

        // Compile a modified endpoint (different topic = different properties)
        var modifiedSpec = new EndpointNodeSpec(
                "test/kafka-drift",
                EndpointType.SERVICE,
                EndpointProtocol.KAFKA,
                Map.of(EndpointPropertyKeys.TOPIC, "modified.topic"),
                null,
                Set.of(EndpointCapability.RECEIVE));
        var modifiedGoals = new DeploymentGoals(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new GoalEntry<>(modifiedSpec, List.of())),
                List.of(),
                List.of());
        var modifiedDesired = ((CompilationResult.SingleGraph) compiler.compile(modifiedGoals, graphFactory)).graph();

        // Read actual state — should detect drift
        var actual = adapter.readActual(modifiedDesired, TENANCY_ID);
        var status = actual.statuses().get(NodeId.of("test/kafka-drift"));
        assertThat(status).isEqualTo(NodeStatus.DRIFTED);
    }

    @Test
    void eventSource_emitDrift() {
        var subscriber = eventSource.stream()
                .subscribe().withSubscriber(AssertSubscriber.create(10));

        eventSource.emitDrift(NodeId.of("node-1"));

        var items = subscriber.getItems();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).node()).isEqualTo(NodeId.of("node-1"));
        assertThat(items.get(0).newStatus()).isEqualTo(NodeStatus.DRIFTED);
    }

    @Test
    void faultPolicy_returnsNoMutations() {
        var graph = graphFactory.empty();
        var event = new FaultEvent(NodeId.of("node-1"), FaultType.PROVISION_FAILED, "test error");

        var mutations = faultPolicy.onFault("tenant-1", event, graph, new ActualState(Map.of()));

        assertThat(mutations).isEmpty();
    }

}

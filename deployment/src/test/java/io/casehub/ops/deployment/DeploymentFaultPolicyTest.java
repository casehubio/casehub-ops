package io.casehub.ops.deployment;

import io.casehub.desiredstate.api.*;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.deployment.DeploymentReviewSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentFaultPolicyTest {

    private static final NodeType DEPLOYMENT_REVIEW = NodeType.of("deployment-review");
    private static final ActualState EMPTY_ACTUAL = new ActualState(Map.of());

    private final DefaultDesiredStateGraphFactory graphFactory = new DefaultDesiredStateGraphFactory();
    private DeploymentFaultPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new DeploymentFaultPolicy();
    }

    @Test
    void provisionFailed_belowThreshold_returnsEmpty() {
        var graph = graphWithNode("agent-1", NodeType.of("agent"));
        var event = new FaultEvent(NodeId.of("agent-1"), FaultType.PROVISION_FAILED, "registry timeout");

        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
    }

    @Test
    void provisionFailed_atThreshold_addsReviewNode() {
        var graph = graphWithNode("agent-1", NodeType.of("agent"));
        var event = new FaultEvent(NodeId.of("agent-1"), FaultType.PROVISION_FAILED, "registry timeout");

        policy.onFault("t1", event, graph, EMPTY_ACTUAL);
        policy.onFault("t1", event, graph, EMPTY_ACTUAL);
        var mutations = policy.onFault("t1", event, graph, EMPTY_ACTUAL);

        assertThat(mutations).hasSize(2);
        assertThat(mutations.getFirst()).isInstanceOf(GraphMutation.AddNode.class);

        var addNode = (GraphMutation.AddNode) mutations.getFirst();
        assertThat(addNode.node().id()).isEqualTo(NodeId.of("deployment-review-agent-1"));
        assertThat(addNode.node().type()).isEqualTo(DEPLOYMENT_REVIEW);
        assertThat(addNode.node().humanGating()).isEqualTo(HumanGating.ALL);
        assertThat(addNode.node().spec()).isInstanceOf(DeploymentReviewSpec.class);

        var spec = (DeploymentReviewSpec) addNode.node().spec();
        assertThat(spec.faultedNode()).isEqualTo(NodeId.of("agent-1"));
        assertThat(spec.reason()).isEqualTo("registry timeout");

        assertThat(mutations.get(1)).isInstanceOf(GraphMutation.AddDependency.class);
        var addDep = (GraphMutation.AddDependency) mutations.get(1);
        assertThat(addDep.dependency().from()).isEqualTo(NodeId.of("deployment-review-agent-1"));
        assertThat(addDep.dependency().to()).isEqualTo(NodeId.of("agent-1"));
    }

    @Test
    void nonProvisionFaultType_returnsEmpty() {
        var graph = graphWithNode("agent-1", NodeType.of("agent"));
        for (FaultType type : List.of(FaultType.NODE_DEGRADED, FaultType.NODE_DESTROYED,
                FaultType.DEPROVISION_FAILED, FaultType.DEPENDENCY_UNAVAILABLE,
                FaultType.HUMAN_NODE_TIMEOUT, FaultType.APPROVAL_REJECTED)) {
            var event = new FaultEvent(NodeId.of("agent-1"), type, "test");
            assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL))
                    .as("FaultType %s should return empty", type)
                    .isEmpty();
        }
    }

    @Test
    void nonDeploymentNodeType_returnsEmpty() {
        var otherNode = new DesiredNode(NodeId.of("other-1"), NodeType.of("something-else"),
                testSpec(), HumanGating.NONE);
        var graph = graphFactory.of(List.of(otherNode), List.of());
        var event = new FaultEvent(NodeId.of("other-1"), FaultType.PROVISION_FAILED, "failed");

        for (int i = 0; i < 10; i++) {
            assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
        }
    }

    @Test
    void allDeploymentNodeTypes_escalate() {
        for (NodeType deployType : List.of(
                NodeType.of("agent"), NodeType.of("channel"),
                NodeType.of("case_type"), NodeType.of("trust_policy"),
                NodeType.of("endpoint"))) {
            var freshPolicy = new DeploymentFaultPolicy();
            var node = new DesiredNode(NodeId.of("res-1"), deployType, testSpec(), HumanGating.NONE);
            var graph = graphFactory.of(List.of(node), List.of());
            var event = new FaultEvent(NodeId.of("res-1"), FaultType.PROVISION_FAILED, "fail");

            freshPolicy.onFault("t1", event, graph, EMPTY_ACTUAL);
            freshPolicy.onFault("t1", event, graph, EMPTY_ACTUAL);
            assertThat(freshPolicy.onFault("t1", event, graph, EMPTY_ACTUAL))
                    .as("Deployment node type %s should escalate at threshold", deployType)
                    .hasSize(2);
        }
    }

    @Test
    void provisionFailed_onReviewNode_returnsEmpty() {
        var reviewNode = new DesiredNode(NodeId.of("review-agent-1"), DEPLOYMENT_REVIEW,
                new DeploymentReviewSpec(NodeId.of("agent-1"), "test"), HumanGating.ALL);
        var graph = graphFactory.of(List.of(reviewNode), List.of());
        var event = new FaultEvent(NodeId.of("review-agent-1"), FaultType.PROVISION_FAILED, "failed");

        for (int i = 0; i < 10; i++) {
            assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
        }
    }

    private DesiredStateGraph graphWithNode(String nodeId, NodeType type) {
        var node = new DesiredNode(NodeId.of(nodeId), type, testSpec(), HumanGating.NONE);
        return graphFactory.of(List.of(node), List.of());
    }

    private NodeSpec testSpec() {
        return new NodeSpec() {};
    }
}

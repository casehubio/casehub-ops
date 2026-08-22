package io.casehub.ops.infra;

import io.casehub.desiredstate.api.*;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.infra.InfraReviewSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InfraFaultPolicyTest {

    private static final NodeType    INFRA_REVIEW = NodeType.of("infra-review");
    private static final ActualState EMPTY_ACTUAL = new ActualState(Map.of());

    private final DefaultDesiredStateGraphFactory graphFactory = new DefaultDesiredStateGraphFactory();
    private       InfraFaultPolicy                policy;

    @BeforeEach
    void setUp() {
        policy = new InfraFaultPolicy();
    }

    @Test
    void provisionFailed_belowThreshold_returnsEmpty() {
        var graph = graphWithNode("vm-1", NodeType.of("compute_instance"));
        var event = new FaultEvent(NodeId.of("vm-1"), FaultType.PROVISION_FAILED, "timeout");

        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
        assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
    }

    @Test
    void provisionFailed_atThreshold_addsReviewNode() {
        var graph = graphWithNode("vm-1", NodeType.of("compute_instance"));
        var event = new FaultEvent(NodeId.of("vm-1"), FaultType.PROVISION_FAILED, "timeout");

        policy.onFault("t1", event, graph, EMPTY_ACTUAL);
        policy.onFault("t1", event, graph, EMPTY_ACTUAL);
        var mutations = policy.onFault("t1", event, graph, EMPTY_ACTUAL);

        assertThat(mutations).hasSize(2);
        assertThat(mutations.getFirst()).isInstanceOf(GraphMutation.AddNode.class);

        var addNode = (GraphMutation.AddNode) mutations.getFirst();
        assertThat(addNode.node().id()).isEqualTo(NodeId.of("infra-review-vm-1"));
        assertThat(addNode.node().type()).isEqualTo(INFRA_REVIEW);
        assertThat(addNode.node().humanGating()).isEqualTo(HumanGating.ALL);
        assertThat(addNode.node().spec()).isInstanceOf(InfraReviewSpec.class);

        var spec = (InfraReviewSpec) addNode.node().spec();
        assertThat(spec.faultedNode()).isEqualTo(NodeId.of("vm-1"));
        assertThat(spec.reason()).isEqualTo("timeout");

        assertThat(mutations.get(1)).isInstanceOf(GraphMutation.AddDependency.class);
        var addDep = (GraphMutation.AddDependency) mutations.get(1);
        assertThat(addDep.dependency().from()).isEqualTo(NodeId.of("infra-review-vm-1"));
        assertThat(addDep.dependency().to()).isEqualTo(NodeId.of("vm-1"));
    }

    @Test
    void provisionFailed_reviewAlreadyExists_returnsEmpty() {
        var vmNode = new DesiredNode(NodeId.of("vm-1"),
                                     testSpec(NodeType.of("compute_instance")), HumanGating.NONE);
        var reviewNode = new DesiredNode(NodeId.of("infra-review-vm-1"),
                                         new InfraReviewSpec(NodeId.of("vm-1"), "prior"), HumanGating.ALL);
        var graph = graphFactory.of(List.of(vmNode, reviewNode), List.of());
        var event = new FaultEvent(NodeId.of("vm-1"), FaultType.PROVISION_FAILED, "still failing");

        for (int i = 0; i < 10; i++) {
            assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
        }
    }

    @Test
    void nonProvisionFaultType_returnsEmpty() {
        var graph = graphWithNode("vm-1", NodeType.of("compute_instance"));
        for (FaultType type : List.of(FaultType.NODE_DEGRADED, FaultType.NODE_DESTROYED,
                                      FaultType.DEPROVISION_FAILED, FaultType.DEPENDENCY_UNAVAILABLE,
                                      FaultType.HUMAN_NODE_TIMEOUT, FaultType.APPROVAL_REJECTED)) {
            var event = new FaultEvent(NodeId.of("vm-1"), type, "test");
            assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL))
                    .as("FaultType %s should return empty", type)
                    .isEmpty();
        }
    }

    @Test
    void nonInfraNodeType_returnsEmpty() {
        var otherNode = new DesiredNode(NodeId.of("other-1"),
                                        testSpec(NodeType.of("something-else")), HumanGating.NONE);
        var graph = graphFactory.of(List.of(otherNode), List.of());
        var event = new FaultEvent(NodeId.of("other-1"), FaultType.PROVISION_FAILED, "failed");

        for (int i = 0; i < 10; i++) {
            assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
        }
    }

    @Test
    void allInfraNodeTypes_escalate() {
        for (NodeType infraType : List.of(
                NodeType.of("k8s_namespace"), NodeType.of("k8s_deployment"),
                NodeType.of("k8s_service"), NodeType.of("k8s_ingress"),
                NodeType.of("compute_instance"), NodeType.of("database_cluster"),
                NodeType.of("terraform_workspace"), NodeType.of("ansible_playbook"))) {
            var freshPolicy = new InfraFaultPolicy();
            var node        = new DesiredNode(NodeId.of("res-1"), testSpec(infraType), HumanGating.NONE);
            var graph       = graphFactory.of(List.of(node), List.of());
            var event       = new FaultEvent(NodeId.of("res-1"), FaultType.PROVISION_FAILED, "fail");

            freshPolicy.onFault("t1", event, graph, EMPTY_ACTUAL);
            freshPolicy.onFault("t1", event, graph, EMPTY_ACTUAL);
            assertThat(freshPolicy.onFault("t1", event, graph, EMPTY_ACTUAL))
                    .as("Infra node type %s should escalate at threshold", infraType)
                    .hasSize(2);
        }
    }

    @Test
    void provisionFailed_onReviewNode_returnsEmpty() {
        var reviewNode = new DesiredNode(NodeId.of("review-vm-1"),
                                         new InfraReviewSpec(NodeId.of("vm-1"), "test"), HumanGating.ALL);
        var graph = graphFactory.of(List.of(reviewNode), List.of());
        var event = new FaultEvent(NodeId.of("review-vm-1"), FaultType.PROVISION_FAILED, "failed");

        for (int i = 0; i < 10; i++) {
            assertThat(policy.onFault("t1", event, graph, EMPTY_ACTUAL)).isEmpty();
        }
    }

    private DesiredStateGraph graphWithNode(String nodeId, NodeType type) {
        var node = new DesiredNode(NodeId.of(nodeId), testSpec(type), HumanGating.NONE);
        return graphFactory.of(List.of(node), List.of());
    }

    private NodeSpec testSpec(NodeType type) {
        return new NodeSpec() {
            public NodeType nodeType() { return type; }
        };
    }
}

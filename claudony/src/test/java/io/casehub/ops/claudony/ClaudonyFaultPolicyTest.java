package io.casehub.ops.claudony;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.FaultEvent;
import io.casehub.desiredstate.api.FaultType;
import io.casehub.desiredstate.api.GraphMutation;
import io.casehub.desiredstate.api.HumanGating;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyReviewSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudonyFaultPolicyTest {

    private ClaudonyFaultPolicy policy;
    private DefaultDesiredStateGraphFactory graphFactory;

    @BeforeEach
    void setUp() {
        policy = new ClaudonyFaultPolicy();
        graphFactory = new DefaultDesiredStateGraphFactory();
    }

    @Test
    void provision_failed_no_escalation_below_threshold() {
        var appNode = appNode("my-app");
        var graph = graphFactory.of(List.of(appNode), List.of());
        var actual = new ActualState(Map.of(NodeId.of("claudony_app:my-app"), NodeStatus.ABSENT));
        var fault = new FaultEvent(NodeId.of("claudony_app:my-app"),
            FaultType.PROVISION_FAILED, "connection refused");

        var mutations = policy.onFault("ns1", fault, graph, actual);
        assertThat(mutations).isEmpty();
    }

    @Test
    void provision_failed_escalates_at_threshold_three() {
        var appNode = appNode("my-app");
        var graph = graphFactory.of(List.of(appNode), List.of());
        var actual = new ActualState(Map.of(NodeId.of("claudony_app:my-app"), NodeStatus.ABSENT));
        var fault = new FaultEvent(NodeId.of("claudony_app:my-app"),
            FaultType.PROVISION_FAILED, "image pull failed");

        policy.onFault("ns1", fault, graph, actual);
        policy.onFault("ns1", fault, graph, actual);
        var mutations = policy.onFault("ns1", fault, graph, actual);

        assertThat(mutations).isNotEmpty();
        assertThat(mutations).anySatisfy(m -> {
            assertThat(m).isInstanceOf(GraphMutation.AddNode.class);
            var addNode = (GraphMutation.AddNode<DesiredNode>) m;
            assertThat(addNode.node().spec()).isInstanceOf(ClaudonyReviewSpec.class);
            var spec = (ClaudonyReviewSpec) addNode.node().spec();
            assertThat(spec.faultedNode()).isEqualTo(NodeId.of("claudony_app:my-app"));
            assertThat(spec.reason()).isEqualTo("image pull failed");
        });
    }

    @Test
    void ignores_review_node_faults() {
        var reviewNode = new DesiredNode(NodeId.of("claudony_review:my-app"),
            new ClaudonyReviewSpec(NodeId.of("claudony_app:my-app"), "test"),
            HumanGating.ALL);
        var graph = graphFactory.of(List.of(reviewNode), List.of());
        var actual = new ActualState(Map.of());
        var fault = new FaultEvent(NodeId.of("claudony_review:my-app"),
            FaultType.PROVISION_FAILED, "fail");

        var mutations = policy.onFault("ns1", fault, graph, actual);
        assertThat(mutations).isEmpty();
    }

    private static DesiredNode appNode(String name) {
        return new DesiredNode(NodeId.of("claudony_app:" + name),
            new ClaudonyAppNodeSpec(name, name, null, "img:latest", Map.of()),
            HumanGating.NONE);
    }
}

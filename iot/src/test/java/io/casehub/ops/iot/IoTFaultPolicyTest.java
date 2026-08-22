package io.casehub.ops.iot;

import io.casehub.desiredstate.api.*;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.iot.api.DeviceClass;
import io.casehub.ops.api.iot.DeviceConfigSpec;
import io.casehub.ops.api.iot.IoTReviewSpec;
import io.casehub.ops.api.iot.PhysicalDeviceSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IoTFaultPolicyTest {

    private static final NodeType DEVICE_CONFIG   = NodeType.of("device-config");
    private static final NodeType PHYSICAL_DEVICE = NodeType.of("physical-device");
    private static final NodeType IOT_REVIEW      = NodeType.of("iot-review");

    private final DefaultDesiredStateGraphFactory graphFactory = new DefaultDesiredStateGraphFactory();
    private final ActualState                     emptyActual  = new ActualState(Map.of());

    private IoTFaultPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new IoTFaultPolicy();
    }

    @Test
    void provisionFailed_belowThreshold_returnsEmpty() {
        var graph = graphWithConfigNode("dev-1-config");
        var event = new FaultEvent(NodeId.of("dev-1-config"), FaultType.PROVISION_FAILED, "command timeout");

        for (int i = 0; i < IoTFaultPolicy.ESCALATION_THRESHOLD - 1; i++) {
            assertThat(policy.onFault("t1", event, graph, emptyActual)).isEmpty();
        }
    }

    @Test
    void provisionFailed_atThreshold_addsReviewNode() {
        var graph = graphWithConfigNode("dev-1-config");
        var event = new FaultEvent(NodeId.of("dev-1-config"), FaultType.PROVISION_FAILED, "unknown capability");

        for (int i = 0; i < IoTFaultPolicy.ESCALATION_THRESHOLD - 1; i++) {
            policy.onFault("t1", event, graph, emptyActual);
        }

        var mutations = policy.onFault("t1", event, graph, emptyActual);
        assertThat(mutations).hasSize(2);
        assertThat(mutations.getFirst()).isInstanceOf(GraphMutation.AddNode.class);

        var addNode = (GraphMutation.AddNode) mutations.getFirst();
        assertThat(addNode.node().id()).isEqualTo(NodeId.of("iot-review-dev-1-config"));
        assertThat(addNode.node().type()).isEqualTo(IOT_REVIEW);
        assertThat(addNode.node().humanGating()).isEqualTo(HumanGating.ALL);
        assertThat(addNode.node().spec()).isInstanceOf(IoTReviewSpec.class);

        var spec = (IoTReviewSpec) addNode.node().spec();
        assertThat(spec.faultedNode()).isEqualTo(NodeId.of("dev-1-config"));
        assertThat(spec.reason()).isEqualTo("unknown capability");

        assertThat(mutations.get(1)).isInstanceOf(GraphMutation.AddDependency.class);
        var addDep = (GraphMutation.AddDependency) mutations.get(1);
        assertThat(addDep.dependency().from()).isEqualTo(NodeId.of("iot-review-dev-1-config"));
        assertThat(addDep.dependency().to()).isEqualTo(NodeId.of("dev-1-config"));
    }

    @Test
    void provisionFailed_reviewAlreadyExists_returnsEmpty() {
        var configNode = new DesiredNode(NodeId.of("dev-1-config"), new DeviceConfigSpec("dev-1", DeviceClass.SENSOR, Map.of()), HumanGating.NONE);
        var reviewNode = new DesiredNode(NodeId.of("iot-review-dev-1-config"), new IoTReviewSpec(NodeId.of("dev-1-config"), "prior"), HumanGating.ALL);
        var graph = graphFactory.of(List.of(configNode, reviewNode), List.of());
        var event = new FaultEvent(NodeId.of("dev-1-config"), FaultType.PROVISION_FAILED, "still failing");

        for (int i = 0; i < IoTFaultPolicy.ESCALATION_THRESHOLD + 5; i++) {
            assertThat(policy.onFault("t1", event, graph, emptyActual)).isEmpty();
        }
    }

    @Test
    void provisionFailed_onNonConfigNode_returnsEmpty() {
        var physNode = new DesiredNode(NodeId.of("dev-1"), new PhysicalDeviceSpec("dev-1", DeviceClass.SENSOR, "Sensor 1"), HumanGating.ALL);
        var graph = graphFactory.of(List.of(physNode), List.of());
        var event = new FaultEvent(NodeId.of("dev-1"), FaultType.PROVISION_FAILED, "failed");

        for (int i = 0; i < IoTFaultPolicy.ESCALATION_THRESHOLD + 5; i++) {
            assertThat(policy.onFault("t1", event, graph, emptyActual)).isEmpty();
        }
    }

    @Test
    void provisionFailed_onReviewNode_returnsEmpty() {
        var reviewNode = new DesiredNode(NodeId.of("review-dev-1-config"), new IoTReviewSpec(NodeId.of("dev-1-config"), "test"), HumanGating.ALL);
        var graph = graphFactory.of(List.of(reviewNode), List.of());
        var event = new FaultEvent(NodeId.of("review-dev-1-config"), FaultType.PROVISION_FAILED, "failed");

        for (int i = 0; i < IoTFaultPolicy.ESCALATION_THRESHOLD + 5; i++) {
            assertThat(policy.onFault("t1", event, graph, emptyActual)).isEmpty();
        }
    }

    @Test
    void nonProvisionFaultType_returnsEmpty() {
        var graph = graphWithConfigNode("dev-1-config");
        for (FaultType type : List.of(FaultType.NODE_DEGRADED, FaultType.NODE_DESTROYED,
                                      FaultType.DEPROVISION_FAILED, FaultType.DEPENDENCY_UNAVAILABLE,
                                      FaultType.HUMAN_NODE_TIMEOUT, FaultType.APPROVAL_REJECTED)) {
            var event = new FaultEvent(NodeId.of("dev-1-config"), type, "test");
            assertThat(policy.onFault("t1", event, graph, emptyActual))
                    .as("FaultType %s should return empty", type)
                    .isEmpty();
        }
    }

    @Test
    void provisionFailed_nodeNotInGraph_returnsEmpty() {
        var graph = graphFactory.of(List.of(), List.of());
        var event = new FaultEvent(NodeId.of("unknown"), FaultType.PROVISION_FAILED, "failed");

        for (int i = 0; i < IoTFaultPolicy.ESCALATION_THRESHOLD + 1; i++) {
            assertThat(policy.onFault("t1", event, graph, emptyActual)).isEmpty();
        }
    }

    @Test
    void independentCountsPerNode() {
        var config1 = new DesiredNode(NodeId.of("a-config"), new DeviceConfigSpec("a", DeviceClass.SENSOR, Map.of()), HumanGating.NONE);
        var config2 = new DesiredNode(NodeId.of("b-config"), new DeviceConfigSpec("b", DeviceClass.SENSOR, Map.of()), HumanGating.NONE);
        var graph = graphFactory.of(List.of(config1, config2), List.of());

        var eventA = new FaultEvent(NodeId.of("a-config"), FaultType.PROVISION_FAILED, "fail a");
        var eventB = new FaultEvent(NodeId.of("b-config"), FaultType.PROVISION_FAILED, "fail b");

        for (int i = 0; i < IoTFaultPolicy.ESCALATION_THRESHOLD - 1; i++) {
            policy.onFault("t1", eventA, graph, emptyActual);
        }
        assertThat(policy.onFault("t1", eventB, graph, emptyActual)).isEmpty();
        assertThat(policy.onFault("t1", eventA, graph, emptyActual)).hasSize(2);
    }

    private DesiredStateGraph graphWithConfigNode(String nodeId) {
        var node = new DesiredNode(NodeId.of(nodeId), new DeviceConfigSpec(nodeId.replace("-config", ""), DeviceClass.SENSOR, Map.of()), HumanGating.NONE);
        return graphFactory.of(List.of(node), List.of());
    }
}

package io.casehub.ops.topology.reconciliation;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.TransitionPlan;
import io.casehub.desiredstate.api.TransitionResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("reconciliation")
class EventDrivenReconciliationTest extends ReconciliationTestBase {

    private static final String EXEMPLAR = "topologies/event-driven/lb-cluster-telemetry.yaml";

    @Test
    void telemetry_plansAllNodesFromEmpty() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        TransitionPlan plan = planFromEmpty(graph);
        assertThat(plan.additions()).isNotEmpty();
        assertThat(plan.removals()).isEmpty();
    }

    @Test
    void telemetry_namespacePrecedesBroker() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        TransitionPlan plan = planFromEmpty(graph);
        assertOrderedBefore(plan, "telemetry-ns", "kafka-broker");
    }

    @Test
    void telemetry_brokerPrecedesConsumers() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        TransitionPlan plan = planFromEmpty(graph);
        assertOrderedBefore(plan, "kafka-broker", "telemetry-ingest");
        assertOrderedBefore(plan, "kafka-broker", "telemetry-processor");
    }

    @Test
    void telemetry_processorPrecedesTimeseries() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        TransitionPlan plan = planFromEmpty(graph);
        assertOrderedBefore(plan, "telemetry-processor", "timeseries-db");
    }

    @Test
    void telemetry_driftOnBroker() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        Map<NodeId, NodeStatus> statuses = new HashMap<>();
        graph.nodes().keySet().forEach(id -> statuses.put(id, NodeStatus.PRESENT));
        statuses.put(NodeId.of("kafka-broker"), NodeStatus.DRIFTED);
        TransitionPlan plan = planWithActual(graph, new ActualState(statuses));
        assertThat(plan.additions()).hasSize(1);
        assertThat(plan.additions().get(0).node().id()).isEqualTo(NodeId.of("kafka-broker"));
    }

    @Test
    void telemetry_executionSucceeds() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        TransitionPlan plan = planFromEmpty(graph);
        TransitionResult result = executeTransition(plan, "test");
        assertThat(result.outcomes().values())
                .allMatch(o -> o instanceof StepOutcome.Succeeded);
    }
}

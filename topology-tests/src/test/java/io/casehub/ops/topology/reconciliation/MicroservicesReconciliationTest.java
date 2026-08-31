package io.casehub.ops.topology.reconciliation;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.OrderedStep;
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
class MicroservicesReconciliationTest extends ReconciliationTestBase {

    private static final String EXEMPLAR = "topologies/microservices/ha-multi-az-trading.yaml";

    @Test
    void tradingPlatform_plansAllNodesFromEmpty() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        TransitionPlan plan = planFromEmpty(graph);
        assertThat(plan.additions()).isNotEmpty();
        assertThat(plan.removals()).isEmpty();
    }

    @Test
    void tradingPlatform_namespacePrecedesDeployments() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        TransitionPlan plan = planFromEmpty(graph);
        for (OrderedStep step : plan.additions()) {
            if (step.node().type().equals(NodeType.of("k8s_deployment"))) {
                assertOrderedBefore(plan, "trading-ns", step.node().id().value());
            }
        }
    }

    @Test
    void tradingPlatform_forEachStampsMultipleNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        long marketDataCount = graph.nodes().keySet().stream()
                .filter(id -> id.value().startsWith("market-data"))
                .count();
        assertThat(marketDataCount).isEqualTo(3);
    }

    @Test
    void tradingPlatform_settlementDependsOnRiskEngine() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        TransitionPlan plan = planFromEmpty(graph);
        assertOrderedBefore(plan, "risk-engine", "settlement");
    }

    @Test
    void tradingPlatform_driftOnSingleAzNode() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        Map<NodeId, NodeStatus> statuses = new HashMap<>();
        graph.nodes().keySet().forEach(id -> statuses.put(id, NodeStatus.PRESENT));
        NodeId driftedNode = graph.nodes().keySet().stream()
                .filter(id -> id.value().startsWith("market-data"))
                .findFirst().orElseThrow();
        statuses.put(driftedNode, NodeStatus.ABSENT);

        TransitionPlan plan = planWithActual(graph, new ActualState(statuses));
        assertThat(plan.additions()).hasSize(1);
        assertThat(plan.additions().get(0).node().id()).isEqualTo(driftedNode);
    }

    @Test
    void tradingPlatform_executionSucceeds() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(EXEMPLAR);
        TransitionPlan plan = planFromEmpty(graph);
        TransitionResult result = executeTransition(plan, "test");
        assertThat(result.outcomes().values())
                .allMatch(o -> o instanceof StepOutcome.Succeeded);
    }
}

package io.casehub.ops.topology.reconciliation;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.FaultEvent;
import io.casehub.desiredstate.api.FaultPolicy;
import io.casehub.desiredstate.api.FaultType;
import io.casehub.desiredstate.api.GraphMutation;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.Phase;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.ThresholdFaultPolicy;
import io.casehub.desiredstate.api.TransitionPlan;
import io.casehub.desiredstate.api.TransitionResult;
import io.casehub.ops.api.infra.InfraReviewSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("reconciliation")
class MultiTierReconciliationTest extends ReconciliationTestBase {

    private static final String EXEMPLAR = "topologies/multi-tier/lb-cluster-ecommerce.yaml";

    @Test
    void ecommerce_plansAllNodesFromEmpty() throws IOException {
        CompilationResult.Lifecycle lifecycle = compileLifecycle(EXEMPLAR);
        for (Phase phase : lifecycle.phases()) {
            TransitionPlan plan = planFromEmpty(phase.graph());
            assertThat(plan.additions()).isNotEmpty();
            assertThat(plan.removals()).isEmpty();
        }
    }

    @Test
    void ecommerce_lifecyclePhasesInCorrectOrder() throws IOException {
        CompilationResult.Lifecycle lifecycle = compileLifecycle(EXEMPLAR);
        List<Phase> phases = lifecycle.phases();
        assertThat(phases).hasSizeGreaterThanOrEqualTo(2);
        assertThat(phases.get(0).id()).isEqualTo("data");
        assertThat(phases.get(1).id()).isEqualTo("application");
    }

    @Test
    void ecommerce_singleNodeDev_hasDataPhase() throws IOException {
        CompilationResult.Lifecycle lifecycle = compileLifecycle("topologies/multi-tier/single-node-dev.yaml");
        Phase dataPhase = lifecycle.phases().get(0);
        assertThat(dataPhase.graph().nodes().containsKey(NodeId.of("product-db"))).isTrue();
    }

    @Test
    void ecommerce_driftOnDataTier_reProvisions() throws IOException {
        CompilationResult.Lifecycle lifecycle = compileLifecycle(EXEMPLAR);
        Phase                       dataPhase = lifecycle.phases().get(0);
        DesiredStateGraph           dataGraph = dataPhase.graph();

        // Set all nodes present, then drift product-db
        Map<NodeId, NodeStatus> statuses = new java.util.HashMap<>();
        dataGraph.nodes().keySet().forEach(id -> statuses.put(id, NodeStatus.PRESENT));
        statuses.put(NodeId.of("product-db"), NodeStatus.DRIFTED);

        TransitionPlan plan = planWithActual(dataGraph, new ActualState(statuses));
        assertThat(plan.additions()).hasSize(1);
        assertThat(plan.additions().get(0).node().id()).isEqualTo(NodeId.of("product-db"));
    }

    @Test
    void ecommerce_faultEscalation_createsReviewNodeAfterThreshold() throws IOException {
        CompilationResult.Lifecycle lifecycle = compileLifecycle(EXEMPLAR);
        Phase dataPhase = lifecycle.phases().get(0);
        DesiredStateGraph graph = dataPhase.graph();
        ActualState actual = new ActualState(Map.of());

        ThresholdFaultPolicy policy = ThresholdFaultPolicy.builder()
                .faultTypes(Set.of(FaultType.PROVISION_FAILED))
                .tier(3, FaultPolicy.addReviewNode(
                        (event, current) -> new InfraReviewSpec(event.node(), event.detail())))
                .build();
        setFaultPolicies(List.of(policy));

        NodeId faultedNode = NodeId.of("product-db");
        FaultEvent event = new FaultEvent(faultedNode, FaultType.PROVISION_FAILED, "connection refused");

        assertThat(evaluateFault("tenant", event, graph, actual)).isEmpty();
        assertThat(evaluateFault("tenant", event, graph, actual)).isEmpty();

        List<GraphMutation> mutations = evaluateFault("tenant", event, graph, actual);
        assertThat(mutations).isNotEmpty();
        assertThat(mutations).anyMatch(m -> m instanceof GraphMutation.AddNode addNode
                && addNode.node().type().equals(NodeType.of("infra-review")));
    }

    @Test
    void ecommerce_executionWithFailure_recordsOutcome() throws IOException {
        CompilationResult.Lifecycle lifecycle = compileLifecycle(EXEMPLAR);
        Phase dataPhase = lifecycle.phases().get(0);
        TransitionPlan plan = planFromEmpty(dataPhase.graph());

        provisioner.failNode("product-db", 1);
        TransitionResult result = executeTransition(plan, "test");

        assertThat(result.outcomes().get(NodeId.of("product-db")))
                .isInstanceOf(StepOutcome.Failed.class);
    }
}

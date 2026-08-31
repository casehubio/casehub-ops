package io.casehub.ops.topology.reconciliation;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.FaultEvent;
import io.casehub.desiredstate.api.FaultPolicy;
import io.casehub.desiredstate.api.GraphMutation;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.OrderedStep;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.TransitionPlan;
import io.casehub.desiredstate.api.TransitionResult;
import io.casehub.desiredstate.runtime.DefaultNodeProvisionerRouter;
import io.casehub.desiredstate.runtime.FaultPolicyEngine;
import io.casehub.desiredstate.runtime.NoOpHumanNodeHandler;
import io.casehub.desiredstate.runtime.NoOpPendingApprovalHandler;
import io.casehub.desiredstate.runtime.SimpleTransitionExecutor;
import io.casehub.desiredstate.runtime.TransitionPlanner;
import io.casehub.desiredstate.testing.MockActualStateAdapter;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ReconciliationTestBase extends TopologyTestBase {

    protected FailableNodeProvisioner provisioner;
    protected TransitionPlanner planner;
    protected MockActualStateAdapter actualAdapter;
    private FaultPolicyEngine faultEngine;

    @BeforeEach
    void setUpReconciliation() {
        provisioner = new FailableNodeProvisioner();
        provisioner.setHandledTypes(allInfraTypes());
        planner = new TransitionPlanner();
        actualAdapter = new MockActualStateAdapter();
        actualAdapter.setHandledTypes(allInfraTypes());
        faultEngine = new FaultPolicyEngine(List.of());
    }

    protected void setFaultPolicies(List<FaultPolicy> policies) {
        faultEngine = new FaultPolicyEngine(policies);
    }

    protected TransitionPlan planFromEmpty(DesiredStateGraph graph) {
        return planner.plan(graph, new ActualState(Map.of()));
    }

    protected TransitionPlan planWithActual(DesiredStateGraph graph, ActualState actual) {
        return planner.plan(graph, actual);
    }

    protected TransitionResult executeTransition(TransitionPlan plan, String tenancyId) {
        SimpleTransitionExecutor executor = new SimpleTransitionExecutor(
                new DefaultNodeProvisionerRouter(List.of(provisioner)),
                new NoOpHumanNodeHandler(),
                new NoOpPendingApprovalHandler(),
                (step, tid) -> new StepOutcome.Succeeded());
        return executor.execute(plan, tenancyId);
    }

    protected List<GraphMutation> evaluateFault(String tenancyId, FaultEvent event,
                                                 DesiredStateGraph graph, ActualState actual) {
        return faultEngine.evaluate(tenancyId, event, graph, actual);
    }

    protected static void assertOrderedBefore(TransitionPlan plan, String beforeId, String afterId) {
        List<OrderedStep> additions = plan.additions();
        int beforeIdx = -1;
        int afterIdx = -1;
        for (int i = 0; i < additions.size(); i++) {
            String id = additions.get(i).node().id().value();
            if (id.equals(beforeId)) beforeIdx = i;
            if (id.equals(afterId)) afterIdx = i;
        }
        assertThat(beforeIdx)
                .as("Expected '%s' (idx=%d) before '%s' (idx=%d) in plan additions",
                        beforeId, beforeIdx, afterId, afterIdx)
                .isGreaterThanOrEqualTo(0);
        assertThat(afterIdx).isGreaterThanOrEqualTo(0);
        assertThat(beforeIdx).isLessThan(afterIdx);
    }

    private static Set<NodeType> allInfraTypes() {
        Set<NodeType> types = new HashSet<>();
        for (String typeId : buildTypeRegistry().keySet()) {
            types.add(NodeType.of(typeId));
        }
        types.add(NodeType.of("infra-review"));
        return Set.copyOf(types);
    }
}

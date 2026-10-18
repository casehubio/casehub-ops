package io.casehub.ops.claudony;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.FaultEvent;
import io.casehub.desiredstate.api.FaultPolicy;
import io.casehub.desiredstate.api.FaultType;
import io.casehub.desiredstate.api.GraphMutation;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.ThresholdFaultPolicy;
import io.casehub.ops.api.claudony.ClaudonyNodeTypes;
import io.casehub.ops.api.claudony.ClaudonyReviewSpec;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ClaudonyFaultPolicy implements FaultPolicy {

    private static final NodeType CLAUDONY_REVIEW = NodeType.of("claudony_review");

    private final ThresholdFaultPolicy delegate = ThresholdFaultPolicy.builder()
        .faultTypes(Set.of(FaultType.PROVISION_FAILED))
        .nodeTypes(Set.of(ClaudonyNodeTypes.APP, ClaudonyNodeTypes.POOL))
        .ignoreTypes(Set.of(CLAUDONY_REVIEW))
        .tier(3, FaultPolicy.addReviewNode(
            (event, current) -> new ClaudonyReviewSpec(event.node(), event.detail())))
        .build();

    @Override
    public List<GraphMutation<DesiredNode>> onFault(String tenancyId, FaultEvent event,
                                        DesiredStateGraph current, ActualState actual) {
        return delegate.onFault(tenancyId, event, current, actual);
    }
}

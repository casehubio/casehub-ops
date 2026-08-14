package io.casehub.ops.infra;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.FaultEvent;
import io.casehub.desiredstate.api.FaultPolicy;
import io.casehub.desiredstate.api.FaultType;
import io.casehub.desiredstate.api.GraphMutation;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.ThresholdFaultPolicy;
import io.casehub.ops.api.infra.InfraReviewSpec;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class InfraFaultPolicy implements FaultPolicy {

    private static final NodeType INFRA_REVIEW = NodeType.of("infra-review");

    private final ThresholdFaultPolicy delegate = ThresholdFaultPolicy.builder()
                                                                      .faultTypes(Set.of(FaultType.PROVISION_FAILED))
                                                                      .nodeTypes(Set.of(
                                                                              NodeType.of("k8s_namespace"),
                                                                              NodeType.of("k8s_deployment"),
                                                                              NodeType.of("k8s_service"),
                                                                              NodeType.of("k8s_ingress"),
                                                                              NodeType.of("compute_instance"),
                                                                              NodeType.of("database_cluster"),
                                                                              NodeType.of("terraform_workspace"),
                                                                              NodeType.of("ansible_playbook")))
                                                                      .ignoreTypes(Set.of(INFRA_REVIEW))
                                                                      .tier(3, FaultPolicy.addReviewNode(INFRA_REVIEW,
                                                                              (event, current) -> new InfraReviewSpec(event.node(), event.detail())), INFRA_REVIEW)
                                                                      .build();

    @Override
    public List<GraphMutation> onFault(String tenancyId, FaultEvent event,
                                       DesiredStateGraph current, ActualState actualState) {
        return delegate.onFault(tenancyId, event, current, actualState);
    }
}

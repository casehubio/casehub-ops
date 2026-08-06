package io.casehub.ops.deployment.drift;

import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.DetectionNodeSpec;
import io.casehub.ops.api.deployment.NodeDriftChecker;
import io.casehub.ras.api.SituationRegistrar;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DetectionDriftChecker implements NodeDriftChecker {

    private final SituationRegistrar registrar;

    @Inject
    public DetectionDriftChecker(SituationRegistrar registrar) {
        this.registrar = registrar;
    }

    @Override
    public String nodeType() {
        return "detection";
    }

    @Override
    public NodeStatus check(NodeSpec spec, String tenancyId) {
        if (!(spec instanceof DetectionNodeSpec detection)) {
            return NodeStatus.UNKNOWN;
        }
        return registrar.exists(detection.situationId())
                ? NodeStatus.PRESENT
                : NodeStatus.ABSENT;
    }
}

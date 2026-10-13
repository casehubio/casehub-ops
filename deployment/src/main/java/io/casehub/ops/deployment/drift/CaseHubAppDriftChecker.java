package io.casehub.ops.deployment.drift;

import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.NodeDriftChecker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CaseHubAppDriftChecker implements NodeDriftChecker {

    private final ApplicationDeploymentTarget deployTarget;

    @Inject
    public CaseHubAppDriftChecker(ApplicationDeploymentTarget deployTarget) {
        this.deployTarget = deployTarget;
    }

    @Override
    public String nodeType() {
        return "casehub_application";
    }

    @Override
    public NodeStatus check(NodeSpec spec, String tenancyId) {
        if (!(spec instanceof CaseHubApplicationSpec appSpec)) {
            return NodeStatus.UNKNOWN;
        }
        var instanceId = appSpec.applicationId() + "-instance";
        return deployTarget.status(instanceId);
    }
}

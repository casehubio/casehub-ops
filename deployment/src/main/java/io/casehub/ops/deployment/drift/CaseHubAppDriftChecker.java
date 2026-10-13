package io.casehub.ops.deployment.drift;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.NodeDriftChecker;
import io.casehub.ops.deployment.InstanceIdStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CaseHubAppDriftChecker implements NodeDriftChecker {

    private final ApplicationDeploymentTarget deployTarget;
    private final InstanceIdStore             instanceIdStore;

    @Inject
    public CaseHubAppDriftChecker(ApplicationDeploymentTarget deployTarget, InstanceIdStore instanceIdStore) {
        this.deployTarget    = deployTarget;
        this.instanceIdStore = instanceIdStore;
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
        var nodeId     = NodeId.of(appSpec.applicationId());
        var instanceId = instanceIdStore.get(nodeId).orElse(appSpec.applicationId() + "-instance");
        return deployTarget.status(instanceId);
    }
}

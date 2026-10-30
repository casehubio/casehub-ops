package io.casehub.ops.deployment.drift;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeploymentApplicationNodeSpec;
import io.casehub.ops.api.deployment.NodeDriftChecker;
import io.casehub.ops.deployment.InstanceIdStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class CaseHubAppDriftChecker implements NodeDriftChecker {

    private final ApplicationDeploymentTarget           deployTarget;
    private final InstanceIdStore                       instanceIdStore;
    private final Instance<ApplicationDeploymentTarget> allTargets;
    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "casehub.ops.deployment-target-override", defaultValue = "")
    String targetOverride;


    @Inject
    public CaseHubAppDriftChecker(ApplicationDeploymentTarget deployTarget,
                                  InstanceIdStore instanceIdStore,
                                  @Any Instance<ApplicationDeploymentTarget> allTargets) {
        this.deployTarget    = deployTarget;
        this.instanceIdStore = instanceIdStore;
        this.allTargets      = allTargets;
    }

    public CaseHubAppDriftChecker(ApplicationDeploymentTarget deployTarget, InstanceIdStore instanceIdStore) {
        this.deployTarget    = deployTarget;
        this.instanceIdStore = instanceIdStore;
        this.allTargets      = null;
    }


    @Override
    public String nodeType() {
        return "casehub_application";
    }

    @Override
    public NodeStatus check(NodeSpec spec, String tenancyId) {
        if (spec instanceof DeploymentApplicationNodeSpec wrapper) {
            if (wrapper.existingUrl() != null) {
                return probeExistingUrl(wrapper.existingUrl());
            }
            ApplicationDeploymentTarget target = resolveTarget(wrapper.deploymentTargetId());
            return checkAppStatus(wrapper.spec(), target);
        }
        if (!(spec instanceof CaseHubApplicationSpec appSpec)) {
            return NodeStatus.UNKNOWN;
        }
        return checkAppStatus(appSpec, deployTarget);
    }

    private ApplicationDeploymentTarget resolveTarget(String targetId) {
        String effectiveTarget = (targetOverride != null && !targetOverride.isEmpty())
                ? targetOverride : targetId;
        if (effectiveTarget == null || effectiveTarget.isEmpty() || allTargets == null) { return deployTarget; }
        return allTargets.stream()
                .filter(t -> effectiveTarget.equals(t.targetId()))
                .findFirst()
                .orElse(deployTarget);
    }

    private NodeStatus checkAppStatus(CaseHubApplicationSpec appSpec, ApplicationDeploymentTarget target) {
        var nodeId     = NodeId.of(appSpec.applicationId());
        var instanceId = instanceIdStore.get(nodeId).orElse(appSpec.applicationId() + "-instance");
        return target.status(instanceId);
    }

    private NodeStatus probeExistingUrl(String url) {
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            return (code >= 200 && code < 400) ? NodeStatus.PRESENT : NodeStatus.DRIFTED;
        } catch (Exception e) {
            return NodeStatus.ABSENT;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}

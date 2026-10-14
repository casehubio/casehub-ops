package io.casehub.ops.deployment.handler;

import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.ops.api.deployment.ApplicationBuildPipeline;
import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.BuildResult;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.api.deployment.HealthCheckResult;
import io.casehub.ops.deployment.InstanceIdStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class CaseHubAppProvisionHandler {

    private final ApplicationBuildPipeline              buildPipeline;
    private final ApplicationDeploymentTarget           deployTarget;
    private final InstanceIdStore                       instanceIdStore;
    private final Instance<ApplicationDeploymentTarget> allTargets;

    @Inject
    public CaseHubAppProvisionHandler(
            ApplicationBuildPipeline buildPipeline,
            ApplicationDeploymentTarget deployTarget,
            InstanceIdStore instanceIdStore,
            @Any Instance<ApplicationDeploymentTarget> allTargets) {
        this.buildPipeline   = buildPipeline;
        this.deployTarget    = deployTarget;
        this.instanceIdStore = instanceIdStore;
        this.allTargets      = allTargets;
    }

    public ProvisionResult provision(CaseHubApplicationSpec spec, ProvisionContext context) {
        return provisionWith(spec, context, deployTarget);
    }

    public ProvisionResult provision(CaseHubApplicationSpec spec, ProvisionContext context,
                                     String deploymentTargetId, String existingUrl) {
        if (existingUrl != null) {
            return new ProvisionResult.Success();
        }
        ApplicationDeploymentTarget target = resolveTarget(deploymentTargetId);
        return provisionWith(spec, context, target);
    }

    public DeprovisionResult deprovision(CaseHubApplicationSpec spec, DeprovisionContext context) {
        var nodeId     = NodeId.of(spec.applicationId());
        var instanceId = instanceIdStore.get(nodeId).orElse(spec.applicationId() + "-instance");
        var result     = deployTarget.undeploy(instanceId);
        if (result instanceof DeployResult.Failed f) {
            return new DeprovisionResult.Failed("Undeploy failed: " + f.reason());
        }
        instanceIdStore.remove(nodeId);
        return new DeprovisionResult.Success();
    }

    private ApplicationDeploymentTarget resolveTarget(String targetId) {
        if (targetId == null || targetId.isEmpty()) {return deployTarget;}
        return allTargets.stream()
                         .filter(t -> targetId.equals(t.targetId()))
                         .findFirst()
                         .orElse(deployTarget);
    }

    private ProvisionResult provisionWith(CaseHubApplicationSpec spec, ProvisionContext context,
                                          ApplicationDeploymentTarget target) {
        String imageRef;
        if (spec.image() != null && !spec.image().isBlank()) {
            imageRef = spec.image();
        } else {
            var buildResult = buildPipeline.build(spec);
            if (buildResult instanceof BuildResult.Failed f) {
                return new ProvisionResult.Failed("Build failed: " + f.reason());
            }
            imageRef = ((BuildResult.Success) buildResult).imageRef();
        }

        var deployConfig = new DeployConfig(
                spec.applicationId(), spec.replicas(), spec.config(), Map.of());
        var deployResult = target.deploy(imageRef, deployConfig);

        if (deployResult instanceof DeployResult.Failed f) {
            return new ProvisionResult.Failed("Deploy failed: " + f.reason());
        }

        var instanceId = ((DeployResult.Success) deployResult).instanceId();
        var health     = target.healthCheck(instanceId);
        if (health instanceof HealthCheckResult.Unhealthy u) {
            return new ProvisionResult.Failed("Health check failed: " + u.reason());
        }

        instanceIdStore.record(NodeId.of(spec.applicationId()), instanceId);
        return new ProvisionResult.Success();
    }
}

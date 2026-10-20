package io.casehub.ops.topology.integration;

import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.NodeProvisioner;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.api.deployment.DeploymentApplicationNodeSpec;
import io.casehub.ops.claudony.ClaudonyDeploymentTarget;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

class ClaudonyNodeProvisioner implements NodeProvisioner {

    private final ClaudonyDeploymentTarget target;

    ClaudonyNodeProvisioner(ClaudonyDeploymentTarget target) {
        this.target = target;
    }

    @Override
    public Set<NodeType> handledTypes() {
        return Set.of(NodeType.of("casehub_application"));
    }

    @Override
    public Duration resyncInterval() {
        return Duration.ofMinutes(5);
    }

    @Override
    public ProvisionResult provision(DesiredNode node, ProvisionContext context) {
        try {
            CaseHubApplicationSpec appSpec = extractAppSpec(node);
            DeployResult result = target.deploy(
                    appSpec.image(),
                    new DeployConfig(appSpec.applicationId(), appSpec.replicas(),
                            appSpec.config(), Map.of()));
            if (result instanceof DeployResult.Success) {
                return new ProvisionResult.Success();
            }
            return new ProvisionResult.Failed(((DeployResult.Failed) result).reason());
        } catch (Exception e) {
            return new ProvisionResult.Failed(e.getMessage());
        }
    }

    @Override
    public DeprovisionResult deprovision(DesiredNode node, DeprovisionContext context) {
        try {
            CaseHubApplicationSpec appSpec = extractAppSpec(node);
            DeployResult result = target.undeploy(appSpec.applicationId());
            if (result instanceof DeployResult.Success) {
                return new DeprovisionResult.Success();
            }
            return new DeprovisionResult.Failed(((DeployResult.Failed) result).reason());
        } catch (Exception e) {
            return new DeprovisionResult.Failed(e.getMessage());
        }
    }

    private CaseHubApplicationSpec extractAppSpec(DesiredNode node) {
        if (node.spec() instanceof DeploymentApplicationNodeSpec wrapper) {
            return wrapper.spec();
        }
        return (CaseHubApplicationSpec) node.spec();
    }
}

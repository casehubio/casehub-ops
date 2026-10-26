package io.casehub.ops.testing.env;

import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.HumanGating;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.api.deployment.HealthCheckResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentAwareNodeProvisionerTest {

    @Test
    void handledTypes_includesCasehubApplication() {
        var provisioner = new EnvironmentAwareNodeProvisioner(new RecordingTarget());
        assertThat(provisioner.handledTypes())
            .containsExactly(NodeType.of("casehub_application"));
    }

    @Test
    void provision_delegatesToTarget() {
        var target = new RecordingTarget();
        var provisioner = new EnvironmentAwareNodeProvisioner(target);
        var spec = new CaseHubApplicationSpec(
            "my-app", null, "casehubio/my-app:latest", null,
            Map.of(), List.of(), 1);
        var node = new DesiredNode(NodeId.of("my-app"), spec, HumanGating.NONE);
        var graph = new DefaultDesiredStateGraphFactory().of(List.of(), List.of());

        var result = provisioner.provision(node, new ProvisionContext("tenant", graph));

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(target.lastDeployedAppId).isEqualTo("my-app");
        assertThat(target.lastDeployedImage).isEqualTo("casehubio/my-app:latest");
    }

    @Test
    void deprovision_delegatesToTarget() {
        var target = new RecordingTarget();
        var provisioner = new EnvironmentAwareNodeProvisioner(target);
        var spec = new CaseHubApplicationSpec(
            "my-app", null, "casehubio/my-app:latest", null,
            Map.of(), List.of(), 1);
        var node = new DesiredNode(NodeId.of("my-app"), spec, HumanGating.NONE);
        var graph = new DefaultDesiredStateGraphFactory().of(List.of(), List.of());

        var result = provisioner.deprovision(node, new DeprovisionContext("tenant", graph));

        assertThat(result).isInstanceOf(DeprovisionResult.Success.class);
        assertThat(target.lastUndeployedId).isEqualTo("my-app");
    }

    @Test
    void provision_returnsFailedOnTargetFailure() {
        var target = new FailingTarget();
        var provisioner = new EnvironmentAwareNodeProvisioner(target);
        var spec = new CaseHubApplicationSpec(
            "my-app", null, "casehubio/my-app:latest", null,
            Map.of(), List.of(), 1);
        var node = new DesiredNode(NodeId.of("my-app"), spec, HumanGating.NONE);
        var graph = new DefaultDesiredStateGraphFactory().of(List.of(), List.of());

        var result = provisioner.provision(node, new ProvisionContext("tenant", graph));

        assertThat(result).isInstanceOf(ProvisionResult.Failed.class);
    }

    private static class RecordingTarget implements ApplicationDeploymentTarget {
        String lastDeployedAppId;
        String lastDeployedImage;
        String lastUndeployedId;

        @Override public String targetId() { return "recording"; }
        @Override public DeployResult deploy(String imageRef, DeployConfig config) {
            lastDeployedAppId = config.applicationId();
            lastDeployedImage = imageRef;
            return new DeployResult.Success(config.applicationId());
        }
        @Override public DeployResult undeploy(String instanceId) {
            lastUndeployedId = instanceId;
            return new DeployResult.Success(instanceId);
        }
        @Override public NodeStatus status(String instanceId) { return NodeStatus.PRESENT; }
        @Override public HealthCheckResult healthCheck(String instanceId) {
            return new HealthCheckResult.Healthy(instanceId);
        }
    }

    private static class FailingTarget implements ApplicationDeploymentTarget {
        @Override public String targetId() { return "failing"; }
        @Override public DeployResult deploy(String imageRef, DeployConfig config) {
            return new DeployResult.Failed("simulated failure");
        }
        @Override public DeployResult undeploy(String instanceId) {
            return new DeployResult.Failed("simulated failure");
        }
        @Override public NodeStatus status(String instanceId) { return NodeStatus.ABSENT; }
        @Override public HealthCheckResult healthCheck(String instanceId) {
            return new HealthCheckResult.Unhealthy(instanceId, "simulated");
        }
    }
}

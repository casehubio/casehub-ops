package io.casehub.ops.deployment.handler;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.deployment.*;
import io.casehub.ops.deployment.InstanceIdStore;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class CaseHubAppProvisionHandlerMultiTargetTest {

    private static final CaseHubApplicationSpec SPEC = new CaseHubApplicationSpec(
            "qhorus", null, "casehubio/qhorus:0.5", null, Map.of(), List.of(), 1);

    @Test
    void resolvesTargetByIdWhenDeploymentTargetIdProvided() {
        var podmanTarget = new TrackingTarget("podman");
        var defaultTarget = new TrackingTarget("");

        var handler = new CaseHubAppProvisionHandler(
                new PrebuiltImagePipeline(), defaultTarget, new InstanceIdStore(),
                stubInstance(podmanTarget, defaultTarget));

        var ctx = new ProvisionContext("default", new DefaultDesiredStateGraphFactory().empty());
        var result = handler.provision(SPEC, ctx, "podman", null);

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(podmanTarget.deployCount).isEqualTo(1);
        assertThat(defaultTarget.deployCount).isZero();
    }

    @Test
    void skipsProvisioningWhenExistingUrlSet() {
        var defaultTarget = new TrackingTarget("");

        var handler = new CaseHubAppProvisionHandler(
                new PrebuiltImagePipeline(), defaultTarget, new InstanceIdStore(),
                stubInstance(defaultTarget));

        var ctx = new ProvisionContext("default", new DefaultDesiredStateGraphFactory().empty());
        var result = handler.provision(SPEC, ctx, null, "https://qhorus.internal:8443");

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(defaultTarget.deployCount).isZero();
    }

    @Test
    void fallsBackToDefaultTargetWhenTargetIdNotFound() {
        var defaultTarget = new TrackingTarget("");

        var handler = new CaseHubAppProvisionHandler(
                new PrebuiltImagePipeline(), defaultTarget, new InstanceIdStore(),
                stubInstance(defaultTarget));

        var ctx = new ProvisionContext("default", new DefaultDesiredStateGraphFactory().empty());
        var result = handler.provision(SPEC, ctx, "nonexistent", null);

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(defaultTarget.deployCount).isEqualTo(1);
    }

    private static class TrackingTarget implements ApplicationDeploymentTarget {
        private final String id;
        int deployCount = 0;

        TrackingTarget(String id) { this.id = id; }

        @Override public String targetId() { return id; }

        @Override
        public DeployResult deploy(String imageRef, DeployConfig config) {
            deployCount++;
            return new DeployResult.Success(config.applicationId() + "-instance");
        }

        @Override
        public DeployResult undeploy(String instanceId) {
            return new DeployResult.Success(instanceId);
        }

        @Override
        public NodeStatus status(String instanceId) { return NodeStatus.PRESENT; }

        @Override
        public HealthCheckResult healthCheck(String instanceId) {
            return new HealthCheckResult.Healthy(instanceId);
        }
    }

    @SuppressWarnings("unchecked")
    private static Instance<ApplicationDeploymentTarget> stubInstance(ApplicationDeploymentTarget... targets) {
        return new Instance<>() {
            @Override public Instance<ApplicationDeploymentTarget> select(Annotation... q) { return this; }
            @Override public <U extends ApplicationDeploymentTarget> Instance<U> select(Class<U> s, Annotation... q) { return (Instance<U>) this; }
            @Override public <U extends ApplicationDeploymentTarget> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> s, Annotation... q) { return (Instance<U>) this; }
            @Override public boolean isUnsatisfied() { return false; }
            @Override public boolean isAmbiguous() { return targets.length > 1; }
            @Override public boolean isResolvable() { return targets.length == 1; }
            @Override public ApplicationDeploymentTarget get() { return targets[0]; }
            @Override public Iterator<ApplicationDeploymentTarget> iterator() { return List.of(targets).iterator(); }
            @Override public Stream<ApplicationDeploymentTarget> stream() { return Stream.of(targets); }
            @Override public void destroy(ApplicationDeploymentTarget instance) {}
            @Override public Handle<ApplicationDeploymentTarget> getHandle() { return null; }
            @Override public Iterable<? extends Handle<ApplicationDeploymentTarget>> handles() { return List.of(); }
        };
    }
}

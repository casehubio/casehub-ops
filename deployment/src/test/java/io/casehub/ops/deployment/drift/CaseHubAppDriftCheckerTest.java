package io.casehub.ops.deployment.drift;

import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeploymentApplicationNodeSpec;
import io.casehub.ops.api.deployment.HealthCheckResult;
import io.casehub.ops.api.deployment.DeployResult;
import io.casehub.ops.api.deployment.RuntimeConfig;
import io.casehub.ops.testing.StubDeploymentTarget;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CaseHubAppDriftCheckerTest {

    private StubDeploymentTarget deployTarget;
    private CaseHubAppDriftChecker checker;

    @BeforeEach
    void setUp() {
        deployTarget = new StubDeploymentTarget();
        checker = new CaseHubAppDriftChecker(deployTarget,
                new io.casehub.ops.deployment.InstanceIdStore(),
                stubInstance(deployTarget));
    }

    @Test
    void nodeTypeIsCasehubApplication() {
        assertThat(checker.nodeType()).isEqualTo("casehub_application");
    }

    @Test
    void absentWhenNotDeployed() {
        var spec = new CaseHubApplicationSpec(
            "app-1", null, "img:1.0",
            new RuntimeConfig("0.2", List.of()),
            Map.of(), List.of(), 1);
        assertThat(checker.check(spec, "tenant-1")).isEqualTo(NodeStatus.ABSENT);
    }

    @Test
    void presentWhenDeployed() {
        var spec = new CaseHubApplicationSpec(
            "app-1", null, "img:1.0",
            new RuntimeConfig("0.2", List.of()),
            Map.of(), List.of(), 1);
        var config = new DeployConfig("app-1", 1, Map.of(), Map.of());
        deployTarget.deploy("img:1.0", config);
        assertThat(checker.check(spec, "tenant-1")).isEqualTo(NodeStatus.PRESENT);
    }

    @Test
    void unknownForNonApplicationSpec() {
        NodeSpec nonAppSpec = new NodeSpec() {
            @Override
            public io.casehub.desiredstate.api.NodeType nodeType() {
                return io.casehub.desiredstate.api.NodeType.of("other");
            }
        };
        assertThat(checker.check(nonAppSpec, "tenant-1")).isEqualTo(NodeStatus.UNKNOWN);
    }

    @Test
    void resolvesTargetByDeploymentTargetId() {
        var claudonyTarget = new StatusReturningTarget("claudony", NodeStatus.PRESENT);
        var defaultTarget = new StatusReturningTarget("", NodeStatus.ABSENT);
        var multiChecker = new CaseHubAppDriftChecker(defaultTarget,
                new io.casehub.ops.deployment.InstanceIdStore(),
                stubInstance(defaultTarget, claudonyTarget));

        var appSpec = new CaseHubApplicationSpec(
                "app-1", null, "img:1.0",
                new RuntimeConfig("0.2", List.of()),
                Map.of(), List.of(), 1);
        var wrappedSpec = new DeploymentApplicationNodeSpec(appSpec, "claudony", null);

        assertThat(multiChecker.check(wrappedSpec, "tenant-1")).isEqualTo(NodeStatus.PRESENT);
    }

    @Test
    void fallsBackToDefaultTargetWhenTargetIdNotFound() {
        var defaultTarget = new StatusReturningTarget("", NodeStatus.PRESENT);
        var multiChecker = new CaseHubAppDriftChecker(defaultTarget,
                new io.casehub.ops.deployment.InstanceIdStore(),
                stubInstance(defaultTarget));

        var appSpec = new CaseHubApplicationSpec(
                "app-1", null, "img:1.0",
                new RuntimeConfig("0.2", List.of()),
                Map.of(), List.of(), 1);
        var wrappedSpec = new DeploymentApplicationNodeSpec(appSpec, "nonexistent", null);

        assertThat(multiChecker.check(wrappedSpec, "tenant-1")).isEqualTo(NodeStatus.PRESENT);
    }

    private static class StatusReturningTarget implements ApplicationDeploymentTarget {
        private final String id;
        private final NodeStatus returnStatus;

        StatusReturningTarget(String id, NodeStatus returnStatus) {
            this.id = id;
            this.returnStatus = returnStatus;
        }

        @Override public String targetId() { return id; }
        @Override public DeployResult deploy(String imageRef, DeployConfig config) { return new DeployResult.Success(config.applicationId()); }
        @Override public DeployResult undeploy(String instanceId) { return new DeployResult.Success(instanceId); }
        @Override public NodeStatus status(String instanceId) { return returnStatus; }
        @Override public HealthCheckResult healthCheck(String instanceId) { return new HealthCheckResult.Healthy(instanceId); }
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
            @Override public void destroy(ApplicationDeploymentTarget i) {}
            @Override public Handle<ApplicationDeploymentTarget> getHandle() { return null; }
            @Override public Iterable<Handle<ApplicationDeploymentTarget>> handles() { return List.of(); }
        };
    }
}

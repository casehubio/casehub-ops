package io.casehub.ops.deployment.handler;

import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.PrebuiltImagePipeline;
import io.casehub.ops.api.deployment.RuntimeConfig;
import io.casehub.ops.testing.StubDeploymentTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CaseHubAppProvisionHandlerTest {

    private PrebuiltImagePipeline buildPipeline;
    private StubDeploymentTarget deployTarget;
    private CaseHubAppProvisionHandler handler;

    @BeforeEach
    void setUp() {
        buildPipeline = new PrebuiltImagePipeline();
        deployTarget = new StubDeploymentTarget();
        handler = new CaseHubAppProvisionHandler(buildPipeline, deployTarget, new io.casehub.ops.deployment.InstanceIdStore(), null);
    }

    @Test
    void provisionWithPrebuiltImage() {
        var spec = new CaseHubApplicationSpec(
            "incident-app", null, "ghcr.io/acme/incident:1.0",
            new RuntimeConfig("0.2", List.of("a2a")),
            Map.of(), List.of(), 1);
        var graph = new DefaultDesiredStateGraphFactory().empty();
        var result = handler.provision(spec, new ProvisionContext("tenant-1", graph));

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(deployTarget.deployedInstances()).containsKey("incident-app-instance");
        assertThat(deployTarget.deployedInstances().get("incident-app-instance"))
            .isEqualTo("ghcr.io/acme/incident:1.0");
    }

    @Test
    void provisionFailsWhenBuildFails() {
        var spec = new CaseHubApplicationSpec(
            "source-app", "./apps/test", null,
            new RuntimeConfig("0.2", List.of()),
            Map.of(), List.of(), 1);
        var graph = new DefaultDesiredStateGraphFactory().empty();
        var result = handler.provision(spec, new ProvisionContext("tenant-1", graph));

        assertThat(result).isInstanceOf(ProvisionResult.Failed.class);
        assertThat(((ProvisionResult.Failed) result).reason()).contains("no image");
    }

    @Test
    void deprovisionRemovesInstance() {
        var spec = new CaseHubApplicationSpec(
            "incident-app", null, "ghcr.io/acme/incident:1.0",
            new RuntimeConfig("0.2", List.of()),
            Map.of(), List.of(), 1);
        var graph = new DefaultDesiredStateGraphFactory().empty();

        handler.provision(spec, new ProvisionContext("tenant-1", graph));
        assertThat(deployTarget.deployedInstances()).isNotEmpty();

        var result = handler.deprovision(spec, new DeprovisionContext("tenant-1", graph));
        assertThat(result).isInstanceOf(DeprovisionResult.Success.class);
        assertThat(deployTarget.deployedInstances()).isEmpty();
    }

    @Test
    void resolveTargetUsesOverrideWhenSet() throws Exception {
        var podmanTarget = new StubDeploymentTarget() {
            @Override
            public String targetId() {return "podman";}
        };
        var claudonyTarget = new StubDeploymentTarget() {
            @Override
            public String targetId() {return "claudony";}
        };

        var overrideHandler = new CaseHubAppProvisionHandler(
                buildPipeline, deployTarget,
                new io.casehub.ops.deployment.InstanceIdStore(),
                stubInstance(podmanTarget, claudonyTarget));

        var field = CaseHubAppProvisionHandler.class.getDeclaredField("targetOverride");
        field.setAccessible(true);
        field.set(overrideHandler, "podman");

        var spec = new CaseHubApplicationSpec(
                "app-1", null, "img:1.0",
                new RuntimeConfig("0.2", List.of()),
                Map.of(), List.of(), 1);

        var graph  = new DefaultDesiredStateGraphFactory().empty();
        var result = overrideHandler.provision(spec, new ProvisionContext("t1", graph), "claudony", null);

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(podmanTarget.deployedInstances()).containsKey("app-1-instance");
        assertThat(claudonyTarget.deployedInstances()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static jakarta.enterprise.inject.Instance<ApplicationDeploymentTarget> stubInstance(
            ApplicationDeploymentTarget... targets) {
        return new jakarta.enterprise.inject.Instance<>() {
            @Override
            public ApplicationDeploymentTarget get() {return targets[0];}

            @Override
            public jakarta.enterprise.inject.Instance<ApplicationDeploymentTarget> select(
                    java.lang.annotation.Annotation... q) {return this;}

            @Override
            public <U extends ApplicationDeploymentTarget> jakarta.enterprise.inject.Instance<U> select(
                    Class<U> s, java.lang.annotation.Annotation... q) {return (jakarta.enterprise.inject.Instance<U>) this;}

            @Override
            public <U extends ApplicationDeploymentTarget> jakarta.enterprise.inject.Instance<U> select(
                    jakarta.enterprise.util.TypeLiteral<U> s, java.lang.annotation.Annotation... q) {
                return (jakarta.enterprise.inject.Instance<U>) this;
            }

            @Override
            public boolean isUnsatisfied() {return false;}

            @Override
            public boolean isAmbiguous()   {return targets.length > 1;}

            @Override
            public boolean isResolvable()  {return targets.length == 1;}

            @Override
            public java.util.Iterator<ApplicationDeploymentTarget> iterator() {
                return List.of(targets).iterator();
            }

            @Override
            public java.util.stream.Stream<ApplicationDeploymentTarget> stream() {
                return java.util.stream.Stream.of(targets);
            }

            @Override
            public void destroy(ApplicationDeploymentTarget i)             {}

            @Override
            public Handle<ApplicationDeploymentTarget> getHandle()         {return null;}

            @Override
            public Iterable<Handle<ApplicationDeploymentTarget>> handles() {return List.of();}
        };
    }
}

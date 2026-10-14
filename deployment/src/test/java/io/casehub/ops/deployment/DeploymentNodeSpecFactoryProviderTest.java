package io.casehub.ops.deployment;

import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeploymentApplicationNodeSpec;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeploymentNodeSpecFactoryProviderTest {

    private final DeploymentNodeSpecFactoryProvider provider = new DeploymentNodeSpecFactoryProvider();

    @Test
    void registersCaseHubApplicationType() {
        var factories = provider.provide();
        assertTrue(factories.containsKey("casehub_application"));
    }

    @Test
    void createsBareCaseHubApplicationSpec() {
        var factory = provider.provide().get("casehub_application");
        Map<String, Object> specMap = new LinkedHashMap<>(Map.of(
                "applicationId", "qhorus",
                "image", "casehubio/qhorus:0.5",
                "replicas", 1));
        NodeSpec result = factory.create(specMap);
        assertInstanceOf(CaseHubApplicationSpec.class, result);
        assertEquals("qhorus", ((CaseHubApplicationSpec) result).applicationId());
    }

    @Test
    void wrapsWhenDeploymentTargetPresent() {
        var factory = provider.provide().get("casehub_application");
        Map<String, Object> specMap = new LinkedHashMap<>(Map.of(
                "applicationId", "qhorus",
                "image", "casehubio/qhorus:0.5",
                "replicas", 1,
                "deploymentTarget", "podman"));
        NodeSpec result = factory.create(specMap);
        assertInstanceOf(DeploymentApplicationNodeSpec.class, result);
        var wrapper = (DeploymentApplicationNodeSpec) result;
        assertEquals("podman", wrapper.deploymentTargetId());
        assertEquals("qhorus", wrapper.spec().applicationId());
    }

    @Test
    void wrapsWhenExistingPresent() {
        var factory = provider.provide().get("casehub_application");
        Map<String, Object> specMap = new LinkedHashMap<>(Map.of(
                "applicationId", "qhorus",
                "image", "casehubio/qhorus:0.5",
                "replicas", 1,
                "existing", "https://qhorus.internal:8443"));
        NodeSpec result = factory.create(specMap);
        assertInstanceOf(DeploymentApplicationNodeSpec.class, result);
        var wrapper = (DeploymentApplicationNodeSpec) result;
        assertEquals("https://qhorus.internal:8443", wrapper.existingUrl());
    }
}

package io.casehub.ops.api.deployment;

import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeploymentApplicationNodeSpecTest {

    private static final CaseHubApplicationSpec APP_SPEC = new CaseHubApplicationSpec(
            "qhorus", null, "casehubio/qhorus:0.5", null, Map.of(), List.of(), 1);

    @Test
    void wrapsSpecWithTargetId() {
        var wrapper = new DeploymentApplicationNodeSpec(APP_SPEC, "podman", null);
        assertInstanceOf(NodeSpec.class, wrapper);
        assertEquals(APP_SPEC, wrapper.spec());
        assertEquals("podman", wrapper.deploymentTargetId());
        assertNull(wrapper.existingUrl());
        assertEquals("qhorus", wrapper.nodeId());
        assertEquals(NodeType.of("casehub_application"), wrapper.nodeType());
    }

    @Test
    void wrapsSpecWithExistingUrl() {
        var wrapper = new DeploymentApplicationNodeSpec(APP_SPEC, null, "https://qhorus.internal:8443");
        assertNull(wrapper.deploymentTargetId());
        assertEquals("https://qhorus.internal:8443", wrapper.existingUrl());
    }

    @Test
    void delegatesNodeIdAndNodeType() {
        var wrapper = new DeploymentApplicationNodeSpec(APP_SPEC, "podman", null);
        assertEquals(APP_SPEC.nodeId(), wrapper.nodeId());
        assertEquals(APP_SPEC.nodeType(), wrapper.nodeType());
    }
}

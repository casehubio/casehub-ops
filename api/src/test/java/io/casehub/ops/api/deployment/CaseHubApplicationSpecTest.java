package io.casehub.ops.api.deployment;

import io.casehub.desiredstate.api.NodeType;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class CaseHubApplicationSpecTest {

    @Test
    void validSpecWithImage() {
        var spec = new CaseHubApplicationSpec(
            "incident-app", null, "ghcr.io/acme/incident:1.0",
            new RuntimeConfig("0.2", List.of("a2a")),
            Map.of("llm", "anthropic"), List.of(), 2);
        assertThat(spec.applicationId()).isEqualTo("incident-app");
        assertThat(spec.nodeId()).isEqualTo("incident-app");
        assertThat(spec.nodeType()).isEqualTo(NodeType.of("casehub_application"));
        assertThat(spec.image()).isEqualTo("ghcr.io/acme/incident:1.0");
        assertThat(spec.source()).isNull();
        assertThat(spec.replicas()).isEqualTo(2);
    }

    @Test
    void validSpecWithSource() {
        var spec = new CaseHubApplicationSpec(
            "loan-app", "./apps/loan-processing", null,
            new RuntimeConfig("0.2", List.of("mcp")),
            Map.of(), List.of(), 1);
        assertThat(spec.source()).isEqualTo("./apps/loan-processing");
        assertThat(spec.image()).isNull();
    }

    @Test
    void nullApplicationIdThrows() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CaseHubApplicationSpec(
                null, "./apps/test", null,
                new RuntimeConfig("0.2", List.of()),
                Map.of(), List.of(), 1))
            .withMessageContaining("applicationId");
    }

    @Test
    void blankApplicationIdThrows() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CaseHubApplicationSpec(
                "  ", "./apps/test", null,
                new RuntimeConfig("0.2", List.of()),
                Map.of(), List.of(), 1))
            .withMessageContaining("applicationId");
    }

    @Test
    void neitherSourceNorImageThrows() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CaseHubApplicationSpec(
                "test-app", null, null,
                new RuntimeConfig("0.2", List.of()),
                Map.of(), List.of(), 1))
            .withMessageContaining("source or image");
    }

    @Test
    void replicasBelowOneDefaultsToOne() {
        var spec = new CaseHubApplicationSpec(
            "test-app", null, "img:1.0",
            new RuntimeConfig("0.2", List.of()),
            Map.of(), List.of(), 0);
        assertThat(spec.replicas()).isEqualTo(1);
    }

    @Test
    void configAndDependenciesDefensivelyCopied() {
        var config = new java.util.HashMap<String, Object>();
        config.put("key", "val");
        var deps = new java.util.ArrayList<>(List.of("com.example:ext:1.0"));
        var spec = new CaseHubApplicationSpec(
            "test-app", null, "img:1.0",
            new RuntimeConfig("0.2", List.of()),
            config, deps, 1);
        assertThatThrownBy(() -> spec.config().put("k2", "v2"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spec.dependencies().add("another"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}

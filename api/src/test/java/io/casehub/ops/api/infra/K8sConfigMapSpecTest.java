package io.casehub.ops.api.infra;

import io.casehub.ops.api.infra.types.Labels;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class K8sConfigMapSpecTest {
    @Test
    void createsValidConfigMapSpec() {
        var spec = new K8sConfigMapSpec("default", "app-config",
                Map.of("application.properties", "key=value"),
                Labels.of(Map.of("app", "my-app")));
        assertThat(spec.namespace()).isEqualTo("default");
        assertThat(spec.name()).isEqualTo("app-config");
        assertThat(spec.data()).containsEntry("application.properties", "key=value");
        assertThat(spec.resourceType()).isEqualTo("k8s_configmap");
    }

    @Test
    void isAnInfraNodeSpec() {
        var spec = new K8sConfigMapSpec("default", "app-config",
                Map.of(), Labels.of(Map.of()));
        assertThat(spec).isInstanceOf(InfraNodeSpec.class);
    }

    @Test
    void rejectsNullNamespace() {
        assertThatNullPointerException()
                .isThrownBy(() -> new K8sConfigMapSpec(null, "app-config",
                        Map.of(), Labels.of(Map.of())))
                .withMessageContaining("namespace");
    }

    @Test
    void rejectsNullName() {
        assertThatNullPointerException()
                .isThrownBy(() -> new K8sConfigMapSpec("default", null,
                        Map.of(), Labels.of(Map.of())))
                .withMessageContaining("name");
    }

    @Test
    void rejectsNullData() {
        var spec = new K8sConfigMapSpec("default", "app-config", null, Labels.of(Map.of()));
        assertThat(spec.data()).isEmpty();}

    @Test
    void rejectsNullLabels() {
        var spec = new K8sConfigMapSpec("default", "app-config", Map.of(), null);
        assertThat(spec.labels()).isEqualTo(Labels.empty());}

    @Test
    void defensivelyCopiesDataMap() {
        var mutableData = new java.util.HashMap<String, String>();
        mutableData.put("key1", "value1");
        var spec = new K8sConfigMapSpec("default", "app-config",
                mutableData, Labels.of(Map.of()));

        mutableData.put("key2", "value2");

        assertThat(spec.data()).containsOnlyKeys("key1");
        assertThat(spec.data()).doesNotContainKey("key2");
    }
}

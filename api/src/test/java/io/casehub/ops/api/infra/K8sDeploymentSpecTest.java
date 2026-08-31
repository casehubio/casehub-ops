package io.casehub.ops.api.infra;

import io.casehub.ops.api.infra.types.HealthCheckSpec;
import io.casehub.ops.api.infra.types.Labels;
import io.casehub.ops.api.infra.types.PortMapping;
import io.casehub.ops.api.infra.types.ResourceRequirements;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class K8sDeploymentSpecTest {

    @Test
    void backwardCompatibility_sixArgConstructor() {
        var resources = new ResourceRequirements("100m", "500m", "128Mi", "512Mi");
        var labels = Labels.of(Map.of("app", "myapp"));
        var spec = new K8sDeploymentSpec("default", "my-deploy", "nginx:latest", 3, resources, labels);

        assertThat(spec.resourceType()).isEqualTo("k8s_deployment");
        assertThat(spec.namespace()).isEqualTo("default");
        assertThat(spec.name()).isEqualTo("my-deploy");
        assertThat(spec.image()).isEqualTo("nginx:latest");
        assertThat(spec.replicas()).isEqualTo(3);
        assertThat(spec.resources()).isEqualTo(resources);
        assertThat(spec.labels().get("app")).hasValue("myapp");
        assertThat(spec.ports()).isEmpty();
        assertThat(spec.env()).isEmpty();
        assertThat(spec.healthCheck()).isEmpty();
    }

    @Test
    void createsSpecWithPortsEnvAndHealthCheck() {
        var ports = List.of(new PortMapping(8080, 80, "TCP"));
        var env = Map.of("JAVA_OPTS", "-Xmx512m");
        var hc = new HealthCheckSpec("/q/health", 8080, 5, 10);
        var spec = new K8sDeploymentSpec(
                "default", "my-app", "quay.io/app:1.0", 2,
                new ResourceRequirements("500m", "1Gi", "250m", "512Mi"),
                Labels.of(Map.of("app", "my-app")),
                ports, env, java.util.Optional.of(hc));
        assertThat(spec.ports()).containsExactly(new PortMapping(8080, 80, "TCP"));
        assertThat(spec.env()).containsEntry("JAVA_OPTS", "-Xmx512m");
        assertThat(spec.healthCheck()).isPresent();
    }

    @Test
    void rejectsNullPorts() {
        var spec = new K8sDeploymentSpec(
                "default", "my-app", "nginx:latest", 1,
                new ResourceRequirements("100m", "500m", "128Mi", "512Mi"),
                Labels.of(Map.of()),
                null, Map.of(), java.util.Optional.empty());
        assertThat(spec.ports()).isEmpty();}

    @Test
    void rejectsNullEnv() {
        var spec = new K8sDeploymentSpec(
                "default", "my-app", "nginx:latest", 1,
                new ResourceRequirements("100m", "500m", "128Mi", "512Mi"),
                Labels.of(Map.of()),
                List.of(), null, java.util.Optional.empty());
        assertThat(spec.env()).isEmpty();}

    @Test
    void rejectsNullHealthCheck() {
        var spec = new K8sDeploymentSpec(
                "default", "my-app", "nginx:latest", 1,
                new ResourceRequirements("100m", "500m", "128Mi", "512Mi"),
                Labels.of(Map.of()),
                List.of(), Map.of(), null);
        assertThat(spec.healthCheck()).isEmpty();}

    @Test
    void defensivelyCopiesPortsList() {
        var mutablePorts = new java.util.ArrayList<PortMapping>();
        mutablePorts.add(new PortMapping(8080, 80, "TCP"));
        var spec = new K8sDeploymentSpec(
                "default", "my-app", "nginx:latest", 1,
                new ResourceRequirements("100m", "500m", "128Mi", "512Mi"),
                Labels.of(Map.of()),
                mutablePorts, Map.of(), java.util.Optional.empty());

        mutablePorts.add(new PortMapping(9090, 90, "TCP"));

        assertThat(spec.ports()).hasSize(1);
        assertThat(spec.ports()).containsExactly(new PortMapping(8080, 80, "TCP"));
    }

    @Test
    void defensivelyCopiesEnvMap() {
        var mutableEnv = new java.util.HashMap<String, String>();
        mutableEnv.put("VAR1", "value1");
        var spec = new K8sDeploymentSpec(
                "default", "my-app", "nginx:latest", 1,
                new ResourceRequirements("100m", "500m", "128Mi", "512Mi"),
                Labels.of(Map.of()),
                List.of(), mutableEnv, java.util.Optional.empty());

        mutableEnv.put("VAR2", "value2");

        assertThat(spec.env()).containsOnlyKeys("VAR1");
        assertThat(spec.env()).doesNotContainKey("VAR2");
    }
}

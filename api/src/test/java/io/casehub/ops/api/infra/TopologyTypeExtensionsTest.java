package io.casehub.ops.api.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.ops.api.infra.types.Labels;
import io.casehub.ops.api.infra.types.ResourceRequirements;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyTypeExtensionsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // --- LoadBalancerSpec ---

    @Test
    void loadBalancerSpec_resourceType() {
        var spec = new LoadBalancerSpec("web-lb", "production",
                LoadBalancerType.APPLICATION, "/health", 80, List.of("web-svc"), Labels.empty());
        assertThat(spec.resourceType()).isEqualTo("load_balancer");
    }

    @Test
    void loadBalancerSpec_nullCoalescing() {
        var spec = new LoadBalancerSpec("lb", "ns", null, null, 0, null, null);
        assertThat(spec.type()).isEqualTo(LoadBalancerType.APPLICATION);
        assertThat(spec.healthCheckPath()).isEqualTo("/health");
        assertThat(spec.targetServices()).isEmpty();
        assertThat(spec.labels()).isEqualTo(Labels.empty());
    }

    @Test
    void loadBalancerSpec_jacksonDeserialization() {
        var map = Map.<String, Object>of(
                "name", "web-lb", "namespace", "prod",
                "type", "NETWORK", "healthCheckPort", 8080,
                "targetServices", List.of("svc-a", "svc-b"));
        var spec = mapper.convertValue(map, LoadBalancerSpec.class);
        assertThat(spec.name()).isEqualTo("web-lb");
        assertThat(spec.type()).isEqualTo(LoadBalancerType.NETWORK);
        assertThat(spec.targetServices()).containsExactly("svc-a", "svc-b");
    }

    // --- DnsFailoverSpec ---

    @Test
    void dnsFailoverSpec_resourceType() {
        var spec = new DnsFailoverSpec("app.example.com", "us-east.app.com",
                "eu-west.app.com", 60, FailoverPolicy.FAILOVER);
        assertThat(spec.resourceType()).isEqualTo("dns_failover");
    }

    @Test
    void dnsFailoverSpec_nullCoalescing() {
        var spec = new DnsFailoverSpec("app.example.com", "primary", "secondary", 0, null);
        assertThat(spec.policy()).isEqualTo(FailoverPolicy.FAILOVER);
    }

    @Test
    void dnsFailoverSpec_jacksonDeserialization() {
        var map = Map.<String, Object>of(
                "domainName", "api.example.com",
                "primaryEndpoint", "us-east",
                "secondaryEndpoint", "eu-west",
                "ttlSeconds", 30, "policy", "LATENCY");
        var spec = mapper.convertValue(map, DnsFailoverSpec.class);
        assertThat(spec.domainName()).isEqualTo("api.example.com");
        assertThat(spec.policy()).isEqualTo(FailoverPolicy.LATENCY);
    }

    // --- DataReplicationSpec ---

    @Test
    void dataReplicationSpec_resourceType() {
        var spec = new DataReplicationSpec("us-east-cluster", "eu-west-cluster",
                "postgres-main", ReplicationMode.ASYNC, 30);
        assertThat(spec.resourceType()).isEqualTo("data_replication");
    }

    @Test
    void dataReplicationSpec_nullCoalescing() {
        var spec = new DataReplicationSpec("src", "tgt", null, null, 0);
        assertThat(spec.mode()).isEqualTo(ReplicationMode.ASYNC);
    }

    @Test
    void dataReplicationSpec_jacksonDeserialization() {
        var map = Map.<String, Object>of(
                "sourceCluster", "primary",
                "targetCluster", "dr",
                "mode", "SEMI_SYNC",
                "lagToleranceSeconds", 10);
        var spec = mapper.convertValue(map, DataReplicationSpec.class);
        assertThat(spec.sourceCluster()).isEqualTo("primary");
        assertThat(spec.mode()).isEqualTo(ReplicationMode.SEMI_SYNC);
    }

    // --- ServiceMeshControlPlaneSpec ---

    @Test
    void serviceMeshControlPlaneSpec_resourceType() {
        var spec = new ServiceMeshControlPlaneSpec("istio-system",
                "istio/pilot:1.20", 3, Labels.empty());
        assertThat(spec.resourceType()).isEqualTo("mesh_control_plane");
    }

    @Test
    void serviceMeshControlPlaneSpec_nullCoalescing() {
        var spec = new ServiceMeshControlPlaneSpec("ns", null, 0, null);
        assertThat(spec.image()).isEqualTo("istio/pilot:latest");
        assertThat(spec.labels()).isEqualTo(Labels.empty());
    }

    @Test
    void serviceMeshControlPlaneSpec_jacksonDeserialization() {
        var map = Map.<String, Object>of(
                "namespace", "mesh-system",
                "image", "linkerd/proxy:2.14",
                "replicas", 5);
        var spec = mapper.convertValue(map, ServiceMeshControlPlaneSpec.class);
        assertThat(spec.namespace()).isEqualTo("mesh-system");
        assertThat(spec.replicas()).isEqualTo(5);
    }

    // --- SidecarProxySpec ---

    @Test
    void sidecarProxySpec_resourceType() {
        var res = new ResourceRequirements("100m", "200m", "64Mi", "128Mi");
        var spec = new SidecarProxySpec("api-server", "envoyproxy/envoy:v1.28", res);
        assertThat(spec.resourceType()).isEqualTo("sidecar_proxy");
    }

    @Test
    void sidecarProxySpec_nullCoalescing() {
        var spec = new SidecarProxySpec("svc", null, null);
        assertThat(spec.image()).isEqualTo("envoyproxy/envoy:latest");
    }

    @Test
    void sidecarProxySpec_jacksonDeserialization() {
        var map = Map.<String, Object>of(
                "targetService", "web-app",
                "image", "envoyproxy/envoy:v1.29");
        var spec = mapper.convertValue(map, SidecarProxySpec.class);
        assertThat(spec.targetService()).isEqualTo("web-app");
        assertThat(spec.image()).isEqualTo("envoyproxy/envoy:v1.29");
    }

    // --- Enums ---

    @Test
    void loadBalancerType_values() {
        assertThat(LoadBalancerType.values()).containsExactly(
                LoadBalancerType.APPLICATION, LoadBalancerType.NETWORK);
    }

    @Test
    void failoverPolicy_values() {
        assertThat(FailoverPolicy.values()).containsExactly(
                FailoverPolicy.LATENCY, FailoverPolicy.FAILOVER, FailoverPolicy.WEIGHTED);
    }

    @Test
    void replicationMode_values() {
        assertThat(ReplicationMode.values()).containsExactly(
                ReplicationMode.ASYNC, ReplicationMode.SEMI_SYNC);
    }
}

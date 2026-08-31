package io.casehub.ops.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeSpecFactory;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.ops.api.infra.InfraDesiredNodeSpec;
import io.casehub.ops.api.infra.LoadBalancerSpec;
import io.casehub.ops.api.infra.LoadBalancerType;
import io.casehub.ops.api.infra.ServiceMeshControlPlaneSpec;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InfraWrappingFactoryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void wrapsInfraNodeSpecInDesiredNodeSpec() {
        var factory = new InfraWrappingFactory(LoadBalancerSpec.class, mapper, "standalone");

        NodeSpec result = factory.create(Map.of(
                "name", "web-lb",
                "namespace", "prod",
                "type", "APPLICATION",
                "healthCheckPort", 80,
                "targetServices", List.of("web-svc")));

        assertThat(result).isInstanceOf(InfraDesiredNodeSpec.class);
        var desired = (InfraDesiredNodeSpec) result;
        assertThat(desired.backendId()).isEqualTo("standalone");
        assertThat(desired.nodeType()).isEqualTo(NodeType.of("load_balancer"));
        assertThat(desired.resourceSpec()).isInstanceOf(LoadBalancerSpec.class);
        assertThat(((LoadBalancerSpec) desired.resourceSpec()).name()).isEqualTo("web-lb");
    }

    @Test
    void backendIdFromSpecMapOverridesDefault() {
        var factory = new InfraWrappingFactory(LoadBalancerSpec.class, mapper, "standalone");

        Map<String, Object> specMap = new LinkedHashMap<>();
        specMap.put("name", "lb");
        specMap.put("namespace", "ns");
        specMap.put("healthCheckPort", 80);
        specMap.put("backendId", "aws-eks");

        NodeSpec result = factory.create(specMap);
        assertThat(((InfraDesiredNodeSpec) result).backendId()).isEqualTo("aws-eks");
    }

    @Test
    void usesDefaultBackendWhenNotInMap() {
        var factory = new InfraWrappingFactory(ServiceMeshControlPlaneSpec.class, mapper, "terraform-main");

        NodeSpec result = factory.create(Map.of("namespace", "istio-system", "replicas", 3));
        assertThat(((InfraDesiredNodeSpec) result).backendId()).isEqualTo("terraform-main");
    }

    @Test
    void factoryProviderDiscoversAllAnnotatedVariants() {
        var provider = new InfraNodeSpecFactoryProvider();
        Map<String, NodeSpecFactory> factories = provider.provide();

        assertThat(factories).containsKeys(
                "k8s_namespace", "k8s_deployment", "k8s_service", "k8s_ingress",
                "k8s_configmap", "compute_instance", "database_cluster",
                "terraform_workspace", "ansible_playbook",
                "load_balancer", "sidecar_proxy", "mesh_control_plane",
                "dns_failover", "data_replication");
        assertThat(factories).doesNotContainKey("generic_resource");
        assertThat(factories).hasSize(14);
    }

    @Test
    void dynamicHandledTypes_matchesAnnotatedVariants() {
        var types = InfraTypeDiscovery.discoverHandledTypes();
        assertThat(types).hasSize(14);
        assertThat(types).contains(
                NodeType.of("load_balancer"),
                NodeType.of("k8s_deployment"),
                NodeType.of("mesh_control_plane"));
    }
}

package io.casehub.ops.service.topology;

import io.casehub.desiredstate.api.NodeSpecFactory;
import io.casehub.desiredstate.api.NodeSpecFactoryProvider;
import io.casehub.ops.api.deployment.DeploymentNodeSpec;
import io.casehub.ops.api.infra.InfraNodeSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NodeTypeRegistryProducerTest {

    @Test
    void scanSealedInterfaces_findsDeploymentNodeTypes() {
        Map<String, String> registry = NodeTypeRegistryProducer.scanSealedInterfaces(
                DeploymentNodeSpec.class);

        assertThat(registry).isNotEmpty();
        assertThat(registry).containsKey("casehub_application");
    }

    @Test
    void scanSealedInterfaces_findsInfraNodeTypes() {
        Map<String, String> registry = NodeTypeRegistryProducer.scanSealedInterfaces(
                InfraNodeSpec.class);

        assertThat(registry).isNotEmpty();
        assertThat(registry).containsKey("compute_instance");
        assertThat(registry).containsKey("load_balancer");
    }

    @Test
    void scanSealedInterfaces_multipleInterfaces_merges() {
        Map<String, String> deploymentOnly = NodeTypeRegistryProducer.scanSealedInterfaces(
                DeploymentNodeSpec.class);
        Map<String, String> merged = NodeTypeRegistryProducer.scanSealedInterfaces(
                DeploymentNodeSpec.class, InfraNodeSpec.class);

        assertThat(merged.size()).isGreaterThan(deploymentOnly.size());
        assertThat(merged).containsKey("casehub_application");
        assertThat(merged).containsKey("compute_instance");
    }

    @Test
    void collectFactories_fromExplicitProviders() {
        var producer = new NodeTypeRegistryProducer();
        NodeSpecFactoryProvider stubProvider = () -> Map.of(
                "test_type", specMap -> null);
        var factories = producer.collectFactories(stubProvider);

        assertThat(factories).containsKey("test_type");
    }

    @Test
    void collectFactories_empty_whenNoProviders() {
        var producer = new NodeTypeRegistryProducer();
        var factories = producer.collectFactories();

        assertThat(factories).isEmpty();
    }
}

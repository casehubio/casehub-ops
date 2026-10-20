package io.casehub.ops.topology.live;

import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.HumanGating;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.infra.K8sDeploymentSpec;
import io.casehub.ops.api.infra.K8sNamespaceSpec;
import io.casehub.ops.api.infra.K8sServiceSpec;
import io.casehub.ops.api.infra.types.Labels;
import io.casehub.ops.api.infra.types.ResourceRequirements;
import io.casehub.ops.api.infra.types.ServiceType;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class Fabric8NodeProvisionerTest {

    KubernetesClient client;

    private static final DesiredStateGraph EMPTY_GRAPH =
            new DefaultDesiredStateGraphFactory().of(List.of(), List.of());

    @Test
    void handledTypes_includesK8sTypes() {
        var provisioner = new Fabric8NodeProvisioner(client, null);
        assertThat(provisioner.handledTypes()).containsExactlyInAnyOrder(
                NodeType.of("k8s_namespace"),
                NodeType.of("k8s_deployment"),
                NodeType.of("k8s_service"));
    }

    @Test
    void provision_namespace_createsResource() {
        var provisioner = new Fabric8NodeProvisioner(client, null);
        var spec = new K8sNamespaceSpec("test-ns", Labels.empty());
        var node = new DesiredNode(NodeId.of("ns"), spec, HumanGating.NONE);

        var result = provisioner.provision(node, new ProvisionContext("tenant-1", EMPTY_GRAPH));

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(client.namespaces().withName("test-ns").get()).isNotNull();
    }

    @Test
    void provision_deployment_createsResource() {
        var provisioner = new Fabric8NodeProvisioner(client, null);
        client.namespaces().resource(
                new NamespaceBuilder()
                        .withNewMetadata().withName("test-ns").endMetadata().build()
        ).create();

        var spec = new K8sDeploymentSpec("test-ns", "my-app", "nginx:alpine", 1,
                new ResourceRequirements("100m", "500m", "128Mi", "512Mi"),
                Labels.empty());
        var node = new DesiredNode(NodeId.of("dep"), spec, HumanGating.NONE);

        var result = provisioner.provision(node, new ProvisionContext("t", EMPTY_GRAPH));

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(client.apps().deployments()
                .inNamespace("test-ns").withName("my-app").get()).isNotNull();
    }

    @Test
    void provision_deployment_usesImageOverride() {
        var provisioner = new Fabric8NodeProvisioner(client, "nginx:alpine");
        client.namespaces().resource(
                new NamespaceBuilder()
                        .withNewMetadata().withName("test-ns").endMetadata().build()
        ).create();

        var spec = new K8sDeploymentSpec("test-ns", "my-app", "fake:image", 1,
                new ResourceRequirements("100m", "500m", "128Mi", "512Mi"),
                Labels.empty());
        var node = new DesiredNode(NodeId.of("dep"), spec, HumanGating.NONE);

        provisioner.provision(node, new ProvisionContext("t", EMPTY_GRAPH));

        var dep = client.apps().deployments()
                .inNamespace("test-ns").withName("my-app").get();
        assertThat(dep.getSpec().getTemplate().getSpec()
                .getContainers().get(0).getImage()).isEqualTo("nginx:alpine");
    }

    @Test
    void provision_service_createsResource() {
        var provisioner = new Fabric8NodeProvisioner(client, null);
        client.namespaces().resource(
                new NamespaceBuilder()
                        .withNewMetadata().withName("test-ns").endMetadata().build()
        ).create();

        var spec = new K8sServiceSpec("test-ns", "my-svc", 80, 8080,
                ServiceType.CLUSTER_IP, Labels.empty(), Labels.empty());
        var node = new DesiredNode(NodeId.of("svc"), spec, HumanGating.NONE);

        var result = provisioner.provision(node, new ProvisionContext("t", EMPTY_GRAPH));

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(client.services()
                .inNamespace("test-ns").withName("my-svc").get()).isNotNull();
    }

    @Test
    void deprovision_namespace_deletesResource() {
        var provisioner = new Fabric8NodeProvisioner(client, null);
        client.namespaces().resource(
                new NamespaceBuilder()
                        .withNewMetadata().withName("test-ns").endMetadata().build()
        ).create();
        var spec = new K8sNamespaceSpec("test-ns", Labels.empty());
        var node = new DesiredNode(NodeId.of("ns"), spec, HumanGating.NONE);

        var result = provisioner.deprovision(node, new DeprovisionContext("t", EMPTY_GRAPH));

        assertThat(result).isInstanceOf(DeprovisionResult.Success.class);
        assertThat(client.namespaces().withName("test-ns").get()).isNull();
    }
}

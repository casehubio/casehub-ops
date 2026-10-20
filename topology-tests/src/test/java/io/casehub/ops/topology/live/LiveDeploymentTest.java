package io.casehub.ops.topology.live;

import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.TransitionResult;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

class LiveDeploymentTest extends K3sTestBase {

    private static final Duration READY_TIMEOUT = Duration.ofSeconds(120);

    @Test
    void singleServiceBlog_deploysToK8s() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/single-service/single-node-blog.yaml");

        TransitionResult result = planAndExecute(graph);

        assertAllSucceeded(result);
        assertNamespaceExists("ghost-blog");
        awaitDeploymentReady("ghost-blog", "ghost", READY_TIMEOUT);
        assertServiceExists("ghost-blog", "ghost");
    }

    @Test
    void microservicesDev_deploysToK8s() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/microservices/single-node-dev.yaml");

        TransitionResult result = planAndExecute(graph, "nginx:alpine");

        assertAllSucceeded(result);
        assertNamespaceExists("micro-dev");
        awaitDeploymentReady("micro-dev", "user-service", READY_TIMEOUT);
        awaitDeploymentReady("micro-dev", "order-service", READY_TIMEOUT);
        awaitDeploymentReady("micro-dev", "payment-service", READY_TIMEOUT);
    }

    @Test
    void multiTierDev_deploysToK8s() throws IOException {
        CompilationResult.Lifecycle lifecycle = compileLifecycle(
                "topologies/multi-tier/single-node-dev.yaml");

        k8sClient.namespaces().resource(
                new NamespaceBuilder()
                        .withNewMetadata().withName("ecommerce-dev").endMetadata()
                        .build())
                .create();

        DesiredStateGraph fullGraph = lifecycle.phases()
                .get(lifecycle.phases().size() - 1).graph();

        TransitionResult result = planAndExecute(fullGraph, "nginx:alpine");

        assertAllSucceeded(result);
        assertNamespaceExists("ecommerce-dev");
        awaitDeploymentReady("ecommerce-dev", "product-db", READY_TIMEOUT);
        awaitDeploymentReady("ecommerce-dev", "catalog-api", READY_TIMEOUT);
        awaitDeploymentReady("ecommerce-dev", "storefront", READY_TIMEOUT);
    }
}

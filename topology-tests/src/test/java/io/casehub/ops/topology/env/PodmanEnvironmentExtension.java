package io.casehub.ops.topology.env;

import io.casehub.ops.container.podman.PodmanClient;
import io.casehub.ops.testing.env.TargetEnvironment;
import io.vertx.core.Vertx;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;

public class PodmanEnvironmentExtension implements BeforeAllCallback, AfterAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(PodmanEnvironmentExtension.class);
    static final String TEST_NETWORK = "casehub-test-net";

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface PodmanEndpoint {}

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (TargetEnvironment.current() != TargetEnvironment.PODMAN) return;

        String socketPath = System.getProperty(
            "casehub.container.podman.socket", "/var/run/podman/podman.sock");
        Vertx vertx = Vertx.vertx(new io.vertx.core.VertxOptions().setPreferNativeTransport(true));
        WebClient webClient = WebClient.create(vertx);
        PodmanClient podmanClient = new PodmanClient(webClient,
            SocketAddress.domainSocketAddress(socketPath));

        podmanClient.createNetwork(TEST_NETWORK).await().indefinitely();

        context.getStore(NAMESPACE).put("podmanClient", podmanClient);
        context.getStore(NAMESPACE).put("vertx", vertx);

        injectClient(context.getRequiredTestClass(), podmanClient);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        PodmanClient client = context.getStore(NAMESPACE)
            .remove("podmanClient", PodmanClient.class);
        if (client != null) {
            try {
                var containers = client.listContainers().await().indefinitely();
                for (var c : containers) {
                    if (c.names().stream().anyMatch(n -> n.startsWith("casehub-test-"))) {
                        client.stopContainer(c.id()).await().indefinitely();
                        client.removeContainer(c.id()).await().indefinitely();
                    }
                }
            } catch (Exception ignored) {}
        }

        Vertx vertx = context.getStore(NAMESPACE).remove("vertx", Vertx.class);
        if (vertx != null) vertx.close();
    }

    private void injectClient(Class<?> testClass, PodmanClient client)
            throws IllegalAccessException {
        for (Field field : testClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(PodmanEndpoint.class)) {
                field.setAccessible(true);
                field.set(null, client);
            }
        }
    }
}

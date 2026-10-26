package io.casehub.ops.topology.env;

import io.casehub.ops.testing.env.TargetEnvironment;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;

public class K3dClusterExtension implements BeforeAllCallback, AfterAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(K3dClusterExtension.class);
    static final String TEST_NAMESPACE = "casehub-test";

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface K3dClient {}

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (TargetEnvironment.current() != TargetEnvironment.K8S) return;

        K3sContainer k3s = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.31.2-k3s1"));
        k3s.start();
        context.getStore(NAMESPACE).put("k3s", k3s);

        String kubeConfig = k3s.getKubeConfigYaml();
        Config config = Config.fromKubeconfig(kubeConfig);
        KubernetesClient client = new KubernetesClientBuilder().withConfig(config).build();
        context.getStore(NAMESPACE).put("client", client);

        client.namespaces().resource(
            new NamespaceBuilder().withNewMetadata()
                .withName(TEST_NAMESPACE).endMetadata().build()
        ).create();

        injectClient(context.getRequiredTestClass(), client);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        KubernetesClient client = context.getStore(NAMESPACE)
            .remove("client", KubernetesClient.class);
        if (client != null) client.close();

        K3sContainer k3s = context.getStore(NAMESPACE)
            .remove("k3s", K3sContainer.class);
        if (k3s != null) k3s.stop();
    }

    private void injectClient(Class<?> testClass, KubernetesClient client)
            throws IllegalAccessException {
        for (Field field : testClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(K3dClient.class)) {
                field.setAccessible(true);
                field.set(null, client);
            }
        }
    }
}

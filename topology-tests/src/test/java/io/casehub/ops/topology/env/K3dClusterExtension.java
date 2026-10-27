package io.casehub.ops.topology.env;

import io.casehub.ops.testing.env.ImageResolver;
import io.casehub.ops.testing.env.ImageSource;
import io.casehub.ops.testing.env.TargetEnvironment;
import io.casehub.ops.topology.integration.Service;
import io.casehub.ops.topology.integration.ServiceCluster;
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

    private static final ExtensionContext.Namespace NAMESPACE      =
            ExtensionContext.Namespace.create(K3dClusterExtension.class);
    static final         String                     TEST_NAMESPACE = "casehub-test";

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface K3dClient {}

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface K3dNodeHost {}

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface K3dPortMappings {}

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (TargetEnvironment.current() != TargetEnvironment.K8S) {return;}

        K3sContainer k3s = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.31.2-k3s1"))
                                   .withExposedPorts(30080, 30081, 30082, 30083, 30084,
                                                     30085, 30086, 30087, 30088, 30089,
                                                     30090, 30091, 30092, 30093, 30094,
                                                     30095, 30096, 30097, 30098, 30099);
        k3s.start();
        context.getStore(NAMESPACE).put("k3s", k3s);

        String           kubeConfig = k3s.getKubeConfigYaml();
        Config           config     = Config.fromKubeconfig(kubeConfig);
        KubernetesClient client     = new KubernetesClientBuilder().withConfig(config).build();
        context.getStore(NAMESPACE).put("client", client);

        String nodeHost = k3s.getHost();
        context.getStore(NAMESPACE).put("nodeHost", nodeHost);

        java.util.Map<Integer, Integer> portMappings = new java.util.HashMap<>();
        for (int port = 30080; port <= 30099; port++) {
            portMappings.put(port, k3s.getMappedPort(port));
        }
        context.getStore(NAMESPACE).put("portMappings", portMappings);

        client.namespaces().resource(
                new NamespaceBuilder().withNewMetadata()
                                      .withName(TEST_NAMESPACE).endMetadata().build()
                                    ).create();

        importImages(k3s, context);

        injectClient(context.getRequiredTestClass(), client);
        injectNodeHost(context.getRequiredTestClass(), nodeHost);
        injectPortMappings(context.getRequiredTestClass(), portMappings);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        KubernetesClient client = context.getStore(NAMESPACE)
                                         .remove("client", KubernetesClient.class);
        if (client != null) {client.close();}

        K3sContainer k3s = context.getStore(NAMESPACE)
                                  .remove("k3s", K3sContainer.class);
        if (k3s != null) {k3s.stop();}
    }

    static String[] buildCtrImportCommand(String tarPath) {
        return new String[]{"ctr", "images", "import", tarPath};
    }

    private void importImages(K3sContainer k3s, ExtensionContext context) {
        ServiceCluster cluster = context.getRequiredTestClass()
                                        .getAnnotation(ServiceCluster.class);
        if (cluster == null) {return;}

        ImageSource source  = ImageResolver.currentSource();
        String      version = System.getProperty("casehub.version", "0.4-SNAPSHOT");

        for (Service svc : cluster.services()) {
            String imageRef = ImageResolver.resolveFromSource(svc.name(), version, source);
            if (ImageResolver.FALLBACK_IMAGE.equals(imageRef)) {
                System.out.printf("  [k3d] skipping import for %s (using fallback)%n", svc.name());
                continue;
            }
            java.nio.file.Path tarFile = null;
            try {
                tarFile = java.nio.file.Files.createTempFile(svc.name(), ".tar");
                Process save = new ProcessBuilder("docker", "save", imageRef, "-o", tarFile.toString())
                                       .redirectErrorStream(true).start();
                if (save.waitFor() != 0) {
                    throw new IllegalStateException("docker save failed for " + imageRef);
                }
                k3s.copyFileToContainer(
                        org.testcontainers.utility.MountableFile.forHostPath(tarFile),
                        "/tmp/" + svc.name() + ".tar");
                var result = k3s.execInContainer(buildCtrImportCommand("/tmp/" + svc.name() + ".tar"));
                if (result.getExitCode() != 0) {
                    throw new IllegalStateException("ctr import failed: " + result.getStderr());
                }
                System.out.printf("  [k3d] imported %s into k3s containerd%n", imageRef);
            } catch (Exception e) {
                throw new RuntimeException("Failed to import image " + imageRef + " into k3s", e);
            } finally {
                if (tarFile != null) {
                    try {java.nio.file.Files.deleteIfExists(tarFile);} catch (java.io.IOException ignored) {}
                }
            }
        }
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

    private void injectNodeHost(Class<?> testClass, String nodeHost)
            throws IllegalAccessException {
        for (Field field : testClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(K3dNodeHost.class)) {
                field.setAccessible(true);
                field.set(null, nodeHost);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void injectPortMappings(Class<?> testClass, java.util.Map<Integer, Integer> portMappings)
            throws IllegalAccessException {
        for (Field field : testClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(K3dPortMappings.class)) {
                field.setAccessible(true);
                field.set(null, portMappings);
            }
        }
    }
}

package io.casehub.ops.topology.env;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.container.PodmanDeploymentTarget;
import io.casehub.ops.container.podman.PodmanClient;
import io.casehub.ops.testing.env.ServiceHealthWaiter;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-live")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HelloLlmPodmanE2eTest {

    private static final String APP_NAME = "e2e-hello-llm";
    private static final String IMAGE_REF = "localhost/casehubio/casehub-runtime:latest";
    private static final String API_VERSION = "/v5.0.0/libpod";

    static PodmanClient podmanClient;
    static PodmanDeploymentTarget target;
    static WebClient webClient;
    static SocketAddress socketAddr;
    static Vertx vertx;
    static String serviceUrl;
    static String containerId;

    @BeforeAll
    static void setUp() {
        String socketPath = System.getProperty(
            "casehub.container.podman.socket",
            "/var/folders/8m/0v940n7x1m5gy95zvnl3ms700000gn/T/podman/podman-machine-default-api.sock");
        vertx = Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
        webClient = WebClient.create(vertx);
        socketAddr = SocketAddress.domainSocketAddress(socketPath);
        podmanClient = new PodmanClient(webClient, socketAddr);
        target = new PodmanDeploymentTarget(podmanClient);
    }

    @AfterAll
    static void tearDown() {
        if (containerId != null) {
            try { podmanClient.stopContainer(containerId).await().indefinitely(); } catch (Exception ignored) {}
            try { podmanClient.removeContainer(containerId).await().indefinitely(); } catch (Exception ignored) {}
        }
        if (vertx != null) vertx.close();
    }

    private static void removeContainerIfExists(String name) {
        try {
            webClient.request(HttpMethod.POST, socketAddr, 80, "localhost",
                    API_VERSION + "/containers/" + name + "/stop")
                .send().toCompletionStage().toCompletableFuture().join();
        } catch (Exception ignored) {}
        try {
            webClient.request(HttpMethod.DELETE, socketAddr, 80, "localhost",
                    API_VERSION + "/containers/" + name + "?force=true")
                .send().toCompletionStage().toCompletableFuture().join();
        } catch (Exception ignored) {}
    }

    private static String createContainerViaApi(String name, String image, Map<String, String> env) {
        removeContainerIfExists(name);
        var body = new JsonObject().put("name", name).put("image", image);
        if (env != null && !env.isEmpty()) {
            var envObj = new JsonObject();
            env.forEach(envObj::put);
            body.put("env", envObj);
        }
        body.put("portmappings", new JsonArray()
            .add(new JsonObject().put("container_port", 8080).put("host_port", 0)));
        var resp = webClient.request(HttpMethod.POST, socketAddr, 80, "localhost",
                API_VERSION + "/containers/create")
            .sendJsonObject(body)
            .toCompletionStage().toCompletableFuture().join();
        assertThat(resp.statusCode())
            .as("Podman create container (body: %s)", resp.bodyAsString())
            .isEqualTo(201);
        return new JsonObject(resp.bodyAsString()).getString("Id");
    }

    @Test
    @Order(1)
    void deploy_universalRuntimeImage() {
        containerId = createContainerViaApi(APP_NAME, IMAGE_REF,
            Map.of("llm.capability", "fast"));
        assertThat(containerId).as("Container ID").isNotBlank();

        podmanClient.startContainer(containerId).await().indefinitely();

        NodeStatus status = target.status(APP_NAME);
        assertThat(status).isEqualTo(NodeStatus.PRESENT);
    }

    @Test
    @Order(2)
    void container_becomesHealthy() {
        Optional<String> url = target.serviceUrl(APP_NAME);
        assertThat(url).as("Service URL must be present").isPresent();
        serviceUrl = url.get();

        ServiceHealthWaiter.waitForReady(serviceUrl, "/q/health/ready",
            Duration.ofSeconds(30));
    }

    @Test
    @Order(3)
    void healthEndpoint_reportsUp() throws IOException {
        HttpURLConnection conn = (HttpURLConnection)
            URI.create(serviceUrl + "/q/health/ready").toURL().openConnection();
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        try {
            assertThat(conn.getResponseCode()).isEqualTo(200);
            String body = new String(conn.getInputStream().readAllBytes());
            assertThat(body).contains("\"status\": \"UP\"");
        } finally {
            conn.disconnect();
        }
    }

    @Test
    @Order(4)
    void createApplication_persistsViaRestApi() throws IOException {
        HttpURLConnection conn = (HttpURLConnection)
            URI.create(serviceUrl + "/api/ops/applications").toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Tenancy-ID", "e2e-test");
        conn.setDoOutput(true);
        conn.getOutputStream().write(
            """
            {"name":"e2e-app","description":"created by e2e test","servicesJson":"[]"}
            """.getBytes());
        try {
            assertThat(conn.getResponseCode()).isEqualTo(200);
            String body = new String(conn.getInputStream().readAllBytes());
            assertThat(body).contains("\"name\":\"e2e-app\"");
            assertThat(body).contains("\"id\":");
        } finally {
            conn.disconnect();
        }
    }

    @Test
    @Order(5)
    void listApplications_returnsPersistedData() throws IOException {
        HttpURLConnection conn = (HttpURLConnection)
            URI.create(serviceUrl + "/api/ops/applications").toURL().openConnection();
        conn.setRequestProperty("X-Tenancy-ID", "e2e-test");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        try {
            assertThat(conn.getResponseCode()).isEqualTo(200);
            String body = new String(conn.getInputStream().readAllBytes());
            assertThat(body).contains("\"e2e-app\"");
        } finally {
            conn.disconnect();
        }
    }

    @Test
    @Order(6)
    void container_statusIsPresent() {
        NodeStatus status = target.status(APP_NAME);
        assertThat(status).isEqualTo(NodeStatus.PRESENT);
    }

    @Test
    @Order(7)
    void undeploy_removesContainer() {
        podmanClient.stopContainer(containerId).await().indefinitely();
        podmanClient.removeContainer(containerId).await().indefinitely();
        containerId = null;

        NodeStatus status = target.status(APP_NAME);
        assertThat(status).isEqualTo(NodeStatus.ABSENT);
    }
}

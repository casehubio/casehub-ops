package io.casehub.ops.topology.env;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.casehub.ops.container.PodmanDeploymentTarget;
import io.casehub.ops.container.podman.PodmanClient;
import io.vertx.core.Vertx;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PodmanDeploymentTargetServiceUrlTest {

    private HttpServer mockPodman;
    private PodmanDeploymentTarget target;
    private Vertx vertx;

    @BeforeEach
    void setUp() throws IOException {
        mockPodman = HttpServer.create(new InetSocketAddress(0), 0);
        int serverPort = mockPodman.getAddress().getPort();

        mockPodman.createContext("/v5.0.0/libpod/containers/my-svc/json", exchange -> {
            String body = """
                {
                  "NetworkSettings": {
                    "Ports": {
                      "8080/tcp": [{"HostIp": "", "HostPort": "32768"}]
                    }
                  }
                }
                """;
            respond(exchange, 200, body);
        });
        mockPodman.start();

        vertx = Vertx.vertx();
        WebClient webClient = WebClient.create(vertx);
        PodmanClient podmanClient = new PodmanClient(webClient,
            SocketAddress.inetSocketAddress(serverPort, "localhost"));
        target = new PodmanDeploymentTarget(podmanClient);
    }

    @AfterEach
    void tearDown() {
        if (mockPodman != null) mockPodman.stop(0);
        if (vertx != null) vertx.close();
    }

    @Test
    void serviceUrl_returnsMappedPort() {
        var url = target.serviceUrl("my-svc");
        assertThat(url).isPresent().hasValue("http://localhost:32768");
    }

    @Test
    void serviceUrl_returnsEmptyForUnknownContainer() {
        mockPodman.createContext("/v5.0.0/libpod/containers/unknown/json", exchange -> {
            respond(exchange, 404, "{\"cause\":\"not found\"}");
        });
        var url = target.serviceUrl("unknown");
        assertThat(url).isEmpty();
    }

    private static void respond(HttpExchange exchange,
                                 int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length > 0 ? bytes.length : -1);
        if (bytes.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
        exchange.close();
    }
}

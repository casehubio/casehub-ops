package io.casehub.ops.testing.env;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceHealthWaiterTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void returnsImmediatelyWhenHealthy() {
        server.createContext("/q/health/ready", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        ServiceHealthWaiter.waitForReady(baseUrl, "/q/health/ready", Duration.ofSeconds(5));
    }

    @Test
    void throwsOnTimeout() {
        server.createContext("/q/health/ready", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();

        assertThatThrownBy(() ->
            ServiceHealthWaiter.waitForReady(baseUrl, "/q/health/ready", Duration.ofSeconds(2)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("did not become healthy");
    }

    @Test
    void retriesUntilHealthy() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/q/health/ready", exchange -> {
            int n = calls.incrementAndGet();
            exchange.sendResponseHeaders(n >= 3 ? 200 : 503, -1);
            exchange.close();
        });
        server.start();

        ServiceHealthWaiter.waitForReady(baseUrl, "/q/health/ready", Duration.ofSeconds(10));
        assertThat(calls.get()).isGreaterThanOrEqualTo(3);
    }
}

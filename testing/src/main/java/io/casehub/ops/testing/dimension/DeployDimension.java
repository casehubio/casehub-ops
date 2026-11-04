package io.casehub.ops.testing.dimension;

import io.casehub.ops.testing.env.ServiceHealthWaiter;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class DeployDimension implements TestDimension {

    public static final String SERVICE_URL = "serviceUrl";
    public static final String CONTAINER_NAME = "containerName";

    @Override
    public String name() { return "deploy"; }

    @Override
    public Set<String> requires() { return Set.of(); }

    @Override
    public void execute(ServiceUrls urls, DimensionState state) throws Exception {
        String containerName = state.getString(CONTAINER_NAME);
        String serviceUrl = urls.serviceUrl(containerName);
        state.put(SERVICE_URL, serviceUrl);

        ServiceHealthWaiter.waitForReady(serviceUrl, "/q/health/ready",
            Duration.ofSeconds(30));

        HttpURLConnection conn = (HttpURLConnection)
            URI.create(serviceUrl + "/q/health/ready").toURL().openConnection();
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        try {
            assertThat(conn.getResponseCode())
                .as("Health endpoint status")
                .isEqualTo(200);
            String body = new String(conn.getInputStream().readAllBytes());
            assertThat(body).contains("\"status\": \"UP\"");
        } finally {
            conn.disconnect();
        }
    }
}

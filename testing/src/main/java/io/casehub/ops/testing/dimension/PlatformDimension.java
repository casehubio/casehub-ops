package io.casehub.ops.testing.dimension;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class PlatformDimension implements TestDimension {

    public static final String APPLICATION_ID = "applicationId";

    @Override
    public String name() { return "platform"; }

    @Override
    public Set<String> requires() { return Set.of("deploy"); }

    @Override
    public void execute(ServiceUrls urls, DimensionState state) throws Exception {
        String serviceUrl = state.getString(DeployDimension.SERVICE_URL);

        HttpURLConnection conn = (HttpURLConnection)
                                         URI.create(serviceUrl + "/api/ops/applications").toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Tenancy-ID", "e2e-test");
        conn.setDoOutput(true);
        try {
            conn.getOutputStream().write(
                    "{\"name\":\"e2e-app\",\"description\":\"created by dimension test\",\"servicesJson\":\"[]\"}".getBytes());
            assertThat(conn.getResponseCode()).isEqualTo(200);
            String body = new String(conn.getInputStream().readAllBytes());
            assertThat(body).contains("\"name\":\"e2e-app\"");
            String appId = new ObjectMapper().readTree(body).path("id").asText();
            assertThat(appId).as("Application ID").isNotBlank();
            state.put(APPLICATION_ID, appId);
        } finally {
            conn.disconnect();
        }

        HttpURLConnection get = (HttpURLConnection)
                                        URI.create(serviceUrl + "/api/ops/applications").toURL().openConnection();
        get.setRequestProperty("X-Tenancy-ID", "e2e-test");
        get.setConnectTimeout(2000);
        get.setReadTimeout(2000);
        try {
            assertThat(get.getResponseCode()).isEqualTo(200);
            String body = new String(get.getInputStream().readAllBytes());
            assertThat(body).contains("\"e2e-app\"");
        } finally {
            get.disconnect();
        }
    }
}

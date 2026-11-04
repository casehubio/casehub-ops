package io.casehub.ops.testing.dimension;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ChannelsDimension implements TestDimension {

    public static final String CHANNEL_ID = "channelId";

    @Override
    public String name() { return "channels"; }

    @Override
    public Set<String> requires() { return Set.of("deploy"); }

    @Override
    public void execute(ServiceUrls urls, DimensionState state) throws Exception {
        String       serviceUrl = state.getString(DeployDimension.SERVICE_URL);
        ObjectMapper mapper     = new ObjectMapper();

        HttpURLConnection create = (HttpURLConnection)
                                           URI.create(serviceUrl + "/api/channels").toURL().openConnection();
        create.setRequestMethod("POST");
        create.setRequestProperty("Content-Type", "application/json");
        create.setRequestProperty("X-Tenancy-ID", "e2e-test");
        create.setDoOutput(true);
        String channelId;
        try {
            create.getOutputStream().write(
                    "{\"name\":\"e2e-channel\",\"description\":\"created by dimension test\",\"semantic\":\"APPEND\"}".getBytes());
            assertThat(create.getResponseCode())
                    .as("Create channel response")
                    .isIn(200, 201);
            String body = new String(create.getInputStream().readAllBytes());
            assertThat(body).contains("\"name\":\"e2e-channel\"");
            channelId = mapper.readTree(body).path("id").asText();
            assertThat(channelId).as("Channel ID").isNotBlank();
            state.put(CHANNEL_ID, channelId);
        } finally {
            create.disconnect();
        }

        HttpURLConnection post = (HttpURLConnection)
                                         URI.create(serviceUrl + "/api/channels/" + channelId + "/messages")
                                            .toURL().openConnection();
        post.setRequestMethod("POST");
        post.setRequestProperty("Content-Type", "application/json");
        post.setRequestProperty("X-Tenancy-ID", "e2e-test");
        post.setDoOutput(true);
        try {
            post.getOutputStream().write(
                    "{\"sender\":\"platform\",\"type\":\"COMMAND\",\"content\":\"dimension test message\"}".getBytes());
            assertThat(post.getResponseCode())
                    .as("Post message response")
                    .isIn(200, 201);
        } finally {
            post.disconnect();
        }

        HttpURLConnection get = (HttpURLConnection)
                                        URI.create(serviceUrl + "/api/channels/" + channelId + "/messages")
                                           .toURL().openConnection();
        get.setRequestProperty("X-Tenancy-ID", "e2e-test");
        get.setConnectTimeout(2000);
        get.setReadTimeout(2000);
        try {
            assertThat(get.getResponseCode()).isEqualTo(200);
            String body = new String(get.getInputStream().readAllBytes());
            assertThat(body).contains("\"sender\":\"platform\"");
            assertThat(body).contains("dimension test message");
        } finally {
            get.disconnect();
        }
    }
}

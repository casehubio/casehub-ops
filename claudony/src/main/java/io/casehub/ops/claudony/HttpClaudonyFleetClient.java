package io.casehub.ops.claudony;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class HttpClaudonyFleetClient implements ClaudonyFleetClient {

    private static final TypeReference<List<ClaudonyAppInfo>> APP_LIST_TYPE = new TypeReference<>() {};
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public HttpClaudonyFleetClient(final String baseUrl, final ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    }

    HttpClaudonyFleetClient(final String baseUrl, final ObjectMapper objectMapper, final HttpClient httpClient) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public ClaudonyAppInfo registerApp(final ClaudonyAppSpec spec) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/"))
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(jsonBody(spec))
                .build();
        return sendAndParse(request, ClaudonyAppInfo.class);
    }

    @Override
    public ClaudonyAppInfo updateApp(final String name, final ClaudonyAppSpec spec) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/" + name))
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .PUT(jsonBody(spec))
                .build();
        return sendAndParse(request, ClaudonyAppInfo.class);
    }

    @Override
    public void deregisterApp(final String name) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/" + name + "/deregister"))
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        sendExpecting2xx(request);
    }

    @Override
    public Optional<ClaudonyAppInfo> getApp(final String name) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/" + name))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        check2xx(response);
        return Optional.of(parseBody(response.body(), ClaudonyAppInfo.class));
    }

    @Override
    public List<ClaudonyAppInfo> listApps(final String namespace) {
        String path = (namespace != null && !namespace.isBlank())
                ? "/?namespace=" + URLEncoder.encode(namespace, StandardCharsets.UTF_8)
                : "/";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri(path))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        check2xx(response);
        return parseBody(response.body(), APP_LIST_TYPE);
    }

    @Override
    public void suspendApp(final String name) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/" + name + "/suspend"))
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        sendExpecting2xx(request);
    }

    @Override
    public void resumeApp(final String name) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/" + name + "/resume"))
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        sendExpecting2xx(request);
    }

    @Override
    public void heartbeat(final String name) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/" + name + "/heartbeat"))
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        sendExpecting2xx(request);
    }

    private URI uri(final String path) {
        return URI.create(baseUrl + "/api/claudony/apps" + path);
    }

    private HttpRequest.BodyPublisher jsonBody(final Object value) {
        try {
            return HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(value));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T> T sendAndParse(final HttpRequest request, final Class<T> type) {
        HttpResponse<String> response = send(request);
        check2xx(response);
        return parseBody(response.body(), type);
    }

    private HttpResponse<String> send(final HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted calling claudony", e);
        }
    }

    private void sendExpecting2xx(final HttpRequest request) {
        check2xx(send(request));
    }

    private void check2xx(final HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ClaudonyApiException(response.statusCode(), response.body());
        }
    }

    private <T> T parseBody(final String body, final Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T> T parseBody(final String body, final TypeReference<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

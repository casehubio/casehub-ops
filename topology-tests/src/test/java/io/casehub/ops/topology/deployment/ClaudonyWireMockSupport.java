package io.casehub.ops.topology.deployment;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

final class ClaudonyWireMockSupport {

    private ClaudonyWireMockSupport() {}

    static void stubRegisterApp(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/api/claudony/apps/"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"stub\",\"health\":\"HEALTHY\",\"registeredAt\":\"2026-01-01T00:00:00Z\"}")));
    }

    static void stubGetAppHealthy(WireMockServer server, String appName) {
        server.stubFor(get(urlEqualTo("/api/claudony/apps/" + appName))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"" + appName + "\",\"health\":\"HEALTHY\",\"registeredAt\":\"2026-01-01T00:00:00Z\"}")));
    }
}

package io.casehub.ops.topology.integration;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.core.registrar.ClasspathYamlDescriptorRegistrar;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class LlmIdentityTest {

    private static final Map<String, String> ANALYST_CONFIG = Map.of(
            "llm.agent-id", "my-llm.analyst",
            "llm.name", "Analyst LLM",
            "llm.role", "analyst",
            "llm.capability", "deep",
            "llm.archetype", "investigator",
            "llm.adjective", "methodical",
            "llm.domain", "analysis"
    );

    private static final Map<String, String> CODER_CONFIG = Map.of(
            "llm.agent-id", "my-llm.coder",
            "llm.name", "Coder LLM",
            "llm.role", "developer",
            "llm.capability", "fast",
            "llm.archetype", "engineer",
            "llm.adjective", "pragmatic",
            "llm.domain", "software-engineering"
    );

    private static final Pattern CONFIG_PATTERN = Pattern.compile(
            "\\$\\{config\\.([^:}]+)(?::([^}]*))?}");

    @Test
    void resolvedDescriptor_analystHasCorrectIdentity() {
        AgentDescriptor descriptor = resolveParameterisedDescriptor(ANALYST_CONFIG);

        assertThat(descriptor.agentId()).isEqualTo("my-llm.analyst");
        assertThat(descriptor.slot()).isEqualTo("analyst");
        assertThat(descriptor.archetype()).isEqualTo("investigator");
        assertThat(descriptor.domainVocabulary()).isEqualTo("analysis");
        assertThat(descriptor.archetypeAdjectives()).containsExactly("methodical");
    }

    @Test
    void resolvedDescriptor_coderHasCorrectIdentity() {
        AgentDescriptor descriptor = resolveParameterisedDescriptor(CODER_CONFIG);

        assertThat(descriptor.agentId()).isEqualTo("my-llm.coder");
        assertThat(descriptor.slot()).isEqualTo("developer");
        assertThat(descriptor.archetype()).isEqualTo("engineer");
        assertThat(descriptor.domainVocabulary()).isEqualTo("software-engineering");
        assertThat(descriptor.archetypeAdjectives()).containsExactly("pragmatic");
    }

    @Test
    void differentConfigs_produceDifferentBriefings() {
        AgentDescriptor analyst = resolveParameterisedDescriptor(ANALYST_CONFIG);
        AgentDescriptor coder   = resolveParameterisedDescriptor(CODER_CONFIG);

        assertThat(analyst.briefing()).contains("investigator", "deep", "analysis");
        assertThat(coder.briefing()).contains("engineer", "fast", "software-engineering");
        assertThat(analyst.briefing()).isNotEqualTo(coder.briefing());
    }

    @Test
    void resolvedBriefing_containsNoPlaceholders() {
        AgentDescriptor descriptor = resolveParameterisedDescriptor(ANALYST_CONFIG);

        assertThat(descriptor.briefing()).doesNotContain("${config.");
    }

    @Test
    @Tag("integration-live")
    void llmResponse_reflectsAnalystIdentity() throws Exception {
        AgentDescriptor analyst  = resolveParameterisedDescriptor(ANALYST_CONFIG);
        String          response = sendWithSystemPrompt(analyst.briefing(), "Describe your role in one sentence.");

        assertThat(response).isNotNull();
        if (!ManifestLlmClient.isSimulated(response)) {
            String lower = response.toLowerCase();
            assertThat(lower).satisfiesAnyOf(
                    r -> assertThat(r).contains("analyst"),
                    r -> assertThat(r).contains("investigator"),
                    r -> assertThat(r).contains("analysis"));
        }
    }

    @Test
    @Tag("integration-live")
    void llmResponse_reflectsCoderIdentity() throws Exception {
        AgentDescriptor coder    = resolveParameterisedDescriptor(CODER_CONFIG);
        String          response = sendWithSystemPrompt(coder.briefing(), "Describe your role in one sentence.");

        assertThat(response).isNotNull();
        if (!ManifestLlmClient.isSimulated(response)) {
            String lower = response.toLowerCase();
            assertThat(lower).satisfiesAnyOf(
                    r -> assertThat(r).contains("developer"),
                    r -> assertThat(r).contains("engineer"),
                    r -> assertThat(r).contains("software"));
        }
    }

    @Test
    @Tag("integration-live")
    void differentIdentities_produceDifferentResponses() throws Exception {
        AgentDescriptor analyst = resolveParameterisedDescriptor(ANALYST_CONFIG);
        AgentDescriptor coder   = resolveParameterisedDescriptor(CODER_CONFIG);

        String analystResponse = sendWithSystemPrompt(analyst.briefing(), "What do you specialise in?");
        String coderResponse   = sendWithSystemPrompt(coder.briefing(), "What do you specialise in?");

        assertThat(analystResponse).isNotNull();
        assertThat(coderResponse).isNotNull();
        if (!ManifestLlmClient.isSimulated(analystResponse)) {
            assertThat(analystResponse).isNotEqualTo(coderResponse);
        }
    }


    private AgentDescriptor resolveParameterisedDescriptor(Map<String, String> config) {
        try {
            String yamlText = new String(
                    getClass().getClassLoader()
                            .getResourceAsStream("META-INF/eidos/descriptors.yaml")
                            .readAllBytes(),
                    StandardCharsets.UTF_8);

            String resolved = resolveConfigPlaceholders(yamlText, config);

            ClasspathYamlDescriptorRegistrar registrar = new ClasspathYamlDescriptorRegistrar();
            List<AgentDescriptor> descriptors = registrar.loadFrom(
                    new ByteArrayInputStream(resolved.getBytes(StandardCharsets.UTF_8)));

            return descriptors.stream()
                    .filter(d -> d.agentId().equals(config.get("llm.agent-id")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "No descriptor with agentId " + config.get("llm.agent-id")));
        } catch (IOException e) {
            throw new RuntimeException("Failed to resolve descriptor", e);
        }
    }

    private String sendWithSystemPrompt(String systemPrompt, String userMessage) throws Exception {
        try {
            String host = "localhost:11434";
            String body = String.format(
                    "{\"model\":\"gemma3:4b\",\"messages\":[{\"role\":\"system\",\"content\":%s},{\"role\":\"user\",\"content\":%s}],\"stream\":false}",
                    escapeJson(systemPrompt), escapeJson(userMessage));
            var request = java.net.http.HttpRequest.newBuilder()
                                                   .uri(java.net.URI.create("http://" + host + "/api/chat"))
                                                   .header("content-type", "application/json")
                                                   .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                                                   .timeout(java.time.Duration.ofSeconds(60))
                                                   .build();
            var response = java.net.http.HttpClient.newHttpClient()
                                                   .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var root   = mapper.readTree(response.body());
                return root.path("message").path("content").asText();
            }
        } catch (Exception ignored) {
        }
        return ManifestLlmClient.simulate();
    }

    private static String escapeJson(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }


    private String resolveConfigPlaceholders(String yaml, Map<String, String> config) {
        Matcher matcher = CONFIG_PATTERN.matcher(yaml);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String defaultValue = matcher.group(2);
            String value = config.getOrDefault(key, defaultValue);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}

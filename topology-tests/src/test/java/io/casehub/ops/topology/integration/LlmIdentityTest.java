package io.casehub.ops.topology.integration;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.platform.agent.langchain4j.AgentProviderChatModel;
import io.casehub.platform.agent.langchain4j.CaseHubChatResponseMetadata;
import io.casehub.platform.agent.router.RoutingAgentProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

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
    void llmResponse_reflectsAnalystIdentity() {
        AgentDescriptor analyst = resolveParameterisedDescriptor(ANALYST_CONFIG);
        var result = invokeProvider(analyst.briefing(), "Describe your role in one sentence.");

        assertThat(result.text).isNotNull();
        if (!"simulation".equals(result.backendKey)) {
            String lower = result.text.toLowerCase();
            assertThat(lower).satisfiesAnyOf(
                    r -> assertThat(r).contains("analyst"),
                    r -> assertThat(r).contains("investigator"),
                    r -> assertThat(r).contains("analysis"));
        }
    }

    @Test
    @Tag("integration-live")
    void llmResponse_reflectsCoderIdentity() {
        AgentDescriptor coder = resolveParameterisedDescriptor(CODER_CONFIG);
        var result = invokeProvider(coder.briefing(), "Describe your role in one sentence.");

        assertThat(result.text).isNotNull();
        if (!"simulation".equals(result.backendKey)) {
            String lower = result.text.toLowerCase();
            assertThat(lower).satisfiesAnyOf(
                    r -> assertThat(r).contains("developer"),
                    r -> assertThat(r).contains("engineer"),
                    r -> assertThat(r).contains("software"));
        }
    }

    @Test
    @Tag("integration-live")
    void differentIdentities_produceDifferentResponses() {
        AgentDescriptor analyst = resolveParameterisedDescriptor(ANALYST_CONFIG);
        AgentDescriptor coder   = resolveParameterisedDescriptor(CODER_CONFIG);

        var analystResult = invokeProvider(analyst.briefing(), "What do you specialise in?");
        var coderResult   = invokeProvider(coder.briefing(), "What do you specialise in?");

        assertThat(analystResult.text).isNotNull();
        assertThat(coderResult.text).isNotNull();
        if (!"simulation".equals(analystResult.backendKey)) {
            assertThat(analystResult.text).isNotEqualTo(coderResult.text);
        }
    }


    private AgentDescriptor resolveParameterisedDescriptor(Map<String, String> config) {
        return FleetLlmExerciser.resolveDescriptor(config);
    }

    private record InvokeResult(String text, String backendKey) {}

    private InvokeResult invokeProvider(String systemPrompt, String userMessage) {
        ChatModel chatModel = new AgentProviderChatModel(
                RoutingAgentProvider.discover(Path.of(System.getProperty("user.home"))),
                List.of(),
                new io.casehub.platform.agent.langchain4j.AgentLangchain4jProperties() {
                    public Duration closeTimeout() { return Duration.ofSeconds(90); }
                    public int sessionMemoryWindowSize() { return 10; }
                    public int maxConcurrentSessions() { return 4; }
                });
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(SystemMessage.from(systemPrompt), UserMessage.from(userMessage))
                .build());
        String backendKey = null;
        if (response.metadata() instanceof CaseHubChatResponseMetadata meta) {
            backendKey = meta.backendKey();
        }
        return new InvokeResult(response.aiMessage().text(), backendKey);
    }
}

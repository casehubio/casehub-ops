package io.casehub.ops.topology.integration;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.platform.agent.langchain4j.AgentProviderChatModel;
import io.casehub.platform.agent.langchain4j.CaseHubChatResponseMetadata;
import io.casehub.platform.agent.router.RoutingAgentProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-live")
class HelloWorldLlmTest {

    private final ChatModel chatModel = new AgentProviderChatModel(
            RoutingAgentProvider.discover(Path.of(System.getProperty("user.home"))),
            List.of(),
            new io.casehub.platform.agent.langchain4j.AgentLangchain4jProperties() {
                public Duration closeTimeout() { return Duration.ofSeconds(90); }
                public int sessionMemoryWindowSize() { return 10; }
                public int maxConcurrentSessions() { return 4; }
            });

    @Test
    void manifestRouting_respondsToHello() {
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(SystemMessage.from("You are a helpful assistant."),
                          UserMessage.from("Say hello and nothing else."))
                .build());

        String text = response.aiMessage().text();
        assertThat(text).isNotEmpty();

        if (response.metadata() instanceof CaseHubChatResponseMetadata meta) {
            System.out.printf("  route: %s/%s%n", meta.backendKey(), meta.instanceId());
            if (!"simulation".equals(meta.backendKey())) {
                assertThat(text.toLowerCase()).contains("hello");
            }
        }
    }
}

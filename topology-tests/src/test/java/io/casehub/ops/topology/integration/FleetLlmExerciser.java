package io.casehub.ops.topology.integration;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.core.registrar.ClasspathYamlDescriptorRegistrar;
import io.casehub.platform.agent.langchain4j.AgentProviderChatModel;
import io.casehub.platform.agent.langchain4j.CaseHubChatResponseMetadata;
import io.casehub.platform.agent.router.RoutingAgentProvider;
import io.casehub.yaml.core.resolver.VariableSource;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FleetLlmExerciser {

    private static final String USER_PROMPT =
            "A production system is showing intermittent 500 errors."
            + " What is your specific first action? Answer in 1-2 sentences.";

    private static volatile ChatModel cachedChatModel;

    public static io.casehub.ops.testing.env.LlmResponse exercise(String appId, Map<String, ?> config) {
        Map<String, String> stringConfig = toStringMap(config);
        AgentDescriptor     descriptor   = resolveDescriptor(stringConfig);

        ChatResponse response = chatModel().chat(ChatRequest.builder()
                                                            .messages(SystemMessage.from(descriptor.briefing()),
                                                                      UserMessage.from(USER_PROMPT))
                                                            .build());

        String text       = response.aiMessage().text();
        String backendKey = null;
        String instanceId = null;
        if (response.metadata() instanceof CaseHubChatResponseMetadata meta) {
            backendKey = meta.backendKey();
            instanceId = meta.instanceId();
        }

        System.out.printf("  [%s] route: %s/%s (%d chars)%n",
                          appId, backendKey, instanceId, text.length());

        return new io.casehub.ops.testing.env.LlmResponse(appId, descriptor.slot(), descriptor.domainVocabulary(),
                                                          text, backendKey, instanceId);
    }

    public static List<io.casehub.ops.testing.env.LlmResponse> exerciseFleet(List<String> appIds,
                                                                             java.util.function.Function<String, Map<String, ?>> configProvider) {
        List<io.casehub.ops.testing.env.LlmResponse> responses = new ArrayList<>();
        for (String appId : appIds) {
            responses.add(exercise(appId, configProvider.apply(appId)));
        }
        return responses;
    }

    private static ChatModel chatModel() {
        if (cachedChatModel == null) {
            String profile = System.getenv("CASEHUB_AGENT_PROFILE");
            if (profile == null) {profile = System.getProperty("casehub.agent.profile");}
            Path projectDir = Path.of(System.getProperty("user.home"));
            var provider = profile != null
                           ? RoutingAgentProvider.discover(projectDir, profile)
                           : RoutingAgentProvider.discover(projectDir);
            cachedChatModel = new AgentProviderChatModel(provider, List.of(),
                                                         new SimpleProperties());
        }
        return cachedChatModel;
    }

    static AgentDescriptor resolveDescriptor(Map<String, String> config) {
        InputStream yaml = FleetLlmExerciser.class.getClassLoader()
                                                  .getResourceAsStream("META-INF/eidos/descriptors.yaml");
        Map<String, VariableSource> sources = new LinkedHashMap<>();
        sources.put("config", key -> {
            int    colon      = key.indexOf(':');
            String actualKey  = colon >= 0 ? key.substring(0, colon) : key;
            String defaultVal = colon >= 0 ? key.substring(colon + 1) : null;
            String value      = config.get(actualKey);
            return value != null ? value : defaultVal;
        });
        var                   registrar   = new ClasspathYamlDescriptorRegistrar();
        List<AgentDescriptor> descriptors = registrar.loadFrom(yaml, null, sources);
        String                agentId     = config.getOrDefault("llm.agent-id", "my-llm-fleet");
        return descriptors.stream()
                          .filter(d -> d.agentId().equals(agentId))
                          .findFirst()
                          .orElseThrow(() -> new AssertionError(
                                  "No descriptor with agentId " + agentId));
    }

    private static Map<String, String> toStringMap(Map<String, ?> config) {
        var result = new java.util.HashMap<String, String>();
        for (var entry : config.entrySet()) {
            result.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return result;
    }

    private record SimpleProperties() implements io.casehub.platform.agent.langchain4j.AgentLangchain4jProperties {
        public Duration closeTimeout()       {return Duration.ofSeconds(90);}

        public int sessionMemoryWindowSize() {return 10;}

        public int maxConcurrentSessions()   {return 4;}
    }

    private FleetLlmExerciser() {}
}

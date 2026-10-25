package io.casehub.ops.api.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AdaptationActionSpecTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void addNodeActionSpecJsonRoundTrip() throws Exception {
        var template = new NodeTemplate(
                "specialist-${catalog.name}",
                "claudony_app",
                Map.of("name", "specialist-${catalog.name}",
                       "displayName", "${catalog.name}",
                       "image", "casehubio/my-llm-app:latest"));
        var spec = new AdaptationActionSpec.AddNodeActionSpec(
                template, List.of("engine"));
        String json = mapper.writeValueAsString(spec);
        AdaptationActionSpec deserialized = mapper.readValue(json, AdaptationActionSpec.class);
        assertThat(deserialized).isInstanceOf(AdaptationActionSpec.AddNodeActionSpec.class);
        var ans = (AdaptationActionSpec.AddNodeActionSpec) deserialized;
        assertThat(ans.template().nodeType()).isEqualTo("claudony_app");
        assertThat(ans.template().nodeId()).isEqualTo("specialist-${catalog.name}");
        assertThat(ans.dependsOn()).containsExactly("engine");
    }

    @Test
    void addNodeActionSpecNullDependsOnDefaultsToEmpty() {
        var template = new NodeTemplate("node-1", "claudony_app", Map.of("name", "n"));
        var spec = new AdaptationActionSpec.AddNodeActionSpec(template, null);
        assertThat(spec.dependsOn()).isEmpty();
    }

    @Test
    void addNodeActionSpecRequiresTemplate() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AdaptationActionSpec.AddNodeActionSpec(null, List.of()));
    }

    @Test
    void nodeTemplateValidation() {
        assertThatNullPointerException()
                .isThrownBy(() -> new NodeTemplate(null, "type", Map.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> new NodeTemplate("id", null, Map.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> new NodeTemplate("id", "type", null));
    }

    @Test
    void nodeTemplateRejectsPlaceholdersInNodeType() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new NodeTemplate("id", "${catalog.type}", Map.of()))
                .withMessageContaining("nodeType must not contain placeholders");
    }

    @Test
    void llmDecisionDeployRoundTrip() throws Exception {
        var decision = new LlmDecision("deploy",
                Map.of("name", "product-x-specialist", "role", "specialist"));
        String json = mapper.writeValueAsString(decision);
        var deserialized = mapper.readValue(json, LlmDecision.class);
        assertThat(deserialized.isDeploy()).isTrue();
        assertThat(deserialized.isReject()).isFalse();
        assertThat(deserialized.selectedCatalogEntry().get("name"))
                .isEqualTo("product-x-specialist");
    }

    @Test
    void llmDecisionReject() {
        var decision = new LlmDecision("reject", null);
        assertThat(decision.isReject()).isTrue();
        assertThat(decision.isDeploy()).isFalse();
    }

    @Test
    void llmDecisionRequiresAction() {
        assertThatNullPointerException()
                .isThrownBy(() -> new LlmDecision(null, null));
    }

    @Test
    void existingScaleActionSpecRoundTrip() throws Exception {
        var spec = new AdaptationActionSpec.ScaleActionSpec("agent-1", 1, 5);
        String json = mapper.writeValueAsString(spec);
        AdaptationActionSpec deserialized = mapper.readValue(json, AdaptationActionSpec.class);
        assertThat(deserialized).isInstanceOf(AdaptationActionSpec.ScaleActionSpec.class);
        var s = (AdaptationActionSpec.ScaleActionSpec) deserialized;
        assertThat(s.target()).isEqualTo("agent-1");
        assertThat(s.min()).isEqualTo(1);
        assertThat(s.max()).isEqualTo(5);
    }
}

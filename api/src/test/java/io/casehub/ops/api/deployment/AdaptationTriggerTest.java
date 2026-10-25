package io.casehub.ops.api.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AdaptationTriggerTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void patternTriggerJsonRoundTrip() throws Exception {
        var trigger = new PatternTrigger("high-load", 0.7, null, null);
        String json = mapper.writeValueAsString(trigger);
        AdaptationTrigger deserialized = mapper.readValue(json, AdaptationTrigger.class);
        assertThat(deserialized).isInstanceOf(PatternTrigger.class);
        var pt = (PatternTrigger) deserialized;
        assertThat(pt.situation()).isEqualTo("high-load");
        assertThat(pt.minConfidence()).isEqualTo(0.7);
    }

    @Test
    void llmEvaluatedTriggerJsonRoundTrip() throws Exception {
        var trigger = new LlmEvaluatedTrigger(
                "deployer", "specialist-catalog", 0.5,
                Duration.ofSeconds(60), Duration.ofSeconds(30));
        String json = mapper.writeValueAsString(trigger);
        AdaptationTrigger deserialized = mapper.readValue(json, AdaptationTrigger.class);
        assertThat(deserialized).isInstanceOf(LlmEvaluatedTrigger.class);
        var lt = (LlmEvaluatedTrigger) deserialized;
        assertThat(lt.agent()).isEqualTo("deployer");
        assertThat(lt.catalog()).isEqualTo("specialist-catalog");
        assertThat(lt.minConfidence()).isEqualTo(0.5);
        assertThat(lt.timeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void backwardCompatibility_noTypeField_deserializesAsPattern() throws Exception {
        String json = """
                {"situation":"high-load","minConfidence":0.7}
                """;
        AdaptationTrigger deserialized = mapper.readValue(json, AdaptationTrigger.class);
        assertThat(deserialized).isInstanceOf(PatternTrigger.class);
    }

    @Test
    void patternTrigger_validationPreserved() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PatternTrigger(null, 0.7, null, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PatternTrigger("x", 1.5, null, null));
    }

    @Test
    void llmEvaluatedTrigger_requiresAgentAndCatalog() {
        assertThatNullPointerException()
                .isThrownBy(() -> new LlmEvaluatedTrigger(
                        null, "catalog", 0.5, null, Duration.ofSeconds(30)));
        assertThatNullPointerException()
                .isThrownBy(() -> new LlmEvaluatedTrigger(
                        "agent", null, 0.5, null, Duration.ofSeconds(30)));
    }

    @Test
    void llmEvaluatedTrigger_requiresTimeout() {
        assertThatNullPointerException()
                .isThrownBy(() -> new LlmEvaluatedTrigger(
                        "agent", "catalog", 0.5, null, null));
    }

    @Test
    void patternTrigger_effectiveDeactivateBelow_defaultsToMinConfidence() {
        var trigger = new PatternTrigger("test", 0.7, null, null);
        assertThat(trigger.effectiveDeactivateBelow()).isEqualTo(0.7);
    }

    @Test
    void patternTrigger_effectiveDeactivateBelow_usesExplicitValue() {
        var trigger = new PatternTrigger("test", 0.7, 0.5, null);
        assertThat(trigger.effectiveDeactivateBelow()).isEqualTo(0.5);
    }

    @Test
    void patternTrigger_effectiveCooldown_defaultsToZero() {
        var trigger = new PatternTrigger("test", 0.7, null, null);
        assertThat(trigger.effectiveCooldown()).isEqualTo(Duration.ZERO);
    }

    @Test
    void llmEvaluatedTrigger_effectiveCooldown_defaultsToZero() {
        var trigger = new LlmEvaluatedTrigger("agent", "catalog", 0.5, null, Duration.ofSeconds(30));
        assertThat(trigger.effectiveCooldown()).isEqualTo(Duration.ZERO);
    }
}

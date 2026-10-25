package io.casehub.ops.service.adaptation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.deployment.LlmEvaluatedTrigger;
import io.casehub.ops.api.deployment.PatternTrigger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptationRuleCompilerTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final AdaptationRuleCompiler compiler = new AdaptationRuleCompiler(mapper);

    @Test
    void compilesLlmEvaluatedRuleFromYaml() {
        @SuppressWarnings("unchecked")
        Map<String, Object> rawYaml = mapper.convertValue(
                mapper.valueToTree(Map.of(
                        "adaptations", List.of(Map.of(
                                "name", "deploy-specialist",
                                "trigger", Map.of(
                                        "type", "llm-evaluated",
                                        "agent", "deployer",
                                        "catalog", "specialist-catalog",
                                        "minConfidence", 0.5,
                                        "timeout", "PT30S"),
                                "actions", List.of(Map.of(
                                        "type", "add_node",
                                        "template", Map.of(
                                                "nodeId", "specialist-${catalog.name}",
                                                "nodeType", "claudony_app",
                                                "spec", Map.of(
                                                        "name", "specialist-${catalog.name}",
                                                        "displayName", "${catalog.name}",
                                                        "description", "test",
                                                        "image", "img:latest")),
                                        "dependsOn", List.of("engine"))))))),
                Map.class);

        var nodeTypeRegistry = Map.<String, Class<?>>of(
                "claudony_app", ClaudonyAppNodeSpec.class);

        List<AdaptationRule> rules = compiler.compile(
                rawYaml, nodeTypeRegistry, new DefaultDesiredStateGraphFactory());

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).name()).isEqualTo("deploy-specialist");
        assertThat(rules.get(0).trigger()).isInstanceOf(LlmEvaluatedTrigger.class);
        var trigger = (LlmEvaluatedTrigger) rules.get(0).trigger();
        assertThat(trigger.agent()).isEqualTo("deployer");
        assertThat(trigger.catalog()).isEqualTo("specialist-catalog");
    }

    @Test
    void returnsEmptyWhenNoAdaptationsSection() {
        Map<String, Object> rawYaml = Map.of("nodes", Map.of());

        List<AdaptationRule> rules = compiler.compile(
                rawYaml, Map.of(), new DefaultDesiredStateGraphFactory());

        assertThat(rules).isEmpty();
    }

    @Test
    void compilesPatternTriggerRules() {
        @SuppressWarnings("unchecked")
        Map<String, Object> rawYaml = mapper.convertValue(
                mapper.valueToTree(Map.of(
                        "adaptations", List.of(Map.of(
                                "name", "scale-on-load",
                                "trigger", Map.of(
                                        "situation", "high-load",
                                        "minConfidence", 0.7),
                                "actions", List.of(Map.of(
                                        "type", "scale",
                                        "target", "risk-agent",
                                        "min", 1,
                                        "max", 5)))))),
                Map.class);

        List<AdaptationRule> rules = compiler.compile(
                rawYaml, Map.of(), new DefaultDesiredStateGraphFactory());

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).name()).isEqualTo("scale-on-load");
        assertThat(rules.get(0).trigger()).isInstanceOf(PatternTrigger.class);
    }
}

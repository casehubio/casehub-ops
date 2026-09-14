package io.casehub.ops.app.lifecycle.summarisation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.yaml.*;
import io.casehub.blocks.summarisation.yaml.builtin.*;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.casehub.platform.expression.MvelExpressionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentMonitoringPipelineTest {

    static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());
    static final ExpressionEngine EXPR = new MvelExpressionEngine();
    static final EventLevel INPUT = new EventLevel("input", 0);

    PipelineDefinition definition;
    SummariserRegistry registry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws IOException {
        var yaml = getClass().getResourceAsStream(
                "/META-INF/summarisation/deployment-monitoring.yaml");
        definition = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();

        registry = new SummariserRegistry();
        registry.register("threshold-classify", (SummariserFactory)
                config -> ThresholdClassifySummariser.create(config, EXPR));
        registry.register("phase-detect", (SummariserFactory) config -> {
            var aggregateFields = definition.levels().stream()
                    .filter(l -> l.summariser().type().equals("phase-detect"))
                    .findFirst()
                    .map(LevelDefinition::aggregateFields)
                    .orElse(List.of());
            return PhaseDetectSummariser.create(config, aggregateFields);
        });
    }

    @Test
    void yamlParsesCorrectly() {
        assertThat(definition.name()).isEqualTo("deployment-monitoring");
        assertThat(definition.levels()).hasSize(2);
        assertThat(definition.levels().get(0).name()).isEqualTo("anomalies");
        assertThat(definition.levels().get(1).name()).isEqualTo("phases");
    }

    @Test
    void validationPasses() {
        var errors = new PipelineValidator().validate(definition, registry);
        var realErrors = errors.stream()
                .filter(e -> e.level() == PipelineValidator.ValidationError.Level.ERROR)
                .toList();
        assertThat(realErrors).isEmpty();
    }

    @Test
    void classifiesProvisionFailure() {
        var pipeline = new PipelineCompiler().compile(definition, registry, null, EXPR);

        var anomalies = new ArrayList<LevelEvent<?>>();
        pipeline.<Object>outputBus("anomalies").subscribe(e -> true, anomalies::add);

        publishFaultEvent(pipeline, "node-1", "agent", "PROVISION_FAILED", "timeout", "tenant-1");
        publishFaultEvent(pipeline, "node-2", "channel", "DEPENDENCY_UNAVAILABLE", "db down", "tenant-1");
        for (int i = 0; i < 8; i++) {
            publishRecoveryEvent(pipeline, "node-" + (i + 3), "agent", "tenant-1");
        }
        pipeline.tick(1000L).toCompletableFuture().join();

        var provisionFailures = anomalies.stream()
                .filter(e -> e.payload() instanceof Map<?, ?> m && "PROVISION_FAILURE".equals(m.get("category")))
                .toList();
        assertThat(provisionFailures).hasSize(1);

        var depFaults = anomalies.stream()
                .filter(e -> e.payload() instanceof Map<?, ?> m && "DEPENDENCY_FAULT".equals(m.get("category")))
                .toList();
        assertThat(depFaults).hasSize(1);

        var stabilising = anomalies.stream()
                .filter(e -> e.payload() instanceof Map<?, ?> m && "STABILISING".equals(m.get("category")))
                .toList();
        assertThat(stabilising).hasSize(8);
    }

    @Test
    void phaseTransitionsToDegraded() {
        var pipeline = new PipelineCompiler().compile(definition, registry, null, EXPR);

        var phases = new ArrayList<LevelEvent<?>>();
        pipeline.<Object>outputBus("phases").subscribe(e -> true, phases::add);

        for (int i = 0; i < 10; i++) {
            publishFaultEvent(pipeline, "node-" + i, "agent", "PROVISION_FAILED", "timeout", null);
        }
        pipeline.tick(1000L).toCompletableFuture().join();
        pipeline.tick(70_000L).toCompletableFuture().join();

        assertThat(phases).hasSizeGreaterThanOrEqualTo(1);
        assertThat(phases.get(0).payload()).isInstanceOfSatisfying(Map.class, m -> {
            assertThat(m).containsEntry("from", "HEALTHY");
            assertThat(m).containsEntry("to", "DEGRADED");
        });
    }

    @SuppressWarnings("unchecked")
    private void publishFaultEvent(CompiledPipeline<?> pipeline,
                                    String nodeId, String nodeType, String faultType,
                                    String reason, String tenancyId) {
        var bus = (EventStreamBus<Object>) (Object) pipeline.inputBus();
        bus.publish(new LevelEvent<>(
                Map.<String, Object>of(
                        "nodeId", nodeId,
                        "nodeType", nodeType,
                        "faultType", faultType,
                        "reason", reason,
                        "graphVersion", 1L,
                        "parentNodeId", ""),
                System.currentTimeMillis(), INPUT, tenancyId));
    }

    @SuppressWarnings("unchecked")
    private void publishRecoveryEvent(CompiledPipeline<?> pipeline,
                                       String nodeId, String nodeType, String tenancyId) {
        var bus = (EventStreamBus<Object>) (Object) pipeline.inputBus();
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("nodeId", nodeId);
        payload.put("nodeType", nodeType);
        payload.put("faultType", null);
        payload.put("graphVersion", 1L);
        payload.put("parentNodeId", "");
        bus.publish(new LevelEvent<>(payload, System.currentTimeMillis(), INPUT, tenancyId));
    }
}

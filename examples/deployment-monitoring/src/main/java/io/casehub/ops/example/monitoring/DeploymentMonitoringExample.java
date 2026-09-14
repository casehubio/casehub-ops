package io.casehub.ops.example.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.yaml.*;
import io.casehub.blocks.summarisation.yaml.builtin.*;
import io.casehub.platform.expression.MvelExpressionEngine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeploymentMonitoringExample {

    public record Result(List<Map<String, Object>> anomalies,
                         List<Map<String, Object>> phases) {}

    @SuppressWarnings("unchecked")
    public static Result run() throws IOException {
        var mapper = new ObjectMapper(new YAMLFactory());
        var expr = new MvelExpressionEngine();

        var yaml = DeploymentMonitoringExample.class.getResourceAsStream(
                "/META-INF/summarisation/deployment-monitoring.yaml");
        var definition = mapper.readValue(yaml, PipelineWrapper.class).pipeline();

        var registry = new SummariserRegistry();
        registry.register("threshold-classify", (SummariserFactory)
                config -> ThresholdClassifySummariser.create(config, expr));
        registry.register("phase-detect", (SummariserFactory) config -> {
            var aggregateFields = definition.levels().stream()
                    .filter(l -> l.summariser().type().equals("phase-detect"))
                    .findFirst()
                    .map(LevelDefinition::aggregateFields)
                    .orElse(List.of());
            return PhaseDetectSummariser.create(config, aggregateFields);
        });

        var pipeline = new PipelineCompiler().compile(definition, registry, null, expr);

        var anomalies = new ArrayList<Map<String, Object>>();
        var phases = new ArrayList<Map<String, Object>>();

        pipeline.<Object>outputBus("anomalies").subscribe(e -> true, e -> {
            if (e.payload() instanceof Map<?, ?> m) {
                anomalies.add((Map<String, Object>) m);
            }
        });
        pipeline.<Object>outputBus("phases").subscribe(e -> true, e -> {
            if (e.payload() instanceof Map<?, ?> m) {
                phases.add((Map<String, Object>) m);
            }
        });

        var scenario = DeploymentEventSimulator.cascadeScenario();

        System.out.println("=== Deployment Monitoring — Cascade Failure Scenario ===");
        System.out.println();
        System.out.printf("  %d L1 events in scenario%n", scenario.size());
        System.out.println();

        DeploymentEventSimulator.publishTo(pipeline.inputBus(), scenario);

        long maxTimestamp = scenario.stream()
                .mapToLong(DeploymentEventSimulator.SimulatedEvent::timestamp)
                .max().orElse(0);
        pipeline.tick(maxTimestamp + 1000).toCompletableFuture().join();
        pipeline.tick(maxTimestamp + 70_000).toCompletableFuture().join();
        pipeline.flush().toCompletableFuture().join();

        System.out.println("--- L2: Anomaly Classification ---");
        for (var a : anomalies) {
            System.out.printf("  [%-20s] %-20s — %s  (severity: %s)%n",
                    a.getOrDefault("category", "?"),
                    a.getOrDefault("nodeId", "?"),
                    a.getOrDefault("reason", a.getOrDefault("nodeType", "")),
                    a.getOrDefault("severity", "?"));
        }
        System.out.println();

        System.out.println("--- L3: Phase Transitions ---");
        if (phases.isEmpty()) {
            System.out.println("  (no phase transitions detected)");
        }
        for (var p : phases) {
            System.out.printf("  %s → %s%n",
                    p.getOrDefault("from", "?"),
                    p.getOrDefault("to", "?"));
        }
        System.out.println();
        System.out.println("=== Done ===");

        return new Result(List.copyOf(anomalies), List.copyOf(phases));
    }

    public static void main(String[] args) throws IOException {
        run();
    }
}

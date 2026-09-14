package io.casehub.ops.app.lifecycle.ras;

import io.casehub.ops.api.lifecycle.DeploymentSummarisationEventTypes;
import io.casehub.platform.api.expression.LambdaExpression;
import io.casehub.ras.api.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.*;

@ApplicationScoped
public class DeploymentTopologySituationDefinitionProvider implements SituationDefinitionProvider {

    public static final String DEGRADED_ID = "deployment-degraded";
    public static final String RECOVERING_ID = "deployment-recovering";
    public static final String HEALTHY_ID = "deployment-healthy";

    @Override
    public List<GanglionDescriptor> ganglionDescriptors() {
        return List.of(
                ganglion(DEGRADED_ID, ctx -> {
                    Map<String, Object> data = data(ctx);
                    return data != null && "DEGRADED".equals(data.get("to"));
                }, 0.95),
                ganglion(RECOVERING_ID, ctx -> {
                    Map<String, Object> data = data(ctx);
                    return data != null && "RECOVERING".equals(data.get("to"));
                }, 0.9),
                ganglion(HEALTHY_ID, ctx -> {
                    Map<String, Object> data = data(ctx);
                    return data != null && "HEALTHY".equals(data.get("to"));
                }, 0.95));
    }

    @Override
    public List<SituationRegistration> registrations() {
        return List.of(new SituationRegistration(
                new SituationDefinition(
                        "deployment.sustained-degradation",
                        Set.of(DeploymentSummarisationEventTypes.PHASE),
                        Duration.ofMinutes(15),
                        null,
                        new ChainMode.Streak(DEGRADED_ID, 2),
                        new TriggerAction.CreateCase(
                                new CaseTriggerConfig("ops-deployment", "escalate", "1.0", Map.of())),
                        new TriggerMode.FireOnce()),
                null));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map ctx) {
        Object d = ctx.get("data");
        return d instanceof Map ? (Map<String, Object>) d : null;
    }

    @SuppressWarnings("unchecked")
    private static GanglionDescriptor ganglion(String id,
                                                java.util.function.Function<Map, Boolean> condition,
                                                double confidence) {
        return new GanglionDescriptor.ExpressionRules(
                id,
                Set.of(DeploymentSummarisationEventTypes.PHASE),
                List.of(new GanglionDescriptor.ExpressionRules.Rule(
                        new LambdaExpression<>(condition),
                        DetectionSignal.DETECTED,
                        confidence,
                        null,
                        Map.of())),
                Map.of());
    }
}

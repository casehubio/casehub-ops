package io.casehub.ops.app.lifecycle.ras;

import io.casehub.ras.api.GanglionDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class OpsMonitoringSituationDefinitionProviderTest {

    private OpsMonitoringSituationDefinitionProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OpsMonitoringSituationDefinitionProvider();
    }

    @Test
    void registrationsIsEmpty() {
        assertTrue(provider.registrations().isEmpty());
    }

    @Test
    void ganglionDescriptorsReturns37() {
        assertEquals(37, provider.ganglionDescriptors().size());
    }

    @Test
    void allGanglionIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (var g : provider.ganglionDescriptors()) {
            assertTrue(ids.add(g.ganglionId()), "Duplicate ganglionId: " + g.ganglionId());
        }
    }

    @Test
    void allAreExpressionRules() {
        for (var g : provider.ganglionDescriptors()) {
            assertInstanceOf(GanglionDescriptor.ExpressionRules.class, g);
        }
    }

    @Test
    void allHaveAtLeastOneRule() {
        for (var g : provider.ganglionDescriptors()) {
            var rules = ((GanglionDescriptor.ExpressionRules) g);
            assertFalse(rules.rules().isEmpty(), g.ganglionId() + " has no rules");
        }
    }

    @Test
    void allHaveNonEmptyEventTypes() {
        for (var g : provider.ganglionDescriptors()) {
            assertFalse(g.handledEventTypes().isEmpty(), g.ganglionId() + " has no event types");
        }
    }

    @Test
    void healthGangliaPresent() {
        var ids = ganglionIds();
        assertTrue(ids.contains("heartbeat-check"));
        assertTrue(ids.contains("heartbeat-recovery"));
        assertTrue(ids.contains("metrics-trend"));
        assertTrue(ids.contains("metrics-recovery"));
        assertTrue(ids.contains("log-anomaly"));
        assertTrue(ids.contains("dependency-health"));
    }

    @Test
    void driftGangliaPresent() {
        var ids = ganglionIds();
        assertTrue(ids.contains("config-drift"));
        assertTrue(ids.contains("manual-change-detected"));
    }

    @Test
    void complianceGangliaPresent() {
        var ids = ganglionIds();
        assertTrue(ids.contains("evidence-stale"));
        assertTrue(ids.contains("control-violation"));
        assertTrue(ids.contains("framework-change"));
    }

    @Test
    void scalingGangliaPresent() {
        var ids = ganglionIds();
        assertTrue(ids.contains("cpu-threshold"));
        assertTrue(ids.contains("cpu-recovery"));
        assertTrue(ids.contains("memory-threshold"));
        assertTrue(ids.contains("memory-recovery"));
        assertTrue(ids.contains("queue-depth"));
        assertTrue(ids.contains("queue-recovery"));
        assertTrue(ids.contains("request-latency-trend"));
        assertTrue(ids.contains("cost-anomaly"));
    }

    @Test
    void changeGangliaPresent() {
        var ids = ganglionIds();
        assertTrue(ids.contains("version-check"));
        assertTrue(ids.contains("canary-health"));
        assertTrue(ids.contains("rollout-progress"));
    }

    @Test
    void securityGangliaPresent() {
        var ids = ganglionIds();
        assertTrue(ids.contains("cve-scanner"));
        assertTrue(ids.contains("anomaly-detector"));
        assertTrue(ids.contains("secret-rotation-due"));
        assertTrue(ids.contains("penetration-test-finding"));
    }

    @Test
    void maintenanceGangliaPresent() {
        var ids = ganglionIds();
        assertTrue(ids.contains("maintenance-due"));
        assertTrue(ids.contains("backup-verification"));
        assertTrue(ids.contains("dr-drill-due"));
        assertTrue(ids.contains("certificate-expiry"));
    }

    @Test
    void problemGangliaPresent() {
        var ids = ganglionIds();
        assertTrue(ids.contains("incident-pattern"));
        assertTrue(ids.contains("scaling-pattern"));
        assertTrue(ids.contains("drift-pattern"));
    }

    @Test
    void decommissionGangliaPresent() {
        var ids = ganglionIds();
        assertTrue(ids.contains("decommission-schedule"));
        assertTrue(ids.contains("dependency-check"));
        assertTrue(ids.contains("data-migration-progress"));
        assertTrue(ids.contains("traffic-monitor"));
    }

    @Test
    void eventTypesUseOpsNamespace() {
        for (var g : provider.ganglionDescriptors()) {
            for (String et : g.handledEventTypes()) {
                assertTrue(et.startsWith("io.casehub.ops."),
                    g.ganglionId() + " has non-ops event type: " + et);
            }
        }
    }

    @Test
    void rulesHaveLambdaWhenConditions() {
        for (var g : provider.ganglionDescriptors()) {
            var er = (GanglionDescriptor.ExpressionRules) g;
            for (var rule : er.rules()) {
                if (rule.when() != null) {
                    assertEquals("lambda", rule.when().type(),
                        g.ganglionId() + " rule has non-lambda when condition");
                }
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void heartbeatCheckDetectsOnFailStatus() {
        var heartbeat = findGanglion("heartbeat-check");
        var er = (GanglionDescriptor.ExpressionRules) heartbeat;
        var when = (Function<Map, Boolean>) ((io.casehub.platform.api.expression.LambdaExpression<Map, Boolean>) er.rules().getFirst().when())::eval;

        Map<String, Object> failCtx = Map.of("data", Map.of("status", "FAIL"));
        assertTrue(when.apply(failCtx));

        Map<String, Object> okCtx = Map.of("data", Map.of("status", "OK"));
        assertFalse(when.apply(okCtx));
    }

    @Test
    @SuppressWarnings("unchecked")
    void heartbeatRecoveryDetectsOnOkStatus() {
        var recovery = findGanglion("heartbeat-recovery");
        var er = (GanglionDescriptor.ExpressionRules) recovery;
        var when = (Function<Map, Boolean>) ((io.casehub.platform.api.expression.LambdaExpression<Map, Boolean>) er.rules().getFirst().when())::eval;

        Map<String, Object> okCtx = Map.of("data", Map.of("status", "OK"));
        assertTrue(when.apply(okCtx));

        Map<String, Object> failCtx = Map.of("data", Map.of("status", "FAIL"));
        assertFalse(when.apply(failCtx));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cpuThresholdDetectsHighAndLow() {
        var cpu = findGanglion("cpu-threshold");
        var er = (GanglionDescriptor.ExpressionRules) cpu;
        var when = (Function<Map, Boolean>) ((io.casehub.platform.api.expression.LambdaExpression<Map, Boolean>) er.rules().getFirst().when())::eval;

        assertTrue(when.apply(Map.of("data", Map.of("utilizationPct", 90))));
        assertTrue(when.apply(Map.of("data", Map.of("utilizationPct", 5))));
        assertFalse(when.apply(Map.of("data", Map.of("utilizationPct", 50))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nullDataReturnsFalse() {
        var heartbeat = findGanglion("heartbeat-check");
        var er = (GanglionDescriptor.ExpressionRules) heartbeat;
        var when = (Function<Map, Boolean>) ((io.casehub.platform.api.expression.LambdaExpression<Map, Boolean>) er.rules().getFirst().when())::eval;

        assertFalse(when.apply(Map.of("data", Map.of())));
        assertFalse(when.apply(Map.of()));
    }

    private GanglionDescriptor findGanglion(String id) {
        return provider.ganglionDescriptors().stream()
            .filter(g -> g.ganglionId().equals(id))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Ganglion not found: " + id));
    }

    private Set<String> ganglionIds() {
        Set<String> ids = new HashSet<>();
        provider.ganglionDescriptors().forEach(g -> ids.add(g.ganglionId()));
        return ids;
    }
}

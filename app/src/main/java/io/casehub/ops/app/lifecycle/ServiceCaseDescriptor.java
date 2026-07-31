package io.casehub.ops.app.lifecycle;

import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.SubCase;
import io.casehub.ops.api.lifecycle.DimensionType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ServiceCaseDescriptor {

    private static final Map<DimensionType, List<String>> DIMENSION_BINDINGS = buildDimensionBindings();

    private ServiceCaseDescriptor() {}

    public static CaseDefinition build() {
        var allBindings = new ArrayList<Binding>();
        allBindings.addAll(healthBindings());
        allBindings.addAll(driftBindings());
        allBindings.addAll(complianceBindings());
        allBindings.addAll(scalingBindings());
        allBindings.addAll(changeBindings());
        allBindings.addAll(securityBindings());
        allBindings.addAll(maintenanceBindings());
        allBindings.addAll(problemBindings());
        allBindings.addAll(decommissionBindings());

        return CaseDefinition.builder()
                .namespace("ops")
                .name("service-lifecycle")
                .version("1.0")
                .title("Service Lifecycle")
                .summary("Long-lived case managing a deployed service across nine operational dimensions")
                .bindings(allBindings)
                .build();
    }

    public static Map<DimensionType, List<String>> dimensionBindings() {
        return DIMENSION_BINDINGS;
    }

    private static List<Binding> healthBindings() {
        return List.of(
                binding("health:incident-response", ".health.serviceDown",
                        "ops", "incident-response", "1.0", ".health.incidentData"),
                binding("health:auto-restart", ".health.degraded",
                        "ops", "auto-restart", "1.0", ".health.degradedData"));
    }

    private static List<Binding> driftBindings() {
        return List.of(
                binding("drift:auto-reconciliation", ".drift.detected",
                        "ops", "drift-reconciliation", "1.0", ".drift.driftData"),
                binding("drift:manual-review", ".drift.chronicDrifter",
                        "ops", "drift-manual-review", "1.0", ".drift.driftData"));
    }

    private static List<Binding> complianceBindings() {
        return List.of(
                binding("compliance:evidence-recollection", ".compliance.evidenceStale",
                        "ops", "evidence-recollection", "1.0", ".compliance.staleData"),
                binding("compliance:remediation", ".compliance.controlViolation",
                        "ops", "compliance-remediation", "1.0", ".compliance.violationData"));
    }

    private static List<Binding> scalingBindings() {
        return List.of(
                binding("scaling:scale-out", ".scaling.thresholdBreached",
                        "ops", "scale-out", "1.0", ".scaling.thresholdData"));
    }

    private static List<Binding> changeBindings() {
        return List.of(
                binding("change:rolling-upgrade", ".change.upgradeRequested",
                        "ops", "rolling-upgrade", "1.0", ".change.upgradeSpec"));
    }

    private static List<Binding> securityBindings() {
        return List.of(
                binding("security:vulnerability-patch", ".security.vulnerabilityFound",
                        "ops", "vulnerability-patch", "1.0", ".security.cveData"),
                binding("security:breach-investigation", ".security.breachDetected",
                        "ops", "breach-investigation", "1.0", ".security.breachData"));
    }

    private static List<Binding> maintenanceBindings() {
        return List.of(
                binding("maintenance:scheduled", ".maintenance.windowDue",
                        "ops", "scheduled-maintenance", "1.0", ".maintenance.windowData"));
    }

    private static List<Binding> problemBindings() {
        return List.of(
                binding("problems:root-cause-investigation", ".problems.patternDetected",
                        "ops", "root-cause-investigation", "1.0", ".problems.patternData"));
    }

    private static List<Binding> decommissionBindings() {
        return List.of(
                binding("decommission:data-migration", ".decommission.migrationStarted",
                        "ops", "data-migration", "1.0", ".decommission.migrationSpec"),
                binding("decommission:resource-cleanup", ".decommission.cleanupReady",
                        "ops", "resource-cleanup", "1.0", ".decommission.cleanupData"));
    }

    private static Binding binding(String name, String triggerFilter,
                                    String childNs, String childName,
                                    String childVersion, String inputMapping) {
        return Binding.builder()
                .name(name)
                .on(new ContextChangeTrigger(triggerFilter))
                .subCase(SubCase.builder()
                        .namespace(childNs)
                        .name(childName)
                        .version(childVersion)
                        .inputMapping(inputMapping)
                        .waitForCompletion(false)
                        .build())
                .build();
    }

    private static Map<DimensionType, List<String>> buildDimensionBindings() {
        var map = new EnumMap<DimensionType, List<String>>(DimensionType.class);
        map.put(DimensionType.HEALTH_MONITORING, List.of("health:incident-response", "health:auto-restart"));
        map.put(DimensionType.CONFIGURATION_DRIFT, List.of("drift:auto-reconciliation", "drift:manual-review"));
        map.put(DimensionType.COMPLIANCE, List.of("compliance:evidence-recollection", "compliance:remediation"));
        map.put(DimensionType.SCALING, List.of("scaling:scale-out"));
        map.put(DimensionType.CHANGE_MANAGEMENT, List.of("change:rolling-upgrade"));
        map.put(DimensionType.SECURITY, List.of("security:vulnerability-patch", "security:breach-investigation"));
        map.put(DimensionType.MAINTENANCE, List.of("maintenance:scheduled"));
        map.put(DimensionType.PROBLEM_MANAGEMENT, List.of("problems:root-cause-investigation"));
        map.put(DimensionType.DECOMMISSION, List.of("decommission:data-migration", "decommission:resource-cleanup"));
        return Map.copyOf(map);
    }
}

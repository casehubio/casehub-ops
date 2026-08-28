package io.casehub.ops.compliance;

import io.casehub.desiredstate.api.*;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.desiredstate.runtime.TransitionPlanner;
import io.casehub.ops.api.approval.InMemoryPlanStore;
import io.casehub.ops.api.compliance.*;
import io.casehub.ops.testing.StubEvidenceCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceReconciliationIntegrationTest {

    private static final String TENANCY_ID = "tenant-1";

    private ComplianceGoalLoader goalLoader;
    private ComplianceGoalCompiler compiler;
    private ComplianceNodeProvisioner provisioner;
    private ComplianceActualStateAdapter adapter;
    private TransitionPlanner planner;
    private DefaultDesiredStateGraphFactory graphFactory;

    private ComplianceFrameworkRegistry registry;
    private final List<ComplianceLedgerEntry> ledgerEntries = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ledgerEntries.clear();

        var collectors = List.<EvidenceCollector>of(
                new StubEvidenceCollector("FILE_EXISTENCE"),
                new StubEvidenceCollector("LOG_DIRECTORY"),
                new StubEvidenceCollector("CERTIFICATE_EXPIRY"),
                new StubEvidenceCollector("CONFIG_HASH"));

        ComplianceEvidenceService.LedgerWriter writer =
                (entry, tenancyId) -> ledgerEntries.add(entry);
        ComplianceEvidenceService.LatestEvidenceFinder finder =
                (controlId, tenancyId) -> ledgerEntries.stream()
                        .filter(e -> controlId.equals(e.controlId))
                        .sorted(Comparator.comparing(
                                (ComplianceLedgerEntry e) -> e.occurredAt).reversed())
                        .limit(1)
                        .toList();

        var evidenceService = new ComplianceEvidenceService(collectors, writer, finder);
        registry = new ComplianceFrameworkRegistry();
        var specHashStore = new ComplianceSpecHashStore();

        provisioner = new ComplianceNodeProvisioner(
                evidenceService, registry, specHashStore,
                new ComplianceApprovalEvaluator(), new InMemoryPlanStore());
        adapter = new ComplianceActualStateAdapter(evidenceService, specHashStore);
        compiler = new ComplianceGoalCompiler();
        goalLoader = new ComplianceGoalLoader();
        planner = new TransitionPlanner();
        graphFactory = new DefaultDesiredStateGraphFactory();
    }

    @Test
    void greenField_yamlLoad_provisionAll_loopClosure() {
        var goals = goalLoader.load("test-compliance/all-controls.yaml");
        var desired = ((CompilationResult.SingleGraph) compiler.compile(goals, graphFactory)).graph();
        assertThat(desired.nodes()).hasSize(8);

        var actual = adapter.readActual(desired, TENANCY_ID);
        for (var status : actual.statuses().values()) {
            assertThat(status).isEqualTo(NodeStatus.ABSENT);
        }

        var plan = planner.plan(desired, actual);
        assertThat(plan.additions()).isNotEmpty();

        for (var step : plan.additions()) {
            if (step.action() == StepAction.PROVISION) {
                var result = provisioner.provision(step.node(), new ProvisionContext(TENANCY_ID, desired));
                assertThat(result).as("provisioning %s", step.node().id()).isInstanceOf(ProvisionResult.Success.class);
            }
        }

        var afterProvision = adapter.readActual(desired, TENANCY_ID);
        for (var entry : afterProvision.statuses().entrySet()) {
            assertThat(entry.getValue()).as("node %s", entry.getKey()).isEqualTo(NodeStatus.PRESENT);
        }

        var secondPlan = planner.plan(desired, afterProvision);
        assertThat(secondPlan.additions()).isEmpty();
        assertThat(secondPlan.removals()).isEmpty();
    }

    @Test
    void driftRemediation_modifySpec_recompile_remediate() {
        var goals = goalLoader.load("test-compliance/all-controls.yaml");
        var desired = ((CompilationResult.SingleGraph) compiler.compile(goals, graphFactory)).graph();
        provisionAll(desired);

        var modifiedGoals = withModifiedMaxAge(goals, "encryption-at-rest", 60);
        var modifiedDesired = ((CompilationResult.SingleGraph) compiler.compile(modifiedGoals, graphFactory)).graph();

        var driftActual = adapter.readActual(modifiedDesired, TENANCY_ID);
        assertThat(driftActual.statusOf(NodeId.of("encryption-at-rest"))).contains(NodeStatus.DRIFTED);

        var remediationPlan = planner.plan(modifiedDesired, driftActual);
        assertThat(remediationPlan.additions()).isNotEmpty();

        for (var step : remediationPlan.additions()) {
            if (step.action() == StepAction.PROVISION) {
                var result = provisioner.provision(step.node(), new ProvisionContext(TENANCY_ID, modifiedDesired));
                assertThat(result).isInstanceOf(ProvisionResult.Success.class);
            }
        }

        var afterRemediation = adapter.readActual(modifiedDesired, TENANCY_ID);
        for (var entry : afterRemediation.statuses().entrySet()) {
            assertThat(entry.getValue()).as("node %s after remediation", entry.getKey()).isEqualTo(NodeStatus.PRESENT);
        }

        var closurePlan = planner.plan(modifiedDesired, afterRemediation);
        assertThat(closurePlan.additions()).isEmpty();
        assertThat(closurePlan.removals()).isEmpty();
    }

    @Test
    void stableState_secondCycle_zeroTransitions() {
        var goals = goalLoader.load("test-compliance/all-controls.yaml");
        var desired = ((CompilationResult.SingleGraph) compiler.compile(goals, graphFactory)).graph();
        provisionAll(desired);

        var actual = adapter.readActual(desired, TENANCY_ID);
        for (var entry : actual.statuses().entrySet()) {
            assertThat(entry.getValue()).as("node %s", entry.getKey()).isEqualTo(NodeStatus.PRESENT);
        }

        var plan = planner.plan(desired, actual);
        assertThat(plan.isEmpty()).isTrue();
    }

    private void provisionAll(DesiredStateGraph desired) {
        var actual = adapter.readActual(desired, TENANCY_ID);
        var plan = planner.plan(desired, actual);
        for (var step : plan.additions()) {
            if (step.action() == StepAction.PROVISION) {
                provisioner.provision(step.node(), new ProvisionContext(TENANCY_ID, desired));
            }
        }
    }

    private ComplianceGoals withModifiedMaxAge(ComplianceGoals goals, String controlId, int newMaxAge) {
        var modifiedControls = goals.controls().stream().map(entry -> {
            var s = entry.spec();
            if (!controlId.equals(s.controlId())) return entry;
            var modified = new ComplianceControlSpec(
                    s.controlId(), s.controlType(), s.strategy(), s.title(), s.description(),
                    s.frameworks(), newMaxAge, s.requiresHumanReview(), s.properties());
            return new ComplianceGoalEntry(modified, entry.dependsOn());
        }).toList();
        return new ComplianceGoals(modifiedControls);
    }
}

package io.casehub.ops.deployment;

import io.casehub.desiredstate.api.*;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.desiredstate.runtime.TransitionPlanner;
import io.casehub.ops.api.deployment.*;
import io.casehub.ops.deployment.drift.CaseHubAppDriftChecker;
import io.casehub.ops.deployment.handler.CaseHubAppProvisionHandler;
import io.casehub.ops.testing.StubDeploymentTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class CaseHubAppReconciliationIntegrationTest {

    private StubDeploymentTarget deployTarget;
    private DeploymentGoalCompiler compiler;
    private DeploymentActualStateAdapter adapter;
    private SpecHashStore specHashStore;

    @BeforeEach
    void setUp() {
        deployTarget = new StubDeploymentTarget();
        compiler = new DeploymentGoalCompiler();
        specHashStore = new SpecHashStore();
        var driftChecker = new CaseHubAppDriftChecker(deployTarget);
        adapter = new DeploymentActualStateAdapter(List.of(driftChecker), specHashStore);
    }

    @Test
    void compileAndPlanApplicationNode() {
        var goals = new DeploymentGoals(
            List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(),
            List.of(new GoalEntry<>(
                new CaseHubApplicationSpec(
                    "incident-app", null, "ghcr.io/acme/incident:1.0",
                    new RuntimeConfig("0.2", List.of("a2a")),
                    Map.of(), List.of(), 1),
                List.of())),
            List.of());

        var factory = new DefaultDesiredStateGraphFactory();
        var graph = ((CompilationResult.SingleGraph) compiler.compile(goals, factory)).graph();

        assertThat(graph.nodes()).hasSize(1);
        assertThat(graph.nodes().values().iterator().next().spec())
            .isInstanceOf(CaseHubApplicationSpec.class);

        var actualState = adapter.readActual(graph, "tenant-1");
        assertThat(actualState.statuses().get(NodeId.of("incident-app")))
            .isEqualTo(NodeStatus.ABSENT);

        var planner = new TransitionPlanner();
        var plan = planner.plan(graph, actualState);
        assertThat(plan.flatAdditions()).hasSize(1);
        assertThat(plan.flatAdditions().get(0).node().id()).isEqualTo(NodeId.of("incident-app"));
    }

    @Test
    void applicationWithDependency() {
        var appEntry = new GoalEntry<>(
            new CaseHubApplicationSpec(
                "loan-app", null, "ghcr.io/acme/loan:1.0",
                new RuntimeConfig("0.2", List.of("mcp")),
                Map.of(), List.of(), 1),
            List.of("incident-app"));
        var incidentEntry = new GoalEntry<>(
            new CaseHubApplicationSpec(
                "incident-app", null, "ghcr.io/acme/incident:1.0",
                new RuntimeConfig("0.2", List.of("a2a")),
                Map.of(), List.of(), 1),
            List.of());

        var goals = new DeploymentGoals(
            List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(),
            List.of(incidentEntry, appEntry),
            List.of());

        var factory = new DefaultDesiredStateGraphFactory();
        var graph = ((CompilationResult.SingleGraph) compiler.compile(goals, factory)).graph();

        assertThat(graph.nodes()).hasSize(2);
        assertThat(graph.dependencies()).hasSize(1);
        var dep = graph.dependencies().iterator().next();
        assertThat(dep.from()).isEqualTo(NodeId.of("loan-app"));
        assertThat(dep.to()).isEqualTo(NodeId.of("incident-app"));
    }

    @Test
    void provisionAndVerifyActualState() {
        var spec = new CaseHubApplicationSpec(
            "incident-app", null, "ghcr.io/acme/incident:1.0",
            new RuntimeConfig("0.2", List.of("a2a")),
            Map.of(), List.of(), 1);
        var goals = new DeploymentGoals(
            List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(),
            List.of(new GoalEntry<>(spec, List.of())),
            List.of());

        var factory = new DefaultDesiredStateGraphFactory();
        var graph = ((CompilationResult.SingleGraph) compiler.compile(goals, factory)).graph();

        var handler = new CaseHubAppProvisionHandler(
            new PrebuiltImagePipeline(), deployTarget);
        var provResult = handler.provision(spec,
            new ProvisionContext("tenant-1", graph));
        assertThat(provResult).isInstanceOf(ProvisionResult.Success.class);

        specHashStore.record(NodeId.of("incident-app"), spec);
        var actualState = adapter.readActual(graph, "tenant-1");
        assertThat(actualState.statuses().get(NodeId.of("incident-app")))
            .isEqualTo(NodeStatus.PRESENT);

        var planner = new TransitionPlanner();
        var plan = planner.plan(graph, actualState);
        assertThat(plan.flatAdditions()).isEmpty();
        assertThat(plan.flatRemovals()).isEmpty();
    }
}

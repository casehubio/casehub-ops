package io.casehub.ops.claudony;

import io.casehub.desiredstate.api.DesiredStateGraphFactory;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyGoalEntry;
import io.casehub.ops.api.claudony.ClaudonyGoals;
import io.casehub.ops.api.claudony.ClaudonyPoolNodeSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudonyGoalCompilerTest {

    private final ClaudonyGoalCompiler compiler = new ClaudonyGoalCompiler();
    private final DesiredStateGraphFactory factory = new DefaultDesiredStateGraphFactory();

    @Test
    void compiles_apps_into_desiredNodes() {
        var goals = new ClaudonyGoals(
            List.of(new ClaudonyGoalEntry<>(
                new ClaudonyAppNodeSpec("app-a", "App A", null, "img:1", Map.of()), List.of())),
            List.of());

        var result = compiler.compile(goals, factory);
        var graph = ((io.casehub.desiredstate.api.CompilationResult.SingleGraph) result).graph();

        assertThat(graph.nodes()).hasSize(1);
        assertThat(graph.nodes().get(NodeId.of("claudony_app:app-a"))).isNotNull();
        assertThat(graph.nodes().get(NodeId.of("claudony_app:app-a")).spec())
            .isInstanceOf(ClaudonyAppNodeSpec.class);
    }

    @Test
    void compiles_pools_with_dependency_on_parent_app() {
        var goals = new ClaudonyGoals(
            List.of(new ClaudonyGoalEntry<>(
                new ClaudonyAppNodeSpec("app-a", "App A", null, "img:1", Map.of()), List.of())),
            List.of(new ClaudonyGoalEntry<>(
                new ClaudonyPoolNodeSpec("app-a", "agent-1", 1, 3), List.of())));

        var result = compiler.compile(goals, factory);
        var graph = ((io.casehub.desiredstate.api.CompilationResult.SingleGraph) result).graph();

        assertThat(graph.nodes()).hasSize(2);
        var poolId = NodeId.of("claudony_pool:app-a:agent-1");
        var appId = NodeId.of("claudony_app:app-a");
        assertThat(graph.dependenciesOf(poolId)).contains(appId);
    }

    @Test
    void honours_explicit_dependsOn_edges() {
        var goals = new ClaudonyGoals(
            List.of(
                new ClaudonyGoalEntry<>(
                    new ClaudonyAppNodeSpec("app-a", "A", null, "img", Map.of()), List.of()),
                new ClaudonyGoalEntry<>(
                    new ClaudonyAppNodeSpec("app-b", "B", null, "img", Map.of()),
                    List.of("claudony_app:app-a"))),
            List.of());

        var result = compiler.compile(goals, factory);
        var graph = ((io.casehub.desiredstate.api.CompilationResult.SingleGraph) result).graph();

        var bId = NodeId.of("claudony_app:app-b");
        var aId = NodeId.of("claudony_app:app-a");
        assertThat(graph.dependenciesOf(bId)).contains(aId);
    }

    @Test
    void empty_goals_produce_empty_graph() {
        var goals = new ClaudonyGoals(List.of(), List.of());
        var result = compiler.compile(goals, factory);
        assertThat(((io.casehub.desiredstate.api.CompilationResult.SingleGraph) result).graph().nodes()).isEmpty();
    }

    @Test
    void multiple_pools_for_same_app() {
        var goals = new ClaudonyGoals(
            List.of(new ClaudonyGoalEntry<>(
                new ClaudonyAppNodeSpec("app-a", "A", null, "img", Map.of()), List.of())),
            List.of(
                new ClaudonyGoalEntry<>(new ClaudonyPoolNodeSpec("app-a", "agent-1", 1, 3), List.of()),
                new ClaudonyGoalEntry<>(new ClaudonyPoolNodeSpec("app-a", "agent-2", 2, 5), List.of())));

        var result = compiler.compile(goals, factory);
        var graph = ((io.casehub.desiredstate.api.CompilationResult.SingleGraph) result).graph();

        assertThat(graph.nodes()).hasSize(3);
        var appId = NodeId.of("claudony_app:app-a");
        assertThat(graph.dependenciesOf(NodeId.of("claudony_pool:app-a:agent-1"))).contains(appId);
        assertThat(graph.dependenciesOf(NodeId.of("claudony_pool:app-a:agent-2"))).contains(appId);
    }
}

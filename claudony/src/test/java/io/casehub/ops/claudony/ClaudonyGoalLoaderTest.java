package io.casehub.ops.claudony;

import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyPoolNodeSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudonyGoalLoaderTest {

    private final ClaudonyGoalLoader loader = new ClaudonyGoalLoader();

    @Test
    void loads_goals_from_classpath() {
        var goals = loader.load("claudony-fleet-test.yaml");

        assertThat(goals.apps()).hasSize(1);
        assertThat(goals.apps().getFirst().spec()).isInstanceOf(ClaudonyAppNodeSpec.class);
        assertThat(goals.apps().getFirst().spec().name()).isEqualTo("test-app");
        assertThat(goals.apps().getFirst().spec().image()).isEqualTo("test:1.0");
        assertThat(goals.apps().getFirst().spec().config()).containsEntry("key1", "value1");

        assertThat(goals.pools()).hasSize(1);
        assertThat(goals.pools().getFirst().spec()).isInstanceOf(ClaudonyPoolNodeSpec.class);
        assertThat(goals.pools().getFirst().spec().appName()).isEqualTo("test-app");
        assertThat(goals.pools().getFirst().spec().agentId()).isEqualTo("agent-1");
        assertThat(goals.pools().getFirst().spec().minActive()).isEqualTo(1);
        assertThat(goals.pools().getFirst().spec().maxActive()).isEqualTo(3);
    }

    @Test
    void nonexistent_file_returns_empty_goals() {
        var goals = loader.load("nonexistent-file-that-does-not-exist.yaml");
        assertThat(goals.apps()).isEmpty();
        assertThat(goals.pools()).isEmpty();
    }

    @Test
    void goals_dependsOn_defaults_to_empty() {
        var goals = loader.load("claudony-fleet-test.yaml");
        assertThat(goals.apps().getFirst().dependsOn()).isEmpty();
        assertThat(goals.pools().getFirst().dependsOn()).isEmpty();
    }
}

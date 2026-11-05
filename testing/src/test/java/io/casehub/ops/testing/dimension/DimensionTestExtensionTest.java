package io.casehub.ops.testing.dimension;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DimensionTestExtensionTest {

    @Test
    void multipleContainerSpecs_populateContainerNamesInState() {
        DimensionState state = new DimensionState();
        List<String> names = List.of("fleet-analyst", "fleet-coder", "fleet-assistant");
        state.put("containerNames", names);
        state.put("containerName", names.getFirst());

        assertThat(state.get("containerNames", List.class))
                .containsExactly("fleet-analyst", "fleet-coder", "fleet-assistant");
        assertThat(state.getString("containerName"))
                .isEqualTo("fleet-analyst");
    }
}

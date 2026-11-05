package io.casehub.ops.testing.dimension;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FleetDimensionTest {

    @Test
    void name_isFleet() {
        FleetDimension dimension = new FleetDimension();
        assertThat(dimension.name()).isEqualTo("fleet");
    }

    @Test
    void requires_deploy() {
        FleetDimension dimension = new FleetDimension();
        assertThat(dimension.requires()).containsExactly("deploy");
    }
}

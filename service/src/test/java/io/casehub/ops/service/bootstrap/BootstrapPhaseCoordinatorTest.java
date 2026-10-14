package io.casehub.ops.service.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class BootstrapPhaseCoordinatorTest {

    @Test
    void startsInSeedPhase() {
        var coordinator = new BootstrapPhaseCoordinator();
        assertThat(coordinator.currentPhase()).isEqualTo(BootstrapPhase.SEED);
    }

    @Test
    void transitionsToSelfAwareWhenAllDependenciesHealthy() {
        var coordinator = new BootstrapPhaseCoordinator();
        coordinator.markDependencyHealthy("qhorus");
        coordinator.markDependencyHealthy("eidos");
        coordinator.markDependencyHealthy("engine");

        coordinator.evaluatePhaseTransition(Set.of("qhorus", "eidos", "engine"));
        assertThat(coordinator.currentPhase()).isEqualTo(BootstrapPhase.SELF_AWARE);
    }

    @Test
    void doesNotTransitionWhenDependenciesMissing() {
        var coordinator = new BootstrapPhaseCoordinator();
        coordinator.markDependencyHealthy("qhorus");

        coordinator.evaluatePhaseTransition(Set.of("qhorus", "eidos", "engine"));
        assertThat(coordinator.currentPhase()).isEqualTo(BootstrapPhase.SEED);
    }

    @Test
    void transitionIsIdempotent() {
        var coordinator = new BootstrapPhaseCoordinator();
        coordinator.markDependencyHealthy("qhorus");
        coordinator.markDependencyHealthy("eidos");
        coordinator.markDependencyHealthy("engine");

        coordinator.evaluatePhaseTransition(Set.of("qhorus", "eidos", "engine"));
        coordinator.evaluatePhaseTransition(Set.of("qhorus", "eidos", "engine"));
        assertThat(coordinator.currentPhase()).isEqualTo(BootstrapPhase.SELF_AWARE);
    }

    @Test
    void markUnhealthyPreventsTransition() {
        var coordinator = new BootstrapPhaseCoordinator();
        coordinator.markDependencyHealthy("qhorus");
        coordinator.markDependencyHealthy("eidos");
        coordinator.markDependencyHealthy("engine");
        coordinator.markDependencyUnhealthy("eidos");

        coordinator.evaluatePhaseTransition(Set.of("qhorus", "eidos", "engine"));
        assertThat(coordinator.currentPhase()).isEqualTo(BootstrapPhase.SEED);
    }
}

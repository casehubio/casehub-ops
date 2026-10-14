package io.casehub.ops.service.bootstrap;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class BootstrapPhaseCoordinator {

    private volatile BootstrapPhase phase = BootstrapPhase.SEED;
    private final Set<String> healthyDependencies = ConcurrentHashMap.newKeySet();

    public BootstrapPhase currentPhase() {
        return phase;
    }

    public void markDependencyHealthy(String applicationId) {
        healthyDependencies.add(applicationId);
    }

    public void markDependencyUnhealthy(String applicationId) {
        healthyDependencies.remove(applicationId);
    }

    public void evaluatePhaseTransition(Set<String> requiredDependencies) {
        if (phase == BootstrapPhase.SELF_AWARE) return;
        if (healthyDependencies.containsAll(requiredDependencies)) {
            phase = BootstrapPhase.SELF_AWARE;
        }
    }
}

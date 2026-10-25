package io.casehub.ops.topology.integration;

import io.casehub.ras.api.ActiveSituation;

import java.util.List;
import java.util.Optional;

@FunctionalInterface
interface DeploymentDecisionFunction {
    Optional<String> decide(ActiveSituation situation, List<SpecialistProfile> catalog);
}

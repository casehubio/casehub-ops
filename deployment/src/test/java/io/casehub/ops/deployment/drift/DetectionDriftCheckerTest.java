package io.casehub.ops.deployment.drift;

import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.DetectionNodeSpec;
import io.casehub.ops.api.deployment.TrustPolicyNodeSpec;
import io.casehub.ras.api.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class DetectionDriftCheckerTest {

    @Test
    void nodeTypeIsDetection() {
        var checker = new DetectionDriftChecker(stubRegistrar(false));
        assertThat(checker.nodeType()).isEqualTo("detection");
    }

    @Test
    void returnsPresentWhenSituationExists() {
        var checker = new DetectionDriftChecker(stubRegistrar(true));
        var spec = testSpec("test.situation");
        assertThat(checker.check(spec, "tenant-1")).isEqualTo(NodeStatus.PRESENT);
    }

    @Test
    void returnsAbsentWhenSituationDoesNotExist() {
        var checker = new DetectionDriftChecker(stubRegistrar(false));
        var spec = testSpec("test.situation");
        assertThat(checker.check(spec, "tenant-1")).isEqualTo(NodeStatus.ABSENT);
    }

    @Test
    void returnsUnknownForNonDetectionSpec() {
        var checker = new DetectionDriftChecker(stubRegistrar(true));
        NodeSpec otherSpec = new TrustPolicyNodeSpec(
                "policy", 0.5, 3, 0.1, 0.2, null, false);
        assertThat(checker.check(otherSpec, "tenant-1")).isEqualTo(NodeStatus.UNKNOWN);
    }

    private SituationRegistrar stubRegistrar(boolean exists) {
        return new SituationRegistrar() {
            @Override public void register(SituationRegistration r) {}
            @Override public void deregister(String id) {}
            @Override public boolean exists(String id) { return exists; }
        };
    }

    private DetectionNodeSpec testSpec(String situationId) {
        return new DetectionNodeSpec(
                situationId,
                Set.of("test.event"),
                Duration.ofMinutes(5), null,
                new ChainMode.Streak("g1", 3),
                new TriggerAction.NotifyOnly(),
                new TriggerMode.FireOnce());
    }
}

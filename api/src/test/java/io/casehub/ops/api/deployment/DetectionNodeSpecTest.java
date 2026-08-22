package io.casehub.ops.api.deployment;

import io.casehub.desiredstate.api.NodeType;
import io.casehub.ras.api.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class DetectionNodeSpecTest {

    @Test
    void nodeIdReturnsSituationId() {
        var spec = testSpec("app.failure-detection");
        assertThat(spec.nodeId()).isEqualTo("app.failure-detection");
    }

    @Test
    void nodeTypeReturnsDetection() {
        var spec = testSpec("app.failure-detection");
        assertThat(spec.nodeType()).isEqualTo(NodeType.of("detection"));
    }

    @Test
    void toRegistrationPreservesAllFields() {
        var spec = testSpec("app.failure-detection");
        SituationRegistration reg = spec.toRegistration();

        SituationDefinition def = reg.definition();
        assertThat(def.situationId()).isEqualTo("app.failure-detection");
        assertThat(def.eventTypes()).containsExactlyInAnyOrder(
                "desiredstate.node.faulted", "desiredstate.node.recovered");
        assertThat(def.correlationWindow()).isEqualTo(Duration.ofMinutes(10));
        assertThat(def.chainMode()).isInstanceOf(ChainMode.Streak.class);
        assertThat(((ChainMode.Streak) def.chainMode()).ganglionId())
                .isEqualTo("node-fault");
        assertThat(((ChainMode.Streak) def.chainMode()).requiredCount())
                .isEqualTo(3);
        assertThat(def.triggerAction()).isInstanceOf(TriggerAction.CreateCase.class);
    }

    @Test
    void nullSituationIdThrows() {
        assertThatThrownBy(() -> new DetectionNodeSpec(
                null,
                Set.of("test.event"),
                Duration.ofMinutes(5),
                null,
                new ChainMode.Streak("g1", 3),
                new TriggerAction.NotifyOnly(),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankSituationIdThrows() {
        assertThatThrownBy(() -> new DetectionNodeSpec(
                "  ",
                Set.of("test.event"),
                Duration.ofMinutes(5),
                null,
                new ChainMode.Streak("g1", 3),
                new TriggerAction.NotifyOnly(),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyEventTypesThrows() {
        assertThatThrownBy(() -> new DetectionNodeSpec(
                "test",
                Set.of(),
                Duration.ofMinutes(5),
                null,
                new ChainMode.Streak("g1", 3),
                new TriggerAction.NotifyOnly(),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullChainModeThrows() {
        assertThatThrownBy(() -> new DetectionNodeSpec(
                "test",
                Set.of("test.event"),
                Duration.ofMinutes(5),
                null,
                null,
                new TriggerAction.NotifyOnly(),
                null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullTriggerActionThrows() {
        assertThatThrownBy(() -> new DetectionNodeSpec(
                "test",
                Set.of("test.event"),
                Duration.ofMinutes(5),
                null,
                new ChainMode.Streak("g1", 3),
                null,
                null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullTriggerModeDefaultsToFireOnce() {
        var spec = new DetectionNodeSpec(
                "test",
                Set.of("test.event"),
                Duration.ofMinutes(5),
                null,
                new ChainMode.Streak("g1", 3),
                new TriggerAction.NotifyOnly(),
                null
        );
        SituationRegistration reg = spec.toRegistration();
        assertThat(reg.definition().triggerMode())
                .isInstanceOf(TriggerMode.FireOnce.class);
    }

    @Test
    void jsonRoundTrip() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        var spec = testSpec("app.failure-detection");
        String json = mapper.writeValueAsString(spec);
        DetectionNodeSpec deserialized = mapper.readValue(json, DetectionNodeSpec.class);
        assertThat(deserialized).isEqualTo(spec);
    }

    @Test
    void valueSemantics() {
        var spec1 = testSpec("app.failure-detection");
        var spec2 = testSpec("app.failure-detection");
        assertThat(spec1).isEqualTo(spec2);
        assertThat(spec1.hashCode()).isEqualTo(spec2.hashCode());
    }

    public static DetectionNodeSpec testSpec(String situationId) {
        return new DetectionNodeSpec(
                situationId,
                Set.of("desiredstate.node.faulted", "desiredstate.node.recovered"),
                Duration.ofMinutes(10),
                null,
                new ChainMode.Streak("node-fault", 3),
                new TriggerAction.CreateCase(
                        new CaseTriggerConfig("ops", "incident-response", "1.0", Map.of())),
                new TriggerMode.FireOnce()
        );
    }
}

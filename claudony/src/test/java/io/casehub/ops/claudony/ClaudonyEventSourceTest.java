package io.casehub.ops.claudony;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.StateEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudonyEventSourceTest {

    @Test
    void emit_delivers_to_subscriber() {
        var source = new ClaudonyEventSource();
        var future = source.stream()
            .select().first(1)
            .collect().asList()
            .subscribeAsCompletionStage();

        var event = new StateEvent(NodeId.of("claudony_app:x"), NodeStatus.DRIFTED, "drift");
        source.emit(event);

        assertThat(future).succeedsWithin(Duration.ofSeconds(2))
            .asList().containsExactly(event);
    }

    @Test
    void emitDrift_delivers_drift_event() {
        var source = new ClaudonyEventSource();
        var future = source.stream()
            .select().first(1)
            .collect().asList()
            .subscribeAsCompletionStage();

        source.emitDrift(NodeId.of("claudony_app:x"));

        assertThat(future).succeedsWithin(Duration.ofSeconds(2))
            .asList().singleElement().satisfies(e -> {
                var event = (StateEvent) e;
                assertThat(event.node()).isEqualTo(NodeId.of("claudony_app:x"));
                assertThat(event.newStatus()).isEqualTo(NodeStatus.DRIFTED);
            });
    }
}

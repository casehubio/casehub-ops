package io.casehub.ops.app.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationEventBroadcasterTest {

    private static ApplicationEventBroadcaster broadcaster(int capacity) {
        return new ApplicationEventBroadcaster(capacity);
    }

    @Test
    void publishStoresInRingBuffer() {
        var broadcaster = broadcaster(4);
        UUID appId = UUID.randomUUID();

        broadcaster.publish(appId, "io.casehub.ops.case.started", "evt-1", "{\"status\":\"started\"}");

        assertThat(broadcaster.bufferSize(appId)).isEqualTo(1);
    }

    @Test
    void subscribeAllReceivesAllEvents() {
        var broadcaster = new ApplicationEventBroadcaster(100);
        UUID appId = UUID.randomUUID();
        var received = new ArrayList<ApplicationEventBroadcaster.BroadcastEvent>();

        broadcaster.subscribe(appId, ApplicationEventBroadcaster.EventFilter.ALL, received::add);
        broadcaster.publish(appId, "io.casehub.ops.case.started", "evt-1", "{}");
        broadcaster.publish(appId, "io.casehub.desiredstate.reconciliation.completed", "evt-2", "{}");

        assertThat(received).hasSize(2);
    }

    @Test
    void subscribeCaseReceivesOnlyCaseEvents() {
        var broadcaster = new ApplicationEventBroadcaster(100);
        UUID appId = UUID.randomUUID();
        var received = new ArrayList<ApplicationEventBroadcaster.BroadcastEvent>();

        broadcaster.subscribe(appId, ApplicationEventBroadcaster.EventFilter.CASE, received::add);
        broadcaster.publish(appId, "io.casehub.ops.app.case.started", "evt-1", "{}");
        broadcaster.publish(appId, "io.casehub.desiredstate.reconciliation.completed", "evt-2", "{}");
        broadcaster.publish(appId, "io.casehub.ops.app.status.changed", "evt-3", "{}");

        assertThat(received).hasSize(1);
        assertThat(received.get(0).eventType()).isEqualTo("io.casehub.ops.app.case.started");
    }

    @Test
    void subscribeReconciliationReceivesOnlyReconciliationEvents() {
        var broadcaster = new ApplicationEventBroadcaster(100);
        UUID appId = UUID.randomUUID();
        var received = new ArrayList<ApplicationEventBroadcaster.BroadcastEvent>();

        broadcaster.subscribe(appId, ApplicationEventBroadcaster.EventFilter.RECONCILIATION, received::add);
        broadcaster.publish(appId, "io.casehub.ops.app.case.started", "evt-1", "{}");
        broadcaster.publish(appId, "io.casehub.desiredstate.reconciliation.completed", "evt-2", "{}");

        assertThat(received).hasSize(1);
        assertThat(received.get(0).eventType()).isEqualTo("io.casehub.desiredstate.reconciliation.completed");
    }

    @Test
    void ringBufferEvictsAtCapacity() {
        var broadcaster = new ApplicationEventBroadcaster(3);
        UUID appId = UUID.randomUUID();

        broadcaster.publish(appId, "type", "evt-1", "{}");
        broadcaster.publish(appId, "type", "evt-2", "{}");
        broadcaster.publish(appId, "type", "evt-3", "{}");
        broadcaster.publish(appId, "type", "evt-4", "{}");

        assertThat(broadcaster.bufferSize(appId)).isEqualTo(3);
    }

    @Test
    void replayFromLastEventIdReplaysBuffered() {
        var broadcaster = new ApplicationEventBroadcaster(100);
        UUID appId = UUID.randomUUID();

        broadcaster.publish(appId, "type", "evt-1", "{}");
        broadcaster.publish(appId, "type", "evt-2", "{}");
        broadcaster.publish(appId, "type", "evt-3", "{}");

        var replayed = new ArrayList<ApplicationEventBroadcaster.BroadcastEvent>();
        var result = broadcaster.replayAndSubscribe(appId, "evt-1",
                ApplicationEventBroadcaster.EventFilter.ALL, replayed::add);

        assertThat(replayed).hasSize(2);
        assertThat(replayed.get(0).eventId()).isEqualTo("evt-2");
        assertThat(replayed.get(1).eventId()).isEqualTo("evt-3");
        assertThat(result.gapDetected()).isFalse();
    }

    @Test
    void replayDetectsGapWhenLastEventIdNotInBuffer() {
        var broadcaster = new ApplicationEventBroadcaster(2);
        UUID appId = UUID.randomUUID();

        broadcaster.publish(appId, "type", "evt-1", "{}");
        broadcaster.publish(appId, "type", "evt-2", "{}");
        broadcaster.publish(appId, "type", "evt-3", "{}");

        var replayed = new ArrayList<ApplicationEventBroadcaster.BroadcastEvent>();
        var result = broadcaster.replayAndSubscribe(appId, "evt-1",
                ApplicationEventBroadcaster.EventFilter.ALL, replayed::add);

        assertThat(result.gapDetected()).isTrue();
        assertThat(replayed).hasSize(2);
    }

    @Test
    void unsubscribeStopsDelivery() {
        var broadcaster = new ApplicationEventBroadcaster(100);
        UUID appId = UUID.randomUUID();
        var received = new ArrayList<ApplicationEventBroadcaster.BroadcastEvent>();

        var handle = broadcaster.subscribe(appId, ApplicationEventBroadcaster.EventFilter.ALL, received::add);
        broadcaster.publish(appId, "type", "evt-1", "{}");
        broadcaster.unsubscribe(handle);
        broadcaster.publish(appId, "type", "evt-2", "{}");

        assertThat(received).hasSize(1);
    }

    @Test
    void publishToOneAppDoesNotAffectAnother() {
        var broadcaster = new ApplicationEventBroadcaster(100);
        UUID app1 = UUID.randomUUID();
        UUID app2 = UUID.randomUUID();
        var received1 = new ArrayList<ApplicationEventBroadcaster.BroadcastEvent>();
        var received2 = new ArrayList<ApplicationEventBroadcaster.BroadcastEvent>();

        broadcaster.subscribe(app1, ApplicationEventBroadcaster.EventFilter.ALL, received1::add);
        broadcaster.subscribe(app2, ApplicationEventBroadcaster.EventFilter.ALL, received2::add);
        broadcaster.publish(app1, "type", "evt-1", "{}");

        assertThat(received1).hasSize(1);
        assertThat(received2).isEmpty();
    }
}

package io.casehub.ops.service.container;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.StateEvent;
import io.casehub.ops.container.ContainerEventSource;
import io.casehub.ops.container.podman.PodmanClient;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.MultiEmitter;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class PodmanWatchManagerTest {

    private TestPodmanClient podmanClient;
    private RecordingEventSource eventSource;
    private PodmanWatchManager watchManager;

    @BeforeEach
    void setUp() {
        podmanClient = new TestPodmanClient();
        eventSource = new RecordingEventSource();
        watchManager = new PodmanWatchManager(podmanClient, eventSource);
    }

    @AfterEach
    void tearDown() {
        watchManager.shutdown();
    }

    @Test
    void startWatchingIsIdempotent() {
        watchManager.startWatching();
        watchManager.startWatching();
        assertThat(watchManager.isWatching()).isTrue();
    }

    @Test
    void stopWatchingCancelsSubscription() {
        watchManager.startWatching();
        assertThat(watchManager.isWatching()).isTrue();
        watchManager.stopWatching();
        assertThat(watchManager.isWatching()).isFalse();
    }

    @Test
    void shutdownStopsWatching() {
        watchManager.startWatching();
        watchManager.shutdown();
        assertThat(watchManager.isWatching()).isFalse();
    }

    @Test
    void stopWatchingWhenNotWatchingIsNoOp() {
        watchManager.stopWatching();
        assertThat(watchManager.isWatching()).isFalse();
    }

    @Test
    void translatesStartToPresent() throws Exception {
        watchManager.startWatching();
        podmanClient.emitEvent(containerEvent("start", "myapp"));
        Thread.sleep(100);

        assertThat(eventSource.events).anySatisfy(e -> {
            assertThat(e.node()).isEqualTo(NodeId.of("myapp"));
            assertThat(e.newStatus()).isEqualTo(NodeStatus.PRESENT);
        });
    }

    @Test
    void translatesDieToDrifted() throws Exception {
        watchManager.startWatching();
        podmanClient.emitEvent(containerEvent("die", "myapp-db"));
        Thread.sleep(100);

        assertThat(eventSource.events).anySatisfy(e -> {
            assertThat(e.node()).isEqualTo(NodeId.of("myapp-db"));
            assertThat(e.newStatus()).isEqualTo(NodeStatus.DRIFTED);
        });
    }

    @Test
    void translatesStopToDrifted() throws Exception {
        watchManager.startWatching();
        podmanClient.emitEvent(containerEvent("stop", "myapp"));
        Thread.sleep(100);

        assertThat(eventSource.events).anySatisfy(e -> {
            assertThat(e.node()).isEqualTo(NodeId.of("myapp"));
            assertThat(e.newStatus()).isEqualTo(NodeStatus.DRIFTED);
        });
    }

    @Test
    void translatesRemoveToAbsent() throws Exception {
        watchManager.startWatching();
        podmanClient.emitEvent(containerEvent("remove", "myapp"));
        Thread.sleep(100);

        assertThat(eventSource.events).anySatisfy(e -> {
            assertThat(e.node()).isEqualTo(NodeId.of("myapp"));
            assertThat(e.newStatus()).isEqualTo(NodeStatus.ABSENT);
        });
    }

    @Test
    void translatesHealthStatusHealthyToPresent() throws Exception {
        watchManager.startWatching();
        podmanClient.emitEvent(healthEvent("myapp", "healthy"));
        Thread.sleep(100);

        assertThat(eventSource.events).anySatisfy(e -> {
            assertThat(e.node()).isEqualTo(NodeId.of("myapp"));
            assertThat(e.newStatus()).isEqualTo(NodeStatus.PRESENT);
        });
    }

    @Test
    void translatesHealthStatusUnhealthyToDrifted() throws Exception {
        watchManager.startWatching();
        podmanClient.emitEvent(healthEvent("myapp", "unhealthy"));
        Thread.sleep(100);

        assertThat(eventSource.events).anySatisfy(e -> {
            assertThat(e.node()).isEqualTo(NodeId.of("myapp"));
            assertThat(e.newStatus()).isEqualTo(NodeStatus.DRIFTED);
        });
    }

    @Test
    void ignoresUnrecognizedActions() throws Exception {
        watchManager.startWatching();
        podmanClient.emitEvent(containerEvent("create", "myapp"));
        podmanClient.emitEvent(containerEvent("attach", "myapp"));
        Thread.sleep(100);

        assertThat(eventSource.events).isEmpty();
    }

    @Test
    void ignoresEventsWithoutActor() throws Exception {
        watchManager.startWatching();
        podmanClient.emitEvent(new JsonObject().put("Action", "start"));
        Thread.sleep(100);

        assertThat(eventSource.events).isEmpty();
    }

    @Test
    void ignoresEventsWithoutContainerName() throws Exception {
        watchManager.startWatching();
        podmanClient.emitEvent(new JsonObject()
            .put("Action", "start")
            .put("Actor", new JsonObject().put("Attributes", new JsonObject())));
        Thread.sleep(100);

        assertThat(eventSource.events).isEmpty();
    }

    @Test
    void mapActionCoversAllCases() {
        assertThat(PodmanWatchManager.mapAction("start", null)).isEqualTo(NodeStatus.PRESENT);
        assertThat(PodmanWatchManager.mapAction("die", null)).isEqualTo(NodeStatus.DRIFTED);
        assertThat(PodmanWatchManager.mapAction("stop", null)).isEqualTo(NodeStatus.DRIFTED);
        assertThat(PodmanWatchManager.mapAction("remove", null)).isEqualTo(NodeStatus.ABSENT);
        assertThat(PodmanWatchManager.mapAction("create", null)).isNull();
        assertThat(PodmanWatchManager.mapAction(null, null)).isNull();

        var healthy = new JsonObject().put("healthstatus", "healthy");
        assertThat(PodmanWatchManager.mapAction("health_status", healthy)).isEqualTo(NodeStatus.PRESENT);

        var unhealthy = new JsonObject().put("healthstatus", "unhealthy");
        assertThat(PodmanWatchManager.mapAction("health_status", unhealthy)).isEqualTo(NodeStatus.DRIFTED);
    }

    private static JsonObject containerEvent(String action, String name) {
        return new JsonObject()
            .put("Type", "container")
            .put("Action", action)
            .put("Actor", new JsonObject()
                .put("Attributes", new JsonObject().put("name", name)));
    }

    private static JsonObject healthEvent(String name, String healthStatus) {
        return new JsonObject()
            .put("Type", "container")
            .put("Action", "health_status")
            .put("Actor", new JsonObject()
                .put("Attributes", new JsonObject()
                    .put("name", name)
                    .put("healthstatus", healthStatus)));
    }

    static class TestPodmanClient extends PodmanClient {
        private volatile MultiEmitter<? super JsonObject> eventEmitter;
        private final Multi<JsonObject> eventStream = Multi.createFrom()
            .<JsonObject>emitter(e -> this.eventEmitter = e, BackPressureStrategy.BUFFER)
            .broadcast().toAllSubscribers();

        TestPodmanClient() {
            super(null, null);
        }

        @Override
        public Multi<JsonObject> events(String type) {
            return eventStream;
        }

        void emitEvent(JsonObject event) {
            var e = this.eventEmitter;
            if (e != null) e.emit(event);
        }
    }

    static class RecordingEventSource extends ContainerEventSource {
        final CopyOnWriteArrayList<StateEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void emit(StateEvent event) {
            events.add(event);
            super.emit(event);
        }
    }
}

package io.casehub.ops.app.container;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.StateEvent;
import io.casehub.ops.container.ContainerEventSource;
import io.casehub.ops.container.podman.PodmanClient;
import io.smallrye.mutiny.subscription.Cancellable;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

@ApplicationScoped
public class PodmanWatchManager {

    private static final Logger LOG = Logger.getLogger(PodmanWatchManager.class.getName());

    private final PodmanClient podmanClient;
    private final ContainerEventSource eventSource;
    private final AtomicBoolean watching = new AtomicBoolean(false);
    private volatile Cancellable subscription;

    @Inject
    public PodmanWatchManager(PodmanClient podmanClient, ContainerEventSource eventSource) {
        this.podmanClient = podmanClient;
        this.eventSource = eventSource;
    }

    public void startWatching() {
        if (!watching.compareAndSet(false, true)) {
            return;
        }

        subscription = podmanClient.events("container")
            .onItem().invoke(this::handleEvent)
            .onFailure().invoke(t ->
                LOG.warning("Podman event stream disconnected: " + t.getMessage()
                            + ". Reconnecting with backoff."))
            .onFailure().retry()
                .withBackOff(Duration.ofSeconds(1), Duration.ofSeconds(30))
                .indefinitely()
            .subscribe().with(
                item -> {},
                failure -> LOG.severe("Podman event stream terminated: " + failure.getMessage())
            );

        LOG.info("Started watching Podman container events");
    }

    public void stopWatching() {
        if (watching.compareAndSet(true, false)) {
            var sub = subscription;
            if (sub != null) {
                sub.cancel();
                subscription = null;
            }
            LOG.info("Stopped watching Podman container events");
        }
    }

    public boolean isWatching() {
        return watching.get();
    }

    @PreDestroy
    void shutdown() {
        stopWatching();
    }

    void handleEvent(JsonObject event) {
        String action = event.getString("Action");
        JsonObject actor = event.getJsonObject("Actor");
        if (actor == null) return;

        JsonObject attributes = actor.getJsonObject("Attributes");
        if (attributes == null) return;

        String containerName = attributes.getString("name");
        if (containerName == null) return;

        NodeStatus status = mapAction(action, attributes);
        if (status == null) return;

        String detail = "container " + action;
        NodeId nodeId = NodeId.of(containerName);
        eventSource.emit(new StateEvent(nodeId, status, detail));
        LOG.fine(() -> "Podman event: " + action + " " + containerName + " → " + status);
    }

    static NodeStatus mapAction(String action, JsonObject attributes) {
        if (action == null) return null;
        if ("health_status".equals(action)) {
            String health = attributes != null ? attributes.getString("healthstatus") : null;
            return "healthy".equals(health) ? NodeStatus.PRESENT : NodeStatus.DRIFTED;
        }
        return switch (action) {
            case "start" -> NodeStatus.PRESENT;
            case "die", "stop" -> NodeStatus.DRIFTED;
            case "remove" -> NodeStatus.ABSENT;
            default -> null;
        };
    }
}

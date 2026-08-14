package io.casehub.ops.app.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

@ApplicationScoped
public class ApplicationEventBroadcaster {

    private static final Logger LOG = Logger.getLogger(ApplicationEventBroadcaster.class.getName());

    public enum EventFilter {
        ALL,
        CASE,
        RECONCILIATION;

        boolean matches(String eventType) {
            return switch (this) {
                case ALL -> true;
                case CASE -> eventType.contains(".case.");
                case RECONCILIATION -> eventType.contains(".reconciliation.");
            };
        }
    }

    public record BroadcastEvent(String eventType, String eventId, String data) {}

    public record SubscriptionHandle(UUID appId, int index) {}

    public record ReplayResult(boolean gapDetected) {}

    @FunctionalInterface
    public interface EventCallback {
        void onEvent(BroadcastEvent event);
    }

    private record Subscription(EventFilter filter, EventCallback callback) {}

    private final int ringBufferCapacity;
    private final ConcurrentHashMap<UUID, RingBuffer> buffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Subscription>> subscriptions = new ConcurrentHashMap<>();

    private static final int DEFAULT_CAPACITY = 1000;

    @Inject
    public ApplicationEventBroadcaster(
            @ConfigProperty(name = "casehub.ops.event-buffer-capacity", defaultValue = "1000") int capacity) {
        this.ringBufferCapacity = capacity;
    }

    public ApplicationEventBroadcaster() {
        this.ringBufferCapacity = DEFAULT_CAPACITY;
    }

    public void publish(UUID appId, String eventType, String eventId, String data) {
        var event = new BroadcastEvent(eventType, eventId, data);
        buffers.computeIfAbsent(appId, k -> new RingBuffer(ringBufferCapacity)).add(event);

        var subs = subscriptions.get(appId);
        if (subs != null) {
            for (Subscription sub : subs) {
                if (sub != null && sub.filter().matches(eventType)) {
                    try {
                        sub.callback().onEvent(event);
                    } catch (Exception e) {
                        LOG.warning("Subscriber failed for app " + appId + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    public SubscriptionHandle subscribe(UUID appId, EventFilter filter, EventCallback callback) {
        var subs = subscriptions.computeIfAbsent(appId, k -> new CopyOnWriteArrayList<>());
        subs.add(new Subscription(filter, callback));
        return new SubscriptionHandle(appId, subs.size() - 1);
    }

    public void unsubscribe(SubscriptionHandle handle) {
        var subs = subscriptions.get(handle.appId());
        if (subs != null && handle.index() < subs.size()) {
            subs.set(handle.index(), null);
        }
    }

    public ReplayResult replayAndSubscribe(UUID appId, String lastEventId,
                                            EventFilter filter, EventCallback callback) {
        var buffer = buffers.get(appId);
        boolean gapDetected = false;

        if (buffer != null) {
            List<BroadcastEvent> events = buffer.snapshot();
            int startIndex = -1;

            for (int i = 0; i < events.size(); i++) {
                if (events.get(i).eventId().equals(lastEventId)) {
                    startIndex = i + 1;
                    break;
                }
            }

            if (startIndex == -1) {
                gapDetected = true;
                startIndex = 0;
            }

            for (int i = startIndex; i < events.size(); i++) {
                BroadcastEvent event = events.get(i);
                if (filter.matches(event.eventType())) {
                    callback.onEvent(event);
                }
            }
        }

        subscribe(appId, filter, callback);
        return new ReplayResult(gapDetected);
    }

    public int bufferSize(UUID appId) {
        var buffer = buffers.get(appId);
        return buffer != null ? buffer.size() : 0;
    }

    private static class RingBuffer {
        private final BroadcastEvent[] events;
        private int head = 0;
        private int count = 0;

        RingBuffer(int capacity) {
            this.events = new BroadcastEvent[capacity];
        }

        synchronized void add(BroadcastEvent event) {
            events[(head + count) % events.length] = event;
            if (count < events.length) {
                count++;
            } else {
                head = (head + 1) % events.length;
            }
        }

        synchronized int size() {
            return count;
        }

        synchronized List<BroadcastEvent> snapshot() {
            var result = new java.util.ArrayList<BroadcastEvent>(count);
            for (int i = 0; i < count; i++) {
                result.add(events[(head + i) % events.length]);
            }
            return result;
        }
    }
}

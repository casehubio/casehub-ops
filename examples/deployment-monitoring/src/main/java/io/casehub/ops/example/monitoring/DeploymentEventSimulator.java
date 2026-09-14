package io.casehub.ops.example.monitoring;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DeploymentEventSimulator {

    public record SimulatedEvent(String type, Map<String, Object> payload, long timestamp) {}

    private static final EventLevel INPUT = new EventLevel("input", 0);

    public static List<SimulatedEvent> cascadeScenario() {
        var events = new ArrayList<SimulatedEvent>();
        long t = 0;

        // Phase 1: steady state — initial blip then recovery
        events.add(fault("agent-0", "agent", "PROVISION_FAILED",
                "transient network timeout", t += 1000));
        for (int i = 0; i < 3; i++) {
            events.add(recovery("agent-" + i, "agent", t += 1000));
        }

        // Phase 2: cascading failures — database dependency goes down
        events.add(fault("db-primary", "database", "DEPENDENCY_UNAVAILABLE",
                "connection refused: postgres:5432", t += 2000));
        for (int i = 0; i < 6; i++) {
            events.add(fault("agent-" + i, "agent", "PROVISION_FAILED",
                    "dependency db-primary unavailable", t += 500));
        }
        events.add(fault("channel-inbound", "channel", "PROVISION_FAILED",
                "upstream agent-0 not ready", t += 500));
        events.add(fault("channel-outbound", "channel", "PROVISION_FAILED",
                "upstream agent-1 not ready", t += 500));
        events.add(fault("endpoint-api", "endpoint", "NODE_DEGRADED",
                "health check failing", t += 1000));

        // Phase 3: partial recovery — database comes back, agents start recovering
        events.add(recovery("db-primary", "database", t += 5000));
        for (int i = 0; i < 4; i++) {
            events.add(recovery("agent-" + i, "agent", t += 1000));
        }

        // Phase 4: full recovery — remaining nodes recover
        for (int i = 4; i < 6; i++) {
            events.add(recovery("agent-" + i, "agent", t += 1000));
        }
        events.add(recovery("channel-inbound", "channel", t += 500));
        events.add(recovery("channel-outbound", "channel", t += 500));
        events.add(recovery("endpoint-api", "endpoint", t += 500));

        return List.copyOf(events);
    }

    @SuppressWarnings("unchecked")
    public static void publishTo(EventStreamBus<?> bus, List<SimulatedEvent> events) {
        var objectBus = (EventStreamBus<Object>) bus;
        for (var event : events) {
            objectBus.publish(new LevelEvent<>(event.payload(), event.timestamp(), INPUT, null));
        }
    }

    private static SimulatedEvent fault(String nodeId, String nodeType,
                                         String faultType, String reason, long timestamp) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("nodeId", nodeId);
        payload.put("nodeType", nodeType);
        payload.put("faultType", faultType);
        payload.put("reason", reason);
        payload.put("graphVersion", 1L);
        payload.put("parentNodeId", "");
        return new SimulatedEvent("io.casehub.desiredstate.node.faulted", payload, timestamp);
    }

    private static SimulatedEvent recovery(String nodeId, String nodeType, long timestamp) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("nodeId", nodeId);
        payload.put("nodeType", nodeType);
        payload.put("faultType", null);
        payload.put("graphVersion", 1L);
        payload.put("parentNodeId", "");
        return new SimulatedEvent("io.casehub.desiredstate.node.recovered", payload, timestamp);
    }
}

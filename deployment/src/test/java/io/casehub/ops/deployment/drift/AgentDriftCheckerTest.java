package io.casehub.ops.deployment.drift;

import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.AgentQuery;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.ops.api.deployment.AgentNodeSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentDriftCheckerTest {

    private AgentDriftChecker checker;
    private StubAgentRegistry agentRegistry;
    private static final String TENANCY_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        agentRegistry = new StubAgentRegistry();
        checker = new AgentDriftChecker(agentRegistry);
    }

    @Test
    void nodeType() {
        assertEquals("agent", checker.nodeType());
    }

    @Test
    void agentPresent() {
        var cap = new AgentCapability("cap-a", null, null, null, null, null, null, null, List.of(), List.of(), List.of(), Map.of(), null);
        var descriptor = testDescriptor("agent-1", "Agent", List.of(cap), null, null, TENANCY_ID);
        agentRegistry.register(descriptor);

        var spec = new AgentNodeSpec("agent-1", "Agent", "worker", "anthropic", "claude", "4.6",
                "1.0", "fp1", "domain", "slot", "disp", null, Map.of(), List.of(cap), null, "US", "policy", null, List.of());

        assertEquals(NodeStatus.PRESENT, checker.check(spec, TENANCY_ID));
    }

    @Test
    void agentAbsent() {
        var cap = new AgentCapability("cap-a", null, null, null, null, null, null, null, List.of(), List.of(), List.of(), Map.of(), null);
        var spec = new AgentNodeSpec("agent-1", "Agent", "worker", "anthropic", "claude", "4.6",
                "1.0", "fp1", "domain", "slot", "disp", null, Map.of(), List.of(cap), null, "US", "policy", null, List.of());

        assertEquals(NodeStatus.ABSENT, checker.check(spec, TENANCY_ID));
    }

    @Test
    void agentDrifted_capabilitiesMismatch() {
        var cap1 = new AgentCapability("cap-a", null, null, null, null, null, null, null, List.of(), List.of(), List.of(), Map.of(), null);
        var cap2 = new AgentCapability("cap-b", null, null, null, null, null, null, null, List.of(), List.of(), List.of(), Map.of(), null);

        var descriptor = testDescriptor("agent-1", "Agent", List.of(cap1), null, null, TENANCY_ID);
        agentRegistry.register(descriptor);

        var spec = new AgentNodeSpec("agent-1", "Agent", "worker", "anthropic", "claude", "4.6",
                "1.0", "fp1", "domain", "slot", "disp", null, Map.of(), List.of(cap2), null, "US", "policy", null, List.of());

        assertEquals(NodeStatus.DRIFTED, checker.check(spec, TENANCY_ID));
    }

    @Test
    void unknownSpecType() {
        var spec = new io.casehub.ops.api.deployment.ChannelNodeSpec(
                "ch1", "desc", io.casehub.qhorus.api.channel.ChannelSemantic.APPEND,
                null, null, null, null, null, null, null, null, null, null, null);

        assertEquals(NodeStatus.UNKNOWN, checker.check(spec, TENANCY_ID));
    }

    @Test
    void agentDrifted_dispositionMismatch() {
        var cap = new AgentCapability("cap-a", null, null, null, null, null, null, null, List.of(), List.of(), List.of(), Map.of(), null);
        var disp1 = AgentDisposition.builder().delegation(false).build();
        var descriptor = testDescriptor("agent-1", "Agent", List.of(cap), disp1, null, TENANCY_ID);
        agentRegistry.register(descriptor);

        var disp2 = AgentDisposition.builder().delegation(true).build();
        var spec = new AgentNodeSpec("agent-1", "Agent", "worker", "anthropic", "claude", "4.6",
                "1.0", "fp1", "domain", "slot", "disp", null, Map.of(), List.of(cap), disp2, "US", "policy", null, List.of());

        assertEquals(NodeStatus.DRIFTED, checker.check(spec, TENANCY_ID));
    }

    @Test
    void agentDrifted_briefingMismatch() {
        var cap = new AgentCapability("cap-a", null, null, null, null, null, null, null, List.of(), List.of(), List.of(), Map.of(), null);
        var descriptor = testDescriptor("agent-1", "Agent", List.of(cap), null, "Original briefing", TENANCY_ID);
        agentRegistry.register(descriptor);

        var spec = new AgentNodeSpec("agent-1", "Agent", "worker", "anthropic", "claude", "4.6",
                "1.0", "fp1", "domain", "slot", "disp", null, Map.of(), List.of(cap), null, "US", "policy", "Changed briefing", List.of());

        assertEquals(NodeStatus.DRIFTED, checker.check(spec, TENANCY_ID));
    }

    @Test
    void agentDrifted_capabilitySubFieldMismatch() {
        var capDesired = new AgentCapability("cap-a", null, null, 0.85, null, null, null, null, List.of(), List.of(), List.of(), Map.of(), null);
        var capActual = new AgentCapability("cap-a", null, null, 0.50, null, null, null, null, List.of(), List.of(), List.of(), Map.of(), null);
        var descriptor = testDescriptor("agent-1", "Agent", List.of(capActual), null, null, TENANCY_ID);
        agentRegistry.register(descriptor);

        var spec = new AgentNodeSpec("agent-1", "Agent", "worker", "anthropic", "claude", "4.6",
                "1.0", "fp1", "domain", "slot", "disp", null, Map.of(), List.of(capDesired), null, "US", "policy", null, List.of());

        assertEquals(NodeStatus.DRIFTED, checker.check(spec, TENANCY_ID));
    }

    @Test
    void agentPresent_allFieldsMatch() {
        var cap = new AgentCapability("cap-a", null, null, 0.85, 2000L, "medium", null, null, List.of("text"), List.of("text"), List.of("tag"), Map.of("java", 0.95), Set.of("cobol"));
        var disp = AgentDisposition.builder().delegation(false).build();
        var descriptor = testDescriptor("agent-1", "Agent", List.of(cap), disp, "briefing", TENANCY_ID);
        agentRegistry.register(descriptor);

        var spec = new AgentNodeSpec("agent-1", "Agent", "worker", "anthropic", "claude", "4.6",
                "1.0", "fp1", "domain", "slot", "disp", null, Map.of(), List.of(cap), disp, "US", "policy", "briefing", List.of());

        assertEquals(NodeStatus.PRESENT, checker.check(spec, TENANCY_ID));
    }

    private static AgentDescriptor testDescriptor(String agentId, String name,
            List<AgentCapability> capabilities, AgentDisposition disposition,
            String briefing, String tenancyId) {
        return new AgentDescriptor(
                agentId, name, "1.0", "anthropic", "claude", "4.6",
                "fp1", "domain", "slot", "disp", null, Map.of(), "worker",
                null, List.of(), null,
                capabilities, disposition, "US", "policy",
                tenancyId, briefing, null, List.of(), List.of(), List.of(), null);
    }

    // Test stub
    static class StubAgentRegistry implements AgentRegistry {
        private final Map<String, AgentDescriptor> agents = new ConcurrentHashMap<>();

        @Override
        public void register(AgentDescriptor descriptor) {
            String key = descriptor.agentId() + ":" + descriptor.tenancyId();
            agents.put(key, descriptor);
        }

        @Override
        public Optional<AgentDescriptor> findById(String agentId, String tenancyId) {
            String key = agentId + ":" + tenancyId;
            return Optional.ofNullable(agents.get(key));
        }

        @Override
        public List<io.casehub.eidos.api.AgentMatch> find(AgentQuery query) {
            return agents.values().stream()
                    .map(d -> new io.casehub.eidos.api.AgentMatch(d, null))
                    .collect(java.util.stream.Collectors.toList());
        }
    }
}

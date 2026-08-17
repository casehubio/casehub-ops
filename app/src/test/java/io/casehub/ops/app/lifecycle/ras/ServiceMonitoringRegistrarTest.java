package io.casehub.ops.app.lifecycle.ras;

import io.casehub.ops.api.lifecycle.DimensionType;
import io.casehub.ops.api.lifecycle.GanglionBinding;
import io.casehub.ops.app.lifecycle.DimensionStatusService;
import io.casehub.ops.app.lifecycle.ServiceCaseRegistry;
import io.casehub.ops.app.lifecycle.ServiceDetectionBridge;
import io.casehub.ras.api.SituationContext;
import io.casehub.ras.api.SituationRegistrar;
import io.casehub.ras.api.SituationRegistration;
import io.casehub.ras.api.SituationStore;
import io.casehub.ras.api.TriggerAction;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServiceMonitoringRegistrarTest {

    private final List<SituationRegistration> registeredSituations = new ArrayList<>();
    private final List<String> deregisteredSituationIds = new ArrayList<>();
    private final List<List<GanglionBinding>> registeredBindings = new ArrayList<>();
    private final List<UUID> deregisteredBindingsCaseIds = new ArrayList<>();
    private final List<String> storeRemovedSituationIds = new ArrayList<>();

    private ServiceMonitoringRegistrar registrar;

    @BeforeEach
    void setUp() {
        SituationRegistrar stubRegistrar = new SituationRegistrar() {
            @Override public void register(SituationRegistration r) { registeredSituations.add(r); }
            @Override public void deregister(String id) { deregisteredSituationIds.add(id); }
            @Override public boolean exists(String id) { return false; }
        };
        ServiceDetectionBridge stubBridge = new ServiceDetectionBridge(
            new ServiceCaseRegistry(), new DimensionStatusService()
        ) {
            @Override public void registerBindings(UUID caseId, List<GanglionBinding> bindings) {
                registeredBindings.add(bindings);
            }
            @Override public void deregisterBindings(UUID caseId) {
                deregisteredBindingsCaseIds.add(caseId);
            }
        };
        SituationStore stubStore = new SituationStore() {
            @Override public Optional<SituationContext> find(String s, String c, String t) { return Optional.empty(); }
            @Override public SituationContext save(SituationContext ctx) { return ctx; }
            @Override public void remove(String s, String c, String t) {}
            @Override public int removeExpired(Instant cutoff) { return 0; }
            @Override public void removeAllForSituation(String situationId) {
                storeRemovedSituationIds.add(situationId);
            }
        };
        registrar = new ServiceMonitoringRegistrar(stubRegistrar, stubBridge, stubStore);
    }

    @Test
    void registerCreates15Situations() {
        registrar.register(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(15, registeredSituations.size());
    }

    @Test
    void registerCreates37Bindings() {
        registrar.register(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(1, registeredBindings.size());
        assertEquals(37, registeredBindings.getFirst().size());
    }

    @Test
    void situationIdsContainAppId() {
        UUID appId = UUID.randomUUID();
        registrar.register(appId, UUID.randomUUID());
        for (var reg : registeredSituations) {
            assertTrue(reg.definition().situationId().contains(appId.toString()),
                "situationId missing appId: " + reg.definition().situationId());
        }
    }

    @Test
    void situationIdsStartWithOps() {
        registrar.register(UUID.randomUUID(), UUID.randomUUID());
        for (var reg : registeredSituations) {
            assertTrue(reg.definition().situationId().startsWith("ops:"),
                "situationId missing ops: prefix: " + reg.definition().situationId());
        }
    }

    @Test
    void allSituationsUseNotifyOnly() {
        registrar.register(UUID.randomUUID(), UUID.randomUUID());
        for (var reg : registeredSituations) {
            assertInstanceOf(TriggerAction.NotifyOnly.class, reg.definition().triggerAction());
        }
    }

    @Test
    void situationIdsAreUnique() {
        registrar.register(UUID.randomUUID(), UUID.randomUUID());
        Set<String> ids = new HashSet<>();
        for (var reg : registeredSituations) {
            assertTrue(ids.add(reg.definition().situationId()),
                "Duplicate situationId: " + reg.definition().situationId());
        }
    }

    @Test
    void situationIdsFollowNamingConvention() {
        UUID appId = UUID.randomUUID();
        registrar.register(appId, UUID.randomUUID());

        var ids = registeredSituations.stream()
            .map(r -> r.definition().situationId())
            .toList();

        assertTrue(ids.contains("ops:health-rt:" + appId));
        assertTrue(ids.contains("ops:health-pd:" + appId));
        assertTrue(ids.contains("ops:drift:" + appId));
        assertTrue(ids.contains("ops:compliance-pd:" + appId));
        assertTrue(ids.contains("ops:compliance-ev:" + appId));
        assertTrue(ids.contains("ops:scaling-rt:" + appId));
        assertTrue(ids.contains("ops:scaling-pd:" + appId));
        assertTrue(ids.contains("ops:change-rt:" + appId));
        assertTrue(ids.contains("ops:change-pd:" + appId));
        assertTrue(ids.contains("ops:security-rt:" + appId));
        assertTrue(ids.contains("ops:security-pd:" + appId));
        assertTrue(ids.contains("ops:maint:" + appId));
        assertTrue(ids.contains("ops:problems:" + appId));
        assertTrue(ids.contains("ops:decommission-rt:" + appId));
        assertTrue(ids.contains("ops:decommission-pd:" + appId));
    }

    @Test
    void deregisterRemovesAll15Situations() {
        UUID appId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        registrar.register(appId, caseId);

        registrar.deregister(appId, caseId);
        assertEquals(15, deregisteredSituationIds.size());
    }

    @Test
    void deregisterRemovesBindings() {
        UUID appId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        registrar.register(appId, caseId);

        registrar.deregister(appId, caseId);
        assertEquals(1, deregisteredBindingsCaseIds.size());
        assertEquals(caseId, deregisteredBindingsCaseIds.getFirst());
    }

    @Test
    void deregisterCleansUpStoreForPersistentSituations() {
        UUID appId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        registrar.register(appId, caseId);

        registrar.deregister(appId, caseId);

        assertTrue(storeRemovedSituationIds.contains("ops:drift:" + appId));
        assertTrue(storeRemovedSituationIds.contains("ops:compliance-ev:" + appId));
    }

    @Test
    void bindingsContainAllNineDimensions() {
        registrar.register(UUID.randomUUID(), UUID.randomUUID());
        var bindings = registeredBindings.getFirst();
        Set<DimensionType> dims = new HashSet<>();
        for (var b : bindings) {
            dims.add(b.dimension());
        }
        assertEquals(Set.of(DimensionType.values()), dims);
    }

    @Test
    void correlationKeyExtractorReturnsCaseId() {
        UUID caseId = UUID.randomUUID();
        registrar.register(UUID.randomUUID(), caseId);
        var extractor = registeredSituations.getFirst().correlationKeyExtractor();
        var dummyEvent = CloudEventBuilder.v1()
            .withId("e1").withSource(URI.create("/t")).withType("test")
            .build();
        assertEquals(caseId.toString(), extractor.extract(dummyEvent));
    }

    @Test
    void eventFilterMatchesApplicationSubject() {
        UUID appId = UUID.randomUUID();
        registrar.register(appId, UUID.randomUUID());
        var filter = registeredSituations.getFirst().eventFilter();

        var matchingEvent = CloudEventBuilder.v1()
            .withId("e1").withSource(URI.create("/t")).withType("test")
            .withSubject(appId.toString())
            .build();
        assertTrue(filter.accepts(matchingEvent));

        var nonMatchingEvent = CloudEventBuilder.v1()
            .withId("e2").withSource(URI.create("/t")).withType("test")
            .withSubject(UUID.randomUUID().toString())
            .build();
        assertFalse(filter.accepts(nonMatchingEvent));
    }

    @Test
    void allSituationsHaveChainModeOr() {
        registrar.register(UUID.randomUUID(), UUID.randomUUID());
        for (var reg : registeredSituations) {
            assertInstanceOf(io.casehub.ras.api.ChainMode.Or.class, reg.definition().chainMode(),
                reg.definition().situationId() + " does not use ChainMode.Or");
        }
    }

    @Test
    void allSituationsHaveRepeatingTriggerMode() {
        registrar.register(UUID.randomUUID(), UUID.randomUUID());
        for (var reg : registeredSituations) {
            assertInstanceOf(io.casehub.ras.api.TriggerMode.Repeating.class, reg.definition().triggerMode(),
                reg.definition().situationId() + " does not use TriggerMode.Repeating");
        }
    }
}

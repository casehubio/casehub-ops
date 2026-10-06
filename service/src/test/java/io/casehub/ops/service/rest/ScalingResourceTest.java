package io.casehub.ops.service.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.ops.service.model.ApplicationStatus;
import io.casehub.ops.service.model.ScalingRule;
import io.casehub.ops.service.model.ServiceDefinition;
import io.casehub.ops.service.rest.dto.ScaleServiceRequest;
import io.casehub.ops.service.service.ScalingRequestedEvent;
import io.casehub.ops.service.service.ScalingService;
import io.casehub.ops.api.infra.types.ResourceRequirements;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;

class ScalingResourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module());

    @Test
    void validRequestDelegatesToService() {
        var events = new CopyOnWriteArrayList<ScalingRequestedEvent>();
        var service = buildService(events);

        var response = service.scale("app-1", UUID.randomUUID(), ApplicationStatus.RUNNING,
                servicesJson("web", 2, List.of()), "web",
                new ScaleServiceRequest(5, "manual"));

        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).targetReplicas()).isEqualTo(5);
        assertThat(events.get(0).serviceId()).isEqualTo("web");
    }

    @Test
    void wrongStatusReturns409() {
        var service = buildService(new CopyOnWriteArrayList<>());

        var response = service.scale("app-1", UUID.randomUUID(), ApplicationStatus.DRAFT,
                servicesJson("web", 2, List.of()), "web",
                new ScaleServiceRequest(5, "manual"));

        assertThat(response.getStatus()).isEqualTo(409);
    }

    @Test
    void degradedStatusAllowed() {
        var service = buildService(new CopyOnWriteArrayList<>());

        var response = service.scale("app-1", UUID.randomUUID(), ApplicationStatus.DEGRADED,
                servicesJson("web", 2, List.of()), "web",
                new ScaleServiceRequest(5, "manual"));

        assertThat(response.getStatus()).isEqualTo(202);
    }

    // --- helpers ---

    private ScalingService buildService(List<ScalingRequestedEvent> events) {
        return buildServiceWithCooldown(events, Set.of());
    }

    private ScalingService buildServiceWithCooldown(List<ScalingRequestedEvent> events,
                                                     Set<String> coolingDown) {
        return new ScalingService(events::add,
                (appId, serviceId) -> coolingDown.contains(appId + ":" + serviceId),
                (appId, serviceId) -> {},
                objectMapper);
    }

    private String servicesJson(String serviceId, int replicas, List<ScalingRule> rules) {
        var sd = new ServiceDefinition(serviceId, serviceId, "img:1.0", replicas,
                List.of(), Map.of(),
                new ResourceRequirements("100m", "256Mi", "50m", "128Mi"),
                List.of(), Optional.empty(), List.of(), rules, 0);
        try {
            return objectMapper.writeValueAsString(List.of(sd));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

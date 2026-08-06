package io.casehub.ops.deployment.handler;

import io.casehub.desiredstate.api.*;
import io.casehub.ops.api.deployment.DetectionNodeSpec;
import io.casehub.ras.api.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class DetectionProvisionHandlerTest {

    @Test
    void provisionRegistersWithRegistrar() {
        var registrar = new StubSituationRegistrar();
        var handler = new DetectionProvisionHandler(registrar);
        var spec = testSpec("app.detect-failure");
        var graph = new io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory()
                .of(List.of(), List.of());

        var result = handler.provision(spec, new ProvisionContext("tenant-1", graph));

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(registrar.registered).containsKey("app.detect-failure");
    }

    @Test
    void deprovisionDeregistersFromRegistrar() {
        var registrar = new StubSituationRegistrar();
        var handler = new DetectionProvisionHandler(registrar);
        var spec = testSpec("app.detect-failure");
        var graph = new io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory()
                .of(List.of(), List.of());

        registrar.register(spec.toRegistration());
        var result = handler.deprovision(spec, new DeprovisionContext("tenant-1", graph));

        assertThat(result).isInstanceOf(DeprovisionResult.Success.class);
        assertThat(registrar.registered).doesNotContainKey("app.detect-failure");
    }

    private DetectionNodeSpec testSpec(String situationId) {
        return new DetectionNodeSpec(
                situationId,
                Set.of("test.event"),
                Duration.ofMinutes(5), null,
                new ChainMode.Streak("g1", 3),
                new TriggerAction.NotifyOnly(),
                new TriggerMode.FireOnce());
    }

    static class StubSituationRegistrar implements SituationRegistrar {
        final Map<String, SituationRegistration> registered = new HashMap<>();

        @Override
        public void register(SituationRegistration registration) {
            registered.put(registration.definition().situationId(), registration);
        }

        @Override
        public void deregister(String situationId) {
            registered.remove(situationId);
        }

        @Override
        public boolean exists(String situationId) {
            return registered.containsKey(situationId);
        }
    }
}

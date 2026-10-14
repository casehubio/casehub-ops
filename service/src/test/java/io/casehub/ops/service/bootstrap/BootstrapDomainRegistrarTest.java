package io.casehub.ops.service.bootstrap;

import io.casehub.desiredstate.api.DomainId;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.desiredstate.runtime.composition.CrossDomainCompositionEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BootstrapDomainRegistrarTest {

    private final DefaultDesiredStateGraphFactory factory = new DefaultDesiredStateGraphFactory();

    @Test
    void localDevRegistersOnlyDeploymentDomain() {
        var engine = new CrossDomainCompositionEngine(factory);
        var registrar = new BootstrapDomainRegistrar(engine, "local-dev");
        registrar.onStartup();

        assertThat(engine.registrationCount()).isEqualTo(1);
        engine.compose();
        assertThat(engine.topologicalOrder()).containsExactly(DomainId.of("deployment"));
    }

    @Test
    void localDistributedRegistersInfraAndDeployment() {
        var engine = new CrossDomainCompositionEngine(factory);
        var registrar = new BootstrapDomainRegistrar(engine, "local-distributed");
        registrar.onStartup();

        assertThat(engine.registrationCount()).isEqualTo(2);
        engine.compose();
        assertThat(engine.topologicalOrder()).containsExactly(
            DomainId.of("infra"), DomainId.of("deployment"));
    }

    @Test
    void enterpriseRegistersInfraAndDeployment() {
        var engine = new CrossDomainCompositionEngine(factory);
        var registrar = new BootstrapDomainRegistrar(engine, "enterprise");
        registrar.onStartup();

        assertThat(engine.registrationCount()).isEqualTo(2);
        engine.compose();
        assertThat(engine.topologicalOrder()).containsExactly(
            DomainId.of("infra"), DomainId.of("deployment"));
    }
}

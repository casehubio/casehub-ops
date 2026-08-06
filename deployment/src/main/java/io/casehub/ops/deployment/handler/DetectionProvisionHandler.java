package io.casehub.ops.deployment.handler;

import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.ops.api.deployment.DetectionNodeSpec;
import io.casehub.ras.api.SituationRegistrar;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DetectionProvisionHandler {

    private final SituationRegistrar registrar;

    @Inject
    public DetectionProvisionHandler(SituationRegistrar registrar) {
        this.registrar = registrar;
    }

    public ProvisionResult provision(DetectionNodeSpec spec, ProvisionContext context) {
        registrar.register(spec.toRegistration());
        return new ProvisionResult.Success();
    }

    public DeprovisionResult deprovision(DetectionNodeSpec spec, DeprovisionContext context) {
        registrar.deregister(spec.situationId());
        return new DeprovisionResult.Success();
    }
}

package io.casehub.ops.app.case_;

import io.casehub.api.model.CaseDefinition;
import io.casehub.engine.common.spi.CaseDefinitionRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class CaseDefinitionRegistrar {

    private static final Logger LOG = Logger.getLogger(CaseDefinitionRegistrar.class.getName());

    private final CaseDefinitionRegistry                                 registry;
    private final io.casehub.ops.app.service.NodeConvergenceTracker      convergenceTracker;
    private final io.casehub.ops.app.service.ApplicationLifecycleService applicationLifecycleService;

    @Inject
    public CaseDefinitionRegistrar(CaseDefinitionRegistry registry,
                                   io.casehub.ops.app.service.NodeConvergenceTracker convergenceTracker,
                                   io.casehub.ops.app.service.ApplicationLifecycleService applicationLifecycleService) {
        this.registry                    = registry;
        this.convergenceTracker          = convergenceTracker;
        this.applicationLifecycleService = applicationLifecycleService;
    }

    void onStartup(@Observes @Priority(10) StartupEvent event) {
        List<CaseDefinition> definitions = List.of(
                ApplicationCaseDescriptor.build(),
                DriftRemediationCaseDescriptor.build(convergenceTracker),
                CveResponseCaseDescriptor.build(applicationLifecycleService, convergenceTracker),
                ServiceUpgradeCaseDescriptor.build(applicationLifecycleService, convergenceTracker),
                IncidentResponseCaseDescriptor.build(applicationLifecycleService, convergenceTracker),
                ScalingEventCaseDescriptor.build(applicationLifecycleService, convergenceTracker),
                ComplianceRemediationCaseDescriptor.build(applicationLifecycleService, convergenceTracker));

        for (CaseDefinition def : definitions) {
            registry.registerCaseDefinition(def);
            LOG.fine("Registered case definition: " + def.getNamespace() + ":" + def.getName());
        }
        LOG.info("Registered " + definitions.size() + " case definitions");
    }
}

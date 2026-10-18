package io.casehub.ops.service.bootstrap;

import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.DomainId;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.desiredstate.runtime.composition.CrossDomainCompositionEngine;
import io.casehub.desiredstate.runtime.composition.DomainRegistration;

import java.util.List;
import java.util.Set;

public class BootstrapDomainRegistrar {

    private final CrossDomainCompositionEngine engine;
    private final String deploymentProfile;

    public BootstrapDomainRegistrar(
            CrossDomainCompositionEngine engine,
            String deploymentProfile) {
        this.engine = engine;
        this.deploymentProfile = deploymentProfile;
    }

    public void onStartup() {
        Set<NodeType> infraProvides = Set.of();

        if (!"local-dev".equals(deploymentProfile)) {
            infraProvides = registerInfraDomain();
        }

        registerDeploymentDomain(infraProvides);
        registerClaudonyDomain();
    }

    private Set<NodeType> registerInfraDomain() {
        var factory = new DefaultDesiredStateGraphFactory();
        Set<NodeType> provides = Set.of(
            NodeType.of("container:database"),
            NodeType.of("network"),
            NodeType.of("volume"));

        var emptyGraph = factory.of(List.of(), List.of());
        engine.registerDomain(DomainRegistration.builder(DomainId.of("infra"),
            CompilationResult.single(emptyGraph))
            .provides(provides)
            .build());
        return provides;
    }

    private void registerDeploymentDomain(Set<NodeType> infraRequires) {
        var factory = new DefaultDesiredStateGraphFactory();
        var emptyGraph = factory.of(List.of(), List.of());
        engine.registerDomain(DomainRegistration.builder(DomainId.of("deployment"),
            CompilationResult.single(emptyGraph))
            .provides(Set.of(NodeType.of("casehub_application")))
            .requires(infraRequires)
            .build());
    }

    private void registerClaudonyDomain() {
        var factory    = new DefaultDesiredStateGraphFactory();
        var emptyGraph = factory.of(List.of(), List.of());
        engine.registerDomain(DomainRegistration.builder(DomainId.of("claudony"),
                                                         CompilationResult.single(emptyGraph))
                                                .provides(Set.of(
                                                        io.casehub.ops.api.claudony.ClaudonyNodeTypes.APP,
                                                        io.casehub.ops.api.claudony.ClaudonyNodeTypes.POOL))
                                                .build());
    }

}

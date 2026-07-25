package io.casehub.ops.api.infra.spi;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.ops.api.infra.InfraNodeSpec;
import io.casehub.ops.api.infra.context.InfraProvisionContext;
import io.casehub.ops.api.infra.plan.ProvisionPlan;
import io.casehub.ops.api.infra.state.DriftReport;
import io.casehub.ops.api.infra.state.ResourceState;

import java.util.Optional;

public interface InfraBackend {

    String backendId();

    BackendProvisionResult provision(InfraNodeSpec spec, InfraProvisionContext context);

    BackendDeprovisionResult deprovision(InfraNodeSpec spec, InfraProvisionContext context);

    ResourceState readState(NodeId nodeId, InfraNodeSpec spec);

    DriftReport detectDrift(NodeId nodeId, InfraNodeSpec spec);

    Optional<ProvisionPlan> plan(InfraNodeSpec spec, InfraProvisionContext context);
}

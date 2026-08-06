# io.casehub.ops.api.infra.spi.InfraBackend

**Package:** `io.casehub.ops.api.infra.spi`

**Kind:** `interface`

## Methods

### `public abstract java.lang.String backendId()`

### `public abstract io.casehub.ops.api.infra.spi.BackendDeprovisionResult deprovision(io.casehub.ops.api.infra.InfraNodeSpec spec, io.casehub.ops.api.infra.context.InfraProvisionContext context)`

#### Parameters

- `spec` (`io.casehub.ops.api.infra.InfraNodeSpec`)
- `context` (`io.casehub.ops.api.infra.context.InfraProvisionContext`)

### `public abstract io.casehub.ops.api.infra.state.DriftReport detectDrift(NodeId nodeId, io.casehub.ops.api.infra.InfraNodeSpec spec)`

#### Parameters

- `nodeId` (`NodeId`)
- `spec` (`io.casehub.ops.api.infra.InfraNodeSpec`)

### `public abstract java.util.Optional<io.casehub.ops.api.infra.plan.ProvisionPlan> plan(io.casehub.ops.api.infra.InfraNodeSpec spec, io.casehub.ops.api.infra.context.InfraProvisionContext context)`

#### Parameters

- `spec` (`io.casehub.ops.api.infra.InfraNodeSpec`)
- `context` (`io.casehub.ops.api.infra.context.InfraProvisionContext`)

### `public abstract io.casehub.ops.api.infra.spi.BackendProvisionResult provision(io.casehub.ops.api.infra.InfraNodeSpec spec, io.casehub.ops.api.infra.context.InfraProvisionContext context)`

#### Parameters

- `spec` (`io.casehub.ops.api.infra.InfraNodeSpec`)
- `context` (`io.casehub.ops.api.infra.context.InfraProvisionContext`)

### `public abstract io.casehub.ops.api.infra.state.ResourceState readState(NodeId nodeId, io.casehub.ops.api.infra.InfraNodeSpec spec)`

#### Parameters

- `nodeId` (`NodeId`)
- `spec` (`io.casehub.ops.api.infra.InfraNodeSpec`)

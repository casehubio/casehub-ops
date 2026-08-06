# io.casehub.ops.api.infra.spi.ResourceProvisioner

**Package:** `io.casehub.ops.api.infra.spi`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.ops.api.infra.task.ProvisionOutcome execute(io.casehub.ops.api.infra.task.ProvisionTask task)`

#### Parameters

- `task` (`io.casehub.ops.api.infra.task.ProvisionTask`)

### `public abstract boolean handles(io.casehub.ops.api.infra.InfraNodeSpec spec)`

#### Parameters

- `spec` (`io.casehub.ops.api.infra.InfraNodeSpec`)

### `public abstract java.lang.String provisionerId()`

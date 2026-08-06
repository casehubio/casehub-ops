# io.casehub.ops.api.deployment.ChannelNodeSpec

**Package:** `io.casehub.ops.api.deployment`

**Kind:** `record`

## Fields

### `adminInstances` (`java.lang.String`)

### `allowedTypes` (`java.util.Set<MessageType>`)

### `allowedWriters` (`java.lang.String`)

### `barrierContributors` (`java.lang.String`)

### `deniedTypes` (`java.util.Set<MessageType>`)

### `description` (`java.lang.String`)

### `externalKey` (`java.lang.String`)

### `inboundConnectorId` (`java.lang.String`)

### `name` (`java.lang.String`)

### `outboundConnectorId` (`java.lang.String`)

### `outboundDestination` (`java.lang.String`)

### `rateLimitPerChannel` (`java.lang.Integer`)

### `rateLimitPerInstance` (`java.lang.Integer`)

### `semantic` (`ChannelSemantic`)

## Record Components

### `adminInstances` (`java.lang.String`)

### `allowedTypes` (`java.util.Set<MessageType>`)

### `allowedWriters` (`java.lang.String`)

### `barrierContributors` (`java.lang.String`)

### `deniedTypes` (`java.util.Set<MessageType>`)

### `description` (`java.lang.String`)

### `externalKey` (`java.lang.String`)

### `inboundConnectorId` (`java.lang.String`)

### `name` (`java.lang.String`)

### `outboundConnectorId` (`java.lang.String`)

### `outboundDestination` (`java.lang.String`)

### `rateLimitPerChannel` (`java.lang.Integer`)

### `rateLimitPerInstance` (`java.lang.Integer`)

### `semantic` (`ChannelSemantic`)

## Constructors

### `public ChannelNodeSpec(java.lang.String name, java.lang.String description, ChannelSemantic semantic, java.util.Set<MessageType> allowedTypes, java.util.Set<MessageType> deniedTypes, java.lang.String allowedWriters, java.lang.String adminInstances, java.lang.String barrierContributors, java.lang.Integer rateLimitPerChannel, java.lang.Integer rateLimitPerInstance, java.lang.String inboundConnectorId, java.lang.String externalKey, java.lang.String outboundConnectorId, java.lang.String outboundDestination)`

#### Parameters

- `name` (`java.lang.String`)
- `description` (`java.lang.String`)
- `semantic` (`ChannelSemantic`)
- `allowedTypes` (`java.util.Set<MessageType>`)
- `deniedTypes` (`java.util.Set<MessageType>`)
- `allowedWriters` (`java.lang.String`)
- `adminInstances` (`java.lang.String`)
- `barrierContributors` (`java.lang.String`)
- `rateLimitPerChannel` (`java.lang.Integer`)
- `rateLimitPerInstance` (`java.lang.Integer`)
- `inboundConnectorId` (`java.lang.String`)
- `externalKey` (`java.lang.String`)
- `outboundConnectorId` (`java.lang.String`)
- `outboundDestination` (`java.lang.String`)

## Methods

### `public java.lang.String adminInstances()`

### `public java.util.Set<MessageType> allowedTypes()`

### `public java.lang.String allowedWriters()`

### `public java.lang.String barrierContributors()`

### `public java.util.Set<MessageType> deniedTypes()`

### `public java.lang.String description()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String externalKey()`

### `public final int hashCode()`

### `public java.lang.String inboundConnectorId()`

### `public java.lang.String name()`

### `public java.lang.String nodeId()`

### `public java.lang.String nodeType()`

### `public java.lang.String outboundConnectorId()`

### `public java.lang.String outboundDestination()`

### `public java.lang.Integer rateLimitPerChannel()`

### `public java.lang.Integer rateLimitPerInstance()`

### `public ChannelSemantic semantic()`

### `public final java.lang.String toString()`

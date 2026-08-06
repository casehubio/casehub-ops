# io.casehub.ops.api.deployment.EndpointNodeSpec

**Package:** `io.casehub.ops.api.deployment`

**Kind:** `record`

## Fields

### `capabilities` (`java.util.Set<EndpointCapability>`)

### `credentialRef` (`java.lang.String`)

### `path` (`java.lang.String`)

### `properties` (`java.util.Map<java.lang.String,java.lang.String>`)

### `protocol` (`EndpointProtocol`)

### `type` (`EndpointType`)

## Record Components

### `capabilities` (`java.util.Set<EndpointCapability>`)

### `credentialRef` (`java.lang.String`)

### `path` (`java.lang.String`)

### `properties` (`java.util.Map<java.lang.String,java.lang.String>`)

### `protocol` (`EndpointProtocol`)

### `type` (`EndpointType`)

## Constructors

### `public EndpointNodeSpec(java.lang.String path, EndpointType type, EndpointProtocol protocol, java.util.Map<java.lang.String,java.lang.String> properties, java.lang.String credentialRef, java.util.Set<EndpointCapability> capabilities)`

#### Parameters

- `path` (`java.lang.String`)
- `type` (`EndpointType`)
- `protocol` (`EndpointProtocol`)
- `properties` (`java.util.Map<java.lang.String,java.lang.String>`)
- `credentialRef` (`java.lang.String`)
- `capabilities` (`java.util.Set<EndpointCapability>`)

## Methods

### `public java.util.Set<EndpointCapability> capabilities()`

### `public java.lang.String credentialRef()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String nodeId()`

### `public java.lang.String nodeType()`

### `public java.lang.String path()`

### `public java.util.Map<java.lang.String,java.lang.String> properties()`

### `public EndpointProtocol protocol()`

### `private static void requireProperty(java.util.Map<java.lang.String,java.lang.String> properties, java.lang.String key, java.lang.String protocolName)`

#### Parameters

- `properties` (`java.util.Map<java.lang.String,java.lang.String>`)
- `key` (`java.lang.String`)
- `protocolName` (`java.lang.String`)

### `public EndpointDescriptor toDescriptor(java.lang.String tenancyId)`

#### Parameters

- `tenancyId` (`java.lang.String`)

### `public final java.lang.String toString()`

### `public EndpointType type()`

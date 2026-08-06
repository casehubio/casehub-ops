# io.casehub.ops.api.deployment.AgentNodeSpec

**Package:** `io.casehub.ops.api.deployment`

**Kind:** `record`

## Fields

### `agentId` (`java.lang.String`)

### `axisVocabularies` (`java.util.Map<DispositionAxis,java.lang.String>`)

### `briefing` (`java.lang.String`)

### `capabilities` (`java.util.List<AgentCapability>`)

### `dataHandlingPolicy` (`java.lang.String`)

### `disposition` (`AgentDisposition`)

### `dispositionVocabulary` (`java.lang.String`)

### `domainVocabulary` (`java.lang.String`)

### `jurisdiction` (`java.lang.String`)

### `modelFamily` (`java.lang.String`)

### `modelVersion` (`java.lang.String`)

### `name` (`java.lang.String`)

### `provider` (`java.lang.String`)

### `providerConfigs` (`java.util.List<io.casehub.ops.api.deployment.ProviderConfig>`)

### `slot` (`java.lang.String`)

### `slotVocabulary` (`java.lang.String`)

### `version` (`java.lang.String`)

### `weightsFingerprint` (`java.lang.String`)

## Record Components

### `agentId` (`java.lang.String`)

### `axisVocabularies` (`java.util.Map<DispositionAxis,java.lang.String>`)

### `briefing` (`java.lang.String`)

### `capabilities` (`java.util.List<AgentCapability>`)

### `dataHandlingPolicy` (`java.lang.String`)

### `disposition` (`AgentDisposition`)

### `dispositionVocabulary` (`java.lang.String`)

### `domainVocabulary` (`java.lang.String`)

### `jurisdiction` (`java.lang.String`)

### `modelFamily` (`java.lang.String`)

### `modelVersion` (`java.lang.String`)

### `name` (`java.lang.String`)

### `provider` (`java.lang.String`)

### `providerConfigs` (`java.util.List<io.casehub.ops.api.deployment.ProviderConfig>`)

### `slot` (`java.lang.String`)

### `slotVocabulary` (`java.lang.String`)

### `version` (`java.lang.String`)

### `weightsFingerprint` (`java.lang.String`)

## Constructors

### `public AgentNodeSpec(java.lang.String agentId, java.lang.String name, java.lang.String slot, java.lang.String provider, java.lang.String modelFamily, java.lang.String modelVersion, java.lang.String version, java.lang.String weightsFingerprint, java.lang.String domainVocabulary, java.lang.String slotVocabulary, java.lang.String dispositionVocabulary, java.util.Map<DispositionAxis,java.lang.String> axisVocabularies, java.util.List<AgentCapability> capabilities, AgentDisposition disposition, java.lang.String jurisdiction, java.lang.String dataHandlingPolicy, java.lang.String briefing, java.util.List<io.casehub.ops.api.deployment.ProviderConfig> providerConfigs)`

#### Parameters

- `agentId` (`java.lang.String`)
- `name` (`java.lang.String`)
- `slot` (`java.lang.String`)
- `provider` (`java.lang.String`)
- `modelFamily` (`java.lang.String`)
- `modelVersion` (`java.lang.String`)
- `version` (`java.lang.String`)
- `weightsFingerprint` (`java.lang.String`)
- `domainVocabulary` (`java.lang.String`)
- `slotVocabulary` (`java.lang.String`)
- `dispositionVocabulary` (`java.lang.String`)
- `axisVocabularies` (`java.util.Map<DispositionAxis,java.lang.String>`)
- `capabilities` (`java.util.List<AgentCapability>`)
- `disposition` (`AgentDisposition`)
- `jurisdiction` (`java.lang.String`)
- `dataHandlingPolicy` (`java.lang.String`)
- `briefing` (`java.lang.String`)
- `providerConfigs` (`java.util.List<io.casehub.ops.api.deployment.ProviderConfig>`)

## Methods

### `public java.lang.String agentId()`

### `public java.util.Map<DispositionAxis,java.lang.String> axisVocabularies()`

### `public java.lang.String briefing()`

### `public java.util.List<AgentCapability> capabilities()`

### `public java.lang.String dataHandlingPolicy()`

### `public AgentDisposition disposition()`

### `public java.lang.String dispositionVocabulary()`

### `public java.lang.String domainVocabulary()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String jurisdiction()`

### `public java.lang.String modelFamily()`

### `public java.lang.String modelVersion()`

### `public java.lang.String name()`

### `public java.lang.String nodeId()`

### `public java.lang.String nodeType()`

### `public java.lang.String provider()`

### `public java.util.List<io.casehub.ops.api.deployment.ProviderConfig> providerConfigs()`

### `public java.lang.String slot()`

### `public java.lang.String slotVocabulary()`

### `public AgentDescriptor toDescriptor(java.lang.String tenancyId)`

#### Parameters

- `tenancyId` (`java.lang.String`)

### `public final java.lang.String toString()`

### `public java.lang.String version()`

### `public java.lang.String weightsFingerprint()`

### `public io.casehub.ops.api.deployment.AgentNodeSpec withAgentId(java.lang.String newId)`

#### Parameters

- `newId` (`java.lang.String`)

# io.casehub.ops.api.deployment.DeploymentGoals

**Package:** `io.casehub.ops.api.deployment`

**Kind:** `record`

## Fields

### `adaptations` (`java.util.List<io.casehub.ops.api.deployment.AdaptationRuleSpec>`)

### `agents` (`java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.AgentNodeSpec>>`)

### `caseTypes` (`java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.CaseTypeNodeSpec>>`)

### `channels` (`java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.ChannelNodeSpec>>`)

### `endpoints` (`java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.EndpointNodeSpec>>`)

### `trust` (`java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.TrustPolicyNodeSpec>>`)

## Record Components

### `adaptations` (`java.util.List<io.casehub.ops.api.deployment.AdaptationRuleSpec>`)

### `agents` (`java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.AgentNodeSpec>>`)

### `caseTypes` (`java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.CaseTypeNodeSpec>>`)

### `channels` (`java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.ChannelNodeSpec>>`)

### `endpoints` (`java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.EndpointNodeSpec>>`)

### `trust` (`java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.TrustPolicyNodeSpec>>`)

## Constructors

### `public DeploymentGoals(java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.AgentNodeSpec>> agents, java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.ChannelNodeSpec>> channels, java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.CaseTypeNodeSpec>> caseTypes, java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.TrustPolicyNodeSpec>> trust, java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.EndpointNodeSpec>> endpoints, java.util.List<io.casehub.ops.api.deployment.AdaptationRuleSpec> adaptations)`

#### Parameters

- `agents` (`java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.AgentNodeSpec>>`)
- `channels` (`java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.ChannelNodeSpec>>`)
- `caseTypes` (`java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.CaseTypeNodeSpec>>`)
- `trust` (`java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.TrustPolicyNodeSpec>>`)
- `endpoints` (`java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.EndpointNodeSpec>>`)
- `adaptations` (`java.util.List<io.casehub.ops.api.deployment.AdaptationRuleSpec>`)

## Methods

### `public java.util.List<io.casehub.ops.api.deployment.AdaptationRuleSpec> adaptations()`

### `public java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.AgentNodeSpec>> agents()`

### `public java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.CaseTypeNodeSpec>> caseTypes()`

### `public java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.ChannelNodeSpec>> channels()`

### `public java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.EndpointNodeSpec>> endpoints()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public final java.lang.String toString()`

### `public java.util.List<io.casehub.ops.api.deployment.GoalEntry<io.casehub.ops.api.deployment.TrustPolicyNodeSpec>> trust()`

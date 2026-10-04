## D1: Claudony pool API surface

**Choice:** Add createPool/destroyPool endpoints to ClaudonyPoolApi in a separate claudony issue. ops consumes the full CRUD contract.
**Alternatives:**
- REST client to existing API only — limits ops to drift/update of pre-existing pools, no lifecycle management
- SPI interface + stub — defers real integration, risk of contract mismatch
**Rationale:** ops needs full pool lifecycle (create, update, destroy) via desiredstate. Claudony currently lacks create/destroy endpoints.
**Trade-offs:** Requires a separate claudony issue to land first (or code against expected contract).
**Sources:** ClaudonyPoolApi.java, AgentPoolDefinitionRegistry.java, PoolService.java
**Exploration:** quick
**Status:** captured

## D2: PoolNodeSpec shape — structured nested records

**Choice:** Structured nested records (PoolScalingSpec, EvictionStrategy enum) matching Claudony's model.
**Alternatives:**
- Flat string fields — simpler spec but drift checking requires parsing, loses type safety
**Rationale:** Type-safe drift checking, direct mapping to Claudony's ScalingConfig/EvictionStrategy types.
**Trade-offs:** More types to maintain, tighter coupling to Claudony's model.
**Sources:** AgentPoolDefinition.java (ScalingConfig, EvictionStrategy), PoolDetail.java
**Exploration:** quick
**Status:** captured

## D3: Approval infrastructure — reuse existing

**Choice:** Reuse existing ApprovalEvaluator + PlanStore with pool-specific RiskClassification entries.
**Alternatives:**
- Pool-specific approval logic — more granular but unnecessary complexity
- No approval gates — unsafe for destructive pool operations
**Rationale:** Existing infrastructure is idempotent via reconciliation loop. Pool operations fit the same pattern — destroy/scale-to-zero = high risk, config update = low.
**Trade-offs:** None significant — same pattern, just new classification rules.
**Sources:** DeploymentNodeProvisioner.java, ApprovalEvaluator.java, InMemoryPlanStore.java
**Exploration:** quick
**Status:** captured

## D4: Scope — separate claudony issue for CRUD endpoints

**Choice:** File a separate claudony issue for pool CRUD endpoints. This issue (#116) implements ops-side only, coding against the expected API contract.
**Alternatives:**
- Single cross-repo issue — harder to track, mixes concerns
**Rationale:** Clean separation of concerns. ops defines what it needs; claudony implements the endpoints.
**Trade-offs:** ops work can proceed in parallel but integration testing requires both to land.
**Sources:** deployment/pom.xml (no claudony dependency), ClaudonyPoolApi.java
**Exploration:** quick
**Status:** captured

## D5: Drift checking scope — full config comparison

**Choice:** Compare all declarable fields: capacity (min/max), scaling config, eviction strategy. Budget excluded (runtime-only).
**Alternatives:**
- Capacity + health only — simpler but misses scaling/eviction drift, relies on spec hash layer
**Rationale:** Matches AgentDriftChecker pattern of comparing full desired vs. actual descriptor. Catches config drift that spec hash alone would miss.
**Trade-offs:** Requires Claudony's getPool response to expose all config fields (it already does via PoolDetail).
**Sources:** AgentDriftChecker.java, PoolDetail.java
**Exploration:** quick
**Status:** captured

## D6: Communication pattern — PoolOperations interface via mcpDomain

**Choice:** Define a PoolOperations interface in the deployment module (following ChannelProvisionHandler.ChannelOperations pattern). Implementation in app/ uses mcpDomain for REST calls to Claudony.
**Alternatives:**
- New casehub-claudony-client module — YAGNI, no other consumer exists today
- Raw REST client — mcpDomain already provides the infrastructure
**Rationale:** Established pattern in codebase (ChannelOperations). mcpDomain is the standard for cross-service calls in the platform.
**Trade-offs:** None — follows existing conventions on both axes.
**Sources:** ChannelProvisionHandler.java (ChannelOperations pattern), ClaudonyPoolApi.java (@McpDomain annotation)
**Exploration:** quick
**Status:** captured

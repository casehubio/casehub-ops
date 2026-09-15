# 0001 — Push-based SituationRecompiler SPI over poll-based AdaptiveTopologyManager

Date: 2026-09-18
Status: Accepted

## Context and Problem Statement

Deployment topology adaptation needs to respond to runtime conditions (market volatility, security breaches, service degradation). The original implementation (`AdaptiveTopologyManager`) polled `SituationSource` every 5 minutes and observed `SituationChangeEvent` CDI events — mixing pull and push models with a dedicated `ScheduledExecutorService` thread.

## Decision Drivers

* The desiredstate runtime introduced `SituationRecompiler` SPI (desiredstate#49) — a standardised push-based contract
* Poll-based model has inherent latency (up to 5 minutes for missed events)
* `AdaptiveTopologyManager` managed its own reconciliation loop lifecycle (`start`/`updateDesired`/`requestReconciliation`) — crossing responsibility boundaries
* Multiple adaptation domains need to coexist (deployment, CBR) with deterministic ordering

## Considered Options

* **Option A** — `SituationRecompiler` SPI implementation (push-based, return-value contract)
* **Option B** — Keep `AdaptiveTopologyManager` with CDI observer + periodic poll
* **Option C** — Reactive pipeline (`Mutiny`/`Flow`) with situation stream subscription

## Decision Outcome

Chosen option: **Option A**, because the `SituationRecompiler` SPI already exists in desiredstate-api, provides chain-of-responsibility ordering via `priority()`, and the dispatch layer (`SituationRecompilerDispatch`) cleanly separates event observation from graph recompilation.

### Positive Consequences

* No dedicated thread pool — no `ScheduledExecutorService` lifecycle to manage
* Chain-of-responsibility: deployment (100), CBR (MAX_VALUE) — deterministic ordering
* Pure SPI: returns `Optional<CompilationResult>`, dispatch layer handles `updateDesired` + `requestReconciliation`
* `situationResolved()` enables immediate deactivation instead of TTL-based clearing
* Multi-situation composition: tracks all active situations per tenant, recompiles from base every time

### Negative Consequences / Tradeoffs

* Stateful recompiler — maintains per-tenant `trackedSituations` and hysteresis state (vs. stateless poll)
* TTL-based `clearAbsentSituations()` is now a fallback safety net, not the primary deactivation path — requires `situationResolved()` cross-repo change in desiredstate-api

## Pros and Cons of the Options

### Option A — SituationRecompiler SPI

* ✅ Standardised SPI — same contract as `CbrSituationRecompiler`
* ✅ Priority-based ordering — no CDI observer ordering ambiguity
* ✅ No thread management — dispatch layer handles event routing
* ✅ Immediate deactivation via `situationResolved()`
* ❌ Required cross-repo changes (desiredstate#146, #147)

### Option B — Keep AdaptiveTopologyManager

* ✅ Already implemented and tested
* ✅ Simpler mental model — one class does everything
* ❌ Mixes concerns: CDI observation + reconciliation lifecycle + scheduling
* ❌ Poll latency — up to 5 minutes for missed events
* ❌ No priority ordering with other recompilers

### Option C — Reactive pipeline

* ✅ Elegant backpressure handling
* ❌ Significant complexity for a problem that doesn't need backpressure
* ❌ No existing SPI — would require new API design
* ❌ Harder to test (reactive chains vs. synchronous `recompile()`)

## Links

* casehubio/casehub-ops#25 — fsitrading adaptive ops (implementing issue)
* casehubio/casehub-desiredstate#49 — SituationRecompiler SPI delivery
* casehubio/casehub-desiredstate#146 — `situationResolved()` default method
* casehubio/casehub-desiredstate#147 — dispatch layer wiring

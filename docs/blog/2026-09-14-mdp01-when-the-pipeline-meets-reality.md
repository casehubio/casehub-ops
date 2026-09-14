---
entry_type: note
subtype: diary
title: "When the Pipeline Meets Reality"
author: mdp
date: 2026-09-14
issue: "casehubio/casehub-ops#84"
tags: [summarisation, ras, mvel, yaml-pipeline, deployment-monitoring]
---

# When the Pipeline Meets Reality

The design spec for summarisation→RAS integration had been sitting in casehub-desiredstate#74 for twelve days — nine decisions, a full module structure, built-in summariser types, and a logistics example that proved the YAML surface worked. The blocks side landed with #233. All that remained was the ops wiring: a YAML pipeline consuming desiredstate CloudEvents, RAS situation definitions consuming the pipeline's output, and an example showing the whole chain.

The pipeline YAML itself was straightforward. Two levels: `threshold-classify` at L2, classifying each fault event by its `FaultType` (PROVISION_FAILED, DEPENDENCY_UNAVAILABLE, NODE_DESTROYED, and so on), with a catch-all `STABILISING` category for drift and recovery signals. Then `phase-detect` at L3, tracking deployment health through HEALTHY → DEGRADED → RECOVERING transitions based on severity counts.

The interesting part was what the spec didn't anticipate: MVEL3's lazy compilation model. The expression engine compiles on first eval, inferring types from the actual map keys in the first event it sees. If that first event is a recovery (no `faultType` key), the symbol `faultType` is unresolvable and compilation fails. Even adding the key with a null value doesn't help — `getTypeMap()` can't infer a type from null.

This is the kind of bug that hides in test environments where you control event ordering but surfaces in production where you don't. The cascade scenario in the example starts with a transient fault followed by recoveries, then the real failure cascade. That ordering ensures the first event has a populated `faultType` for MVEL to latch onto. But a production deployment where the first reconciliation cycle produces only drift events would hit this wall.

The RAS side was clean. Three ganglia — `deployment-degraded`, `deployment-recovering`, `deployment-healthy` — consuming the L3 phase events, following the same `ExpressionRules` pattern as the existing `OpsMonitoringSituationDefinitionProvider`. A `Streak(2)` chain mode on the sustained-degradation situation means two consecutive DEGRADED transitions before escalation — enough to filter transient spikes.

The example module turned out to be the most instructive piece. `DeploymentEventSimulator` generates a realistic cascade: database dependency goes down, agents fail in sequence, channels follow, then recovery propagates back up. `DeploymentMonitoringExample` wires the YAML pipeline and prints classified anomalies alongside phase transitions. The output reads like a deployment incident timeline — which is the point. The summarisation layer gives operators altitude over individual fault events.

What this opens up: the production `CloudEventIngestionAdapter` will need event shape normalisation before mixed-type events can share a `threshold-classify` level. That's a blocks concern, not an ops concern — the adapter should ensure all map keys referenced by any expression rule are present (with sentinel values for missing keys) before the first event triggers lazy compilation. Filing that as a follow-up on blocks.

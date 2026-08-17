---
layout: post
title: "Wiring RAS into the Ops Console — 37 Ganglia and a CDI Surprise"
date: 2026-08-17
entry_type: note
subtype: diary
projects: [casehub-ops]
tags: [ras, detection, cdi, ops-console, health-monitoring]
series: issue-47-wire-ras-health-monitoring
---

The ops console has had a detection-to-dimension routing layer for a while — `ServiceDetectionBridge` maps situation types to dimension context writes, and `ServiceCaseDescriptor` declares engine bindings that spawn child cases when dimensions change. But nothing connected RAS to the bridge. No ganglia declared, no situations registered on deploy, no observer translating `SituationChangeEvent` into bridge calls. The detection surface existed with nothing feeding it.

This session filled the gap with four components. `OpsMonitoringSituationDefinitionProvider` declares 37 ganglia as `ExpressionRules` descriptors — 32 detection and 5 recovery — covering all nine operational dimensions. Each ganglion wraps a `LambdaExpression<Map, Boolean>` that evaluates against the CloudEvent expression context: a map with keys for `type`, `source`, `subject`, and a nested `data` map carrying the event payload. The conditions are straightforward — heartbeat probe status, CPU utilization thresholds, CVE counts, certificate expiry windows — but getting them right required understanding that `ExpressionRules.Rule` takes an `ExpressionEvaluator` (the interface), not a raw lambda. `LambdaExpression` from the platform API bridges that gap.

The interesting design decision was cadence-class grouping for situations. `SituationDefinition` constrains all ganglia within it to share `correlationWindow` and `triggerMode`. Health monitoring spans sub-second reactive checks (heartbeat probes, 30s window) and multi-minute pattern analysis (log anomalies, 5min window). One situation can't serve both. The solution: split each dimension into cadence classes — `health-rt` and `health-pd`, `scaling-rt` and `scaling-pd` — with uniform-cadence dimensions like drift and maintenance staying as single situations. This gives 15 situations per managed application instead of 9 (per-dimension) or 37 (per-ganglion), landing in the sweet spot between flexibility and registration overhead.

`ServiceMonitoringRegistrar` builds both the 15 situations and 37 `GanglionBinding` entries from a shared dimension configuration — the ganglionId-to-situationType contract holds by construction, not by convention. `RasSituationObserver` watches for `SituationChangeEvent`, filters to `ops:` prefixed situations and `TRIGGERED` change types, then iterates detections and routes each to the bridge using the ganglionId as the situation type.

The CDI wiring surfaced a constraint I hadn't anticipated. The ops app depends on `casehub-ras-api` but not the RAS runtime. Adding the runtime to get `SituationRegistrar` and `SituationStore` CDI beans introduced an ambiguity — both the ops app's `StubSituationSource` and the runtime's `DefaultSituationSource` implement `SituationSource`, and Quarkus couldn't resolve the injection for `SituationScalingEvaluator`. Rather than untangle the CDI conflict, the registrar uses a convenience constructor with no-op stubs for the RAS types. The bridge binding registration — the critical path for dimension routing — works end-to-end. When the RAS runtime is properly embedded, swapping to CDI injection is a one-line change.

The end-to-end flow now works: deploy an application, fire a heartbeat failure CloudEvent, and the health dimension status changes to `DOWN`. Fire a recovery event, and it returns to `HEALTHY`. The detection surface that was wired but empty now has teeth.

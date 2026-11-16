---
layout: post
title: "When a Service Becomes a Case"
date: 2026-07-30
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-ops]
tags: [service-lifecycle, domain-model, desired-state, operational-dimensions]
---

Every monitoring tool treats a deployed service as a collection of independent signals. Health checks in Datadog. Compliance status in a GRC tool. Scaling metrics in CloudWatch. CVE scan results in Snyk. Each tool sees one facet. None of them sees the service.

I've been thinking about this gap for a while. CaseHub already has the Case abstraction — a long-lived stateful entity with a blackboard, child cases, signals, and an audit trail. A deployed service sits in production for months or years. It has ongoing operational concerns. It gets sick, gets patched, scales up, scales down, drifts from its declared state, and eventually gets decommissioned. That's a lifecycle. That's a case.

The question I started with for issue #30 was whether the Case model could stretch to cover this. A service case opens on deploy, sits in WAITING indefinitely, and closes when the service is decommissioned. During its life, nine operational concerns run simultaneously: health monitoring, configuration drift, compliance posture, scaling, change management, security, maintenance, problem management, and decommission planning.

The answer, after a thorough brainstorming and adversarial design review, is that Case works — but the nine concerns need their own abstraction. They're not child cases (that over-engineers it — nine permanent WAITING children per service is heavy and misuses the lifecycle). They're not just blackboard sections either (that under-engineers it — the concept deserves its own type). They're *dimensions* — `OperationalDimension`, a first-class type in `ops-api` with its own status vocabulary, its own context section, and its own active-response tracking.

## Why dimensions matter to you

If you're managing services in production, the single most useful thing this model gives you is the answer to a question you currently can't ask without checking five tools: **what is the overall health of this service, right now, across every concern?**

Each dimension carries a status with domain-specific meaning. `HealthStatus.DEGRADED` and `ComplianceStatus.STALE_EVIDENCE` are both "not green" — but they demand completely different responses, and the system knows that. A generic traffic light loses this. The per-dimension status preserves it while still mapping to a `Severity` enum (OK, INFO, WARNING, CRITICAL) for aggregation. One service across fifty — sort by severity, drill into the dimension that's unhealthy.

The status model has a specific design choice worth understanding: it's a composite of the underlying condition AND whether someone is acting on it. `HealthStatus.DOWN` means broken and nobody knows. `HealthStatus.REMEDIATING` means broken and being fixed. At 3am with fifty services, that distinction is everything.

## How it works

A service case holds nine `OperationalDimension` instances, each with a `DimensionSection` — a namespaced view into the case's engine context. The `DimensionSection` delegates to `CaseHubRuntime.signal()` and `query()` — it's not a separate store. The engine's existing persistence handles durability. Context prefixes (`health.`, `security.`, `drift.`) enforce write isolation while allowing cross-dimension reads (problem management reads health incident history to detect patterns).

RAS ganglia — the detection layer — feed detections into dimensions through `GanglionBinding` declarations. A ganglion doesn't know about dimensions. It fires a detection. The `ServiceDetectionBridge` maps `(situationType)` to `(dimension, contextKey)` via the binding, writes to the target section, and triggers status recomputation. One detection can feed multiple dimensions: a CVE found by the scanner writes to both `security.vulnerabilityFound` and `compliance.controlViolation`, potentially spawning child cases in both dimensions simultaneously. The scanner fires once. The system routes it.

The `ServiceCaseDescriptor` builds the case definition with 14 bindings across all nine dimensions, using a name-prefix convention (`health:incident-response`, `drift:auto-reconciliation`, `security:vulnerability-patch`). No engine-api changes needed — the ops layer maintains a `Map<DimensionType, List<String>>` that maps dimensions to their binding names. The engine sees flat bindings; the ops layer knows the structure.

## What this code actually looks like

The type hierarchy is compact:

```
ops-api/io.casehub.ops.api.lifecycle
├── DimensionType          (enum: 9 values, each with a contextPrefix)
├── DimensionStatus        (interface: severity(), label(), isTerminal())
├── Severity               (enum: OK, INFO, WARNING, CRITICAL)
├── OperationalDimension   (type + status + section + activeResponses)
├── ServiceCaseContext     (serviceId + 9 dimensions + metadata)
├── ServiceHealth          (aggregate record for UI: worst-of-9 severity)
└── status/
    ├── HealthStatus           HEALTHY · DEGRADED · DOWN · INVESTIGATING · REMEDIATING
    ├── ConfigurationDriftStatus   IN_SYNC · DRIFTED · RECONCILING · RECONCILIATION_FAILED
    └── ... (7 more, each with domain-specific vocabulary)
```

`ServiceCaseContext.create()` initializes all nine dimensions at their default healthy status — no configuration, no per-service setup. Every service gets the same nine tiles. An operator's dashboard always has the same shape.

The deploy wiring is straightforward. `ApplicationLifecycleService.deploy()` now creates the service case alongside the existing `ReconciliationLoop`:

```java
UUID serviceCaseId = caseHubRuntime.startCase(ServiceCaseDescriptor.build(), ...);
app.engineCaseId = serviceCaseId;
serviceCaseRegistry.register(serviceCaseId, applicationId, app.name,
    ManagedServiceCategory.APPLICATION, metadata, writer, reader);
```

The `engineCaseId` field existed on `ApplicationEntity` but was never set. Now it is.

## The research behind the nine

Before designing anything, I searched across cloud-native lifecycle management, ITIL/ITSM, Kubernetes operator patterns, SRE automated remediation, process manager and saga patterns, and platform engineering. Nine scenarios emerged repeatedly across all of them: health monitoring leading to incident response, configuration drift leading to reconciliation, compliance drift leading to evidence re-collection, scaling events, upgrade and change management, security events (CVE, breach, secret rotation), maintenance windows, problem management (ITIL's pattern-detection discipline), and decommission or end-of-life.

The research confirmed something about the Case abstraction: what practitioners need is essentially a process manager. Not a saga, not a simple state machine — a process manager that maintains state across long periods, routes events to the right handler, coordinates multiple concurrent concerns, and has memory. CaseHub's Case model already IS that. The service case sits in WAITING, receives signals, and spawns child cases via context-change bindings. Each child case is a short-lived workflow — investigate, remediate, close.

The unique value CaseHub adds over existing tools is the memory. The 3rd incident on the same service escalates differently than the 1st — the `incidentCount` in the health section drives that. A service that auto-scales 10 times a day triggers the problem management dimension — the `scalingEventsThisWeek` counter crosses a threshold, and the pattern-detection ganglion fires a root cause investigation. The service remembers. The tools don't.

## What comes next

The domain model is implemented and tested — 19 new source files, 63 tests across `ops-api` and `app`. But the child cases are stubs (binding declarations exist, workers are no-ops). The next issues in the epic fill those in: #34 (incident-response), #37 (compliance-remediation), #38 (UI screens), and #47 (RAS ganglion integration, which wires real detections into the bridge). Issue #31 covers operational efficiency — partial branch loading so a health event doesn't have to hydrate the compliance section.

The open question is whether the nine-dimension model, once the UI is built, changes how people think about service operations. The hypothesis: seeing all nine concerns for one service on one screen — with status that distinguishes "broken" from "broken and being fixed" — is qualitatively different from checking five tools and mentally assembling the picture. That's what we'll find out.

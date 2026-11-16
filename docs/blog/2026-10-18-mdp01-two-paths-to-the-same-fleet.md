---
title: "Two Paths to the Same Fleet"
date: 2026-10-18
author: Mark Proctor
tags: [desiredstate, claudony, architecture, domain-design]
entry_type: note
subtype: diary
projects: [casehubio/casehub-ops]
series: issue-140-claudony-desiredstate-spi
---

Four days ago I wrote ADR-0002 explaining why claudony integration should go through `ApplicationDeploymentTarget` rather than a standalone provisioner. The reasoning was solid: `DefaultNodeProvisionerRouter` throws on overlapping `handledTypes()`, and the deployment module already had multi-target routing built for exactly this kind of backend variation.

Today I built the standalone provisioner anyway.

The key insight was that ADR-0002 answered a different question than issue #140 asked. ADR-0002 was about deploying a CaseHub application to claudony — one app at a time, through the deployment module's existing pipeline. Issue #140 was about declaring what an entire claudony fleet should look like and having the reconciliation loop enforce that state. "Deploy this app to claudony" and "these apps and pools should exist in the fleet" are different operational concerns with different callers, different YAML formats, and different lifecycle semantics.

Claude caught this during the decision review. The review surfaced ADR-0002 and flagged the apparent contradiction. That forced the design to articulate why the two patterns coexist rather than treating it as obvious. The result — ADR-0003 — establishes them as complementary: the deployment module routes individual `casehub_application` nodes to claudony via `deploymentTargetId`, while the claudony domain module manages the fleet declaratively with its own `claudony_app` and `claudony_pool` node types. Disjoint types, no router collision.

The pool design was the other decision worth recording. The user pushed back on my initial recommendation to keep pools as nested data inside the app spec. Pools as first-class graph nodes is the mature design — it gives you dependency ordering (app before pools), independent drift detection per pool, and the ability to add or remove pools without re-provisioning the entire app. The fact that claudony's API doesn't have standalone pool CRUD yet is a claudony issue to file, not a reason to compromise the ops domain model. We bridged it with read-modify-write on the parent app spec, which works because desiredstate reconciliation is sequential per tenant.

The implementation followed the container module's patterns closely. Sealed `ClaudonyNodeSpec` hierarchy for the two node types, direct drift detection in the adapter (batch query + per-node check), inline switch in the provisioner. The full SPI Quad — including `ThresholdFaultPolicy` with tier-3 escalation to a review node — landed in ten commits with 73 tests in the claudony module alone. The `SimulatingFleetClient` in the integration test maintains stateful fleet state in `ConcurrentHashMap`s, exercising the full green-field → provision → converge → drift → re-provision → suspend → resume → deprovision cycle.

Two follow-up issues filed against claudony: standalone pool CRUD endpoints (eliminates the read-modify-write bridge) and ETag support on app specs (enables optimistic concurrency). Two filed against ops: backfilling `@DesiredStateQualifier` on existing GoalCompilers and consolidating the three identical `SpecHashStore` copies into a shared utility.

The deeper pattern here is that "same backend, different concern" keeps showing up. The container module has `PodmanDeploymentTarget` (deployment module's bridge) alongside its own SPI Quad. Now claudony has `ClaudonyDeploymentTarget` alongside its own SPI Quad. The deployment module's multi-target routing handles "deploy this app to one of N backends." Domain modules handle "manage this backend's state declaratively." Both paths share the same REST client. Neither subsumes the other.

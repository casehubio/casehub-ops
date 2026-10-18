# 0003 — Claudony standalone desiredstate domain for fleet management

Date: 2026-10-18
Status: Accepted

## Context and Problem Statement

Issue #140 requires declarative fleet management for claudony — describing what apps and pools should exist and having the reconciliation loop enforce that state. ADR-0002 established `ClaudonyDeploymentTarget implements ApplicationDeploymentTarget` for deploying individual CaseHub applications to claudony. Fleet management is a different concern requiring its own GoalCompiler, ActualStateAdapter, and NodeProvisioner.

## Decision Drivers

* Fleet management needs its own YAML declaration format (`claudony-fleet.yaml`) distinct from deployment topology
* Node types `claudony_app` and `claudony_pool` are disjoint from deployment's `casehub_application` and `pool` — no `handledTypes()` collision
* Pools have different provisioning semantics from apps — sealed hierarchy required
* ADR-0002's `ApplicationDeploymentTarget` pattern remains valid for ad-hoc app deployment

## Considered Options

* **Option A** — Extend the deployment domain with claudony-specific handling
* **Option B** — Standalone desiredstate domain module with disjoint node types
* **Option C** — Replace `ClaudonyDeploymentTarget` with the new SPI Quad

## Decision Outcome

Chosen option: **Option B**, because fleet management and ad-hoc app deployment are different operational concerns with different node types, YAML formats, and lifecycle semantics.

| Concern | Pattern | Node types |
|---------|---------|-----------|
| Deploy a CaseHub app to claudony | ADR-0002: `ApplicationDeploymentTarget` | `casehub_application` |
| Manage claudony fleet declaratively | ADR-0003: standalone SPI Quad | `claudony_app`, `claudony_pool` |

### Positive Consequences

* Declarative fleet management with reconciliation loop enforcement
* Full SPI Quad including fault escalation and event-driven drift detection
* Clean domain boundary — claudony fleet is self-contained
* No `handledTypes()` collision — disjoint from deployment module

### Negative Consequences

* Dual paths to `ClaudonyFleetClient` — operational documentation must prevent declaring the same app in both patterns
* Pool provisioning uses read-modify-write on the parent app spec until claudony adds standalone pool CRUD endpoints

## Relationship to ADR-0002

ADR-0002 remains accepted and valid for its original scope: deploying `casehub_application` nodes to claudony via the deployment module's multi-target routing (`deploymentTargetId="claudony"`). This ADR adds a complementary pattern for a different use case, not a replacement.

## Links

* [ops#140](https://github.com/casehubio/casehub-ops/issues/140) — Claudony NodeProvisioner
* [ADR-0002](0002-claudony-integration-via-deployment-target.md) — Complementary pattern for ad-hoc app deployment

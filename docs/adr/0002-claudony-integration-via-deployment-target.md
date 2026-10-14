# 0002 — Claudony integration via ApplicationDeploymentTarget, not standalone NodeProvisioner

Date: 2026-10-14
Status: Accepted

## Context and Problem Statement

Issues #127 and #128 require integrating claudony's fleet API for deploying, suspending, resuming, and monitoring casehub_application nodes. The issue text described a standalone `NodeProvisioner` and `ActualStateAdapter`, but the existing `DeploymentNodeProvisioner` already handles `casehub_application`.

## Decision Drivers

* `DefaultNodeProvisionerRouter` throws `IllegalArgumentException` at construction time when two provisioners register overlapping `handledTypes()` — hard startup failure, not a preference conflict
* Same constraint applies to `DefaultActualStateAdapterRouter`
* Deployment module already has multi-target routing via `CaseHubAppProvisionHandler.resolveTarget(deploymentTargetId)`
* Issue #117 established `DeploymentApplicationNodeSpec.deploymentTargetId` for target selection

## Considered Options

* **Option A** — Standalone `ClaudonyNodeProvisioner` + `ClaudonyActualStateAdapter`
* **Option B** — `ClaudonyDeploymentTarget implements ApplicationDeploymentTarget`
* **Option C** — Classpath-exclusive modules (claudony replaces deployment)

## Decision Outcome

Chosen option: **Option B**, because it integrates with existing multi-target routing, avoids the type-collision constraint, and supports suspend/resume via SPI extension.

### Positive Consequences

* Zero type conflicts — claudony target coexists with in-process, podman, and kubernetes targets
* Existing provisioning pipeline (build, deploy, health check) works unchanged
* `deploymentTargetId="claudony"` selects the target — no new routing infrastructure needed
* SPI extension (`suspend`/`resume` on `ApplicationDeploymentTarget`) benefits all future targets

### Negative Consequences / Tradeoffs

* Suspend/resume required extending `ApplicationDeploymentTarget` with default methods — API surface grew
* `DeploymentNodeProvisioner.supportsStatefulLifecycle()` returns `true` unconditionally, though only casehub_application nodes to claudony actually support it

## Pros and Cons of the Options

### Option A — Standalone provisioner + adapter

* Good, because direct mapping from issue text
* Good, because clean separation — claudony module owns everything
* Bad, because overlapping `handledTypes()` causes hard startup failure — cannot coexist with `DeploymentNodeProvisioner`
* Bad, because would require removing `casehub_application` from the deployment module or mutual exclusion via classpath

### Option B — ApplicationDeploymentTarget implementation

* Good, because integrates with existing multi-target routing (`resolveTarget`)
* Good, because no type conflicts — multiple targets coexist
* Good, because suspend/resume added as default methods — backward compatible
* Bad, because claudony-specific logic split across claudony module (target) and deployment module (handler/provisioner delegation)

### Option C — Classpath-exclusive modules

* Good, because no type conflicts (only one module active)
* Bad, because deployment module handles 8 other node types (agent, channel, etc.) — can't remove it just for claudony
* Bad, because forces all-or-nothing deployment model selection

## Links

* [ops#127](https://github.com/casehubio/casehub-ops/issues/127) — Claudony NodeProvisioner
* [ops#128](https://github.com/casehubio/casehub-ops/issues/128) — Claudony ActualStateAdapter
* [ops#117](https://github.com/casehubio/casehub-ops/issues/117) — Deployment profiles and bootstrap model (established multi-target routing)

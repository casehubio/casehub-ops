---
layout: post
title: "One Topology, Three Profiles — From In-Memory to Kubernetes"
date: 2026-10-14
entry_type: note
subtype: diary
projects: [casehubio/casehub-ops]
tags: [deployment, bootstrap, desired-state, profiles, self-maintenance]
series: issue-117-deployment-profiles-bootstrap
---

# One Topology, Three Profiles — From In-Memory to Kubernetes

The ops app can now deploy and manage a full CaseHub installation from a single YAML topology declaration. The same `casehub-topology.yaml` works whether you're running everything in a single JVM on your laptop or deploying containers to a Kubernetes cluster. The profile selects the provisioner backend. The topology stays the same.

I'd been circling this problem since the qhorus mesh deployment discussion. The mesh is just a container node in the topology YAML — but that raised the real question: how does the full installation bootstrap from day zero? And once it does, how does the same declaration scale from a developer's inner loop to production?

**The key design decision was reuse.** Platform services like Qhorus, Eidos, and Engine aren't a new type — they're `CaseHubApplicationSpec` instances, the same node type we already had for deploying CaseHub applications. This was a recommendation from the design review, and it's the right call. The existing `CaseHubAppProvisionHandler` already orchestrates the full lifecycle: build pipeline, deployment target, health check. Adding three profiles meant implementing three `ApplicationDeploymentTarget` backends, not a parallel architecture.

The three targets:

- **`InProcessDeploymentTarget`** — for local-dev. Verifies that CDI beans are resolvable on the classpath. If the service JAR is present, "deployment" is a verification that it's already running in the same CDI container. If it's absent, you get an immediate, clear error — not a retry loop.

- **`PodmanDeploymentTarget`** — for local-distributed. Delegates to the existing `PodmanClient` for container lifecycle: pull image, create container, start, stop, remove. Real network boundaries, real protocol negotiation, real failure isolation — but still on your machine.

- **`KubernetesDeploymentTarget`** — for enterprise. A stub today, referencing #126 for the fabric8 wiring. The interface is ready; the implementation is a future session.

The per-node override is where things get interesting. The deployment profile sets a default backend, but individual nodes in the YAML can override it. A developer running `local-dev` might still want Postgres in a container — `deploymentTarget: podman` in the node's spec block forces that one node to use containers while everything else stays in-process. This mirrors the infra module's `backendId` pattern: `DeploymentApplicationNodeSpec` wraps `CaseHubApplicationSpec` with routing metadata, and `DeploymentNodeSpecFactoryProvider` extracts it from the YAML spec map — exactly how `InfraWrappingFactory` handles `backendId`.

The `existing:` field handles the inverse case. When a node declares `existing: https://qhorus.internal:8443`, the system probes the URL instead of provisioning. If reachable, the reconciliation loop skips it. If unreachable, it doesn't try to provision a replacement — the operator declared this service is externally managed, so it raises a fault for human attention. "Use existing or deploy if missing" is the core principle, and the reconciliation loop's existing contract (`ActualStateAdapter` reports `PRESENT`/`ABSENT`, `TransitionPlanner` plans transitions) handles it without new abstractions.

**Bootstrap has two phases.** Phase 1 is the seed: the ops app starts, loads the topology, and provisions everything else — infrastructure first (via `CrossDomainCompositionEngine` ordering), then platform services. Phase 2 is self-awareness: once all dependencies report healthy, the `BootstrapPhaseCoordinator` transitions from `SEED` to `SELF_AWARE`, and the `SelfMaintenanceMonitor` activates. The ops app is already a node in the topology — during Phase 1, the adapter reports it as `PRESENT` (because it's the running seed). After transition, the monitor watches for version upgrades and orchestrates the replacement: provision a new instance, health-check it, swap traffic, let the old instance exit gracefully. The running instance never kills itself — it provisions its replacement and steps aside.

The CDI wiring taught us something. I'd initially made all three deployment targets `@ApplicationScoped` CDI beans — they coexist on the classpath and get resolved by `targetId()` at dispatch time. But Quarkus's CDI container saw them during augmentation and threw ambiguity errors: which one is the `@Default` `ApplicationDeploymentTarget`? The fix was removing `@ApplicationScoped` entirely — these are POJOs instantiated explicitly during bootstrap, not auto-discovered beans. Profile-based CDI activation (Quarkus `@IfBuildProfile` or `@LookupIfProperty`) is the right answer for production, and that's deferred to when the bootstrap sequence moves from explicit instantiation to CDI-managed lifecycle.

What this opens up: the topology YAML is now the single source of truth for a CaseHub installation. A new deployment is a YAML file and a profile selection. The reconciliation loop handles the rest — provision what's missing, monitor what exists, self-heal when things drift. The enterprise target is the obvious next step, but the architecture doesn't change — it's a new `ApplicationDeploymentTarget` implementation, not a new design.

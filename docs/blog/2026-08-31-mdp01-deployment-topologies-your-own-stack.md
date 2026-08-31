---
title: "Deployment topologies: when your own stack is the answer"
date: 2026-08-31
author: mdp
entry_type: note
subtype: diary
projects: [casehub-ops]
tags: [desired-state, yaml, deployment, architecture, goap]
issue: "#74"
---

I started this session wanting to know: can the desired-state YAML frontend handle
real-world deployment architectures? Not just "declare three K8s deployments" — the
full spectrum. A single-service blog on a Raspberry Pi. A multi-tier e-commerce
storefront behind a load balancer. A microservices trading platform replicated across
three availability zones. An event-driven IoT telemetry pipeline. A service mesh with
auto-injected sidecar proxies.

The first thing we did was map the problem space. Five application architectures
(single service, multi-tier, microservices, event-driven, sidecar/mesh) crossed with
four infrastructure topologies (single node, load-balanced cluster, HA multi-AZ,
multi-region active-passive). That gives a 5×4 matrix — 20 theoretical intersections,
14 that are meaningful. Each one mapped to a recognisable domain: hospital records for
HA multi-AZ multi-tier, equities trading for HA microservices, IoT telemetry for
event-driven. Real names for real patterns. If we're going to use these as tutorials,
they need to feel like something a DevOps engineer would actually build.

The original plan was to write a `TopologyGoalCompiler` — a new Java class that
understands what "multi-tier" means and generates the right nodes and dependencies.
Then Claude audited the `casehub-desiredstate-yaml` codebase using IntelliJ, and the
plan changed.

The YAML frontend already has everything. Modules for composable patterns. ForEach
for stamping services across availability zones. Invariants for structural validation
("every payment processor must have fraud detection"). Rules for auto-wiring ("add a
sidecar proxy for every deployment"). Lifecycle phases for ordered rollout. Variable
substitution for per-environment config. A pluggable `NodeSpecRegistry` for type
discovery. All of it working together in the webapp tutorial examples — an e-commerce
pipeline with conditional gift-wrapping, auto-wiring notification rules, and
multi-warehouse forEach shipping.

No new compiler needed. A deployment topology is a composition of existing YAML
primitives. The gap narrowed from "build a topology management system" to "add five
Java records and four YAML modules."

The five records: `LoadBalancerSpec`, `ServiceMeshControlPlaneSpec`, `SidecarProxySpec`,
`DnsFailoverSpec`, `DataReplicationSpec`. Each extending the `InfraNodeSpec` sealed
hierarchy. The four modules: `load-balancer.yaml`, `ha-multi-az.yaml`,
`service-mesh.yaml`, `multi-region.yaml` — pure YAML, shipping inside
`casehub-ops-infra.jar`.

But the insight that matters most isn't about YAML expressiveness. It's about what
happens after compilation.

The desired-state system already has a `TransitionPlanner` that handles day-to-day
reconciliation — diff desired against actual, provision what's missing, deprovision
what's orphaned. Seconds-timescale. That handles steady state. But what about
topology *migrations*? Moving from single-node to HA multi-AZ isn't a diff problem.
You can't just add three AZ node pools simultaneously. You need ordered steps:
provision new nodes, replicate data, configure the load balancer, verify health,
migrate traffic, decommission the old setup. Each step has preconditions. Some need
human approval.

That's a GOAP problem. And the engine already has a `GoapPlanner` — full A* search
with preconditions, effects, costs, soft preconditions, backward pruning, and forward
simulation. It was built for case orchestration, but topology migration is structurally
identical: a world state, a goal state, and a set of actions with ordering constraints.

Layer it up: YAML declares the topology. `TransitionPlanner` handles drift. `GoapPlanner`
handles migrations. Engine cases orchestrate with human approval gates. Service lifecycle
(Chapter 5's nine-dimension model) monitors the running services.

Each component handles a different timescale. Seconds for drift. Minutes for migration.
Hours for approval-gated orchestration. Days for operational monitoring. Nothing new
needed — just wiring. CaseHub's own stack IS the deployment management engine.

The decision review caught real issues. The `TransitionPlanner` and `GoapPlanner` need
a coordination mechanism — you can't have the planner naively deprovisioning nodes that
a GOAP migration hasn't finished setting up. Multi-region active-passive spans two
independent reconciliation loops with no existing cross-cluster coordination. Both
are deferred to later phases, but they're honest gaps, not handwaving.

What shipped today: a research document, a design spec through three adversarial review
rounds, an implementation plan with 14 tasks across 6 batches, epic #74 with eight
child issues, ARC42STORIES Chapter 6, and slot 165 with both `casehub-desiredstate`
and `casehub-ops` ready for implementation.

The first implementation task is #75 — `NodeSpecFactory` SPI in casehub-desiredstate.
It bridges the `InfraNodeSpec`/`NodeSpec` type gap so the YAML frontend can create
infrastructure nodes without a custom compiler. After that lands and releases, the
ops-side work begins: type extensions, factory wiring, topology modules, 14 exemplars,
and a three-layer test pyramid.

The topology exemplars are simultaneously integration tests and tutorial material.
That's the bet: YAML-first tutorials with real domain language, verifiable by running
`mvn test`, that show how CaseHub deploys anything from a Ghost blog to a multi-region
banking core. If the YAML reads well enough to learn from and compiles correctly enough
to test against, we've found the right level of abstraction.

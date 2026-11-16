---
layout: post
title: "The Operations Arc — From Deployed to Self-Healing in Four Levels"
date: 2026-10-18
entry_type: article
subtype: diary
projects: [casehubio/casehub-ops]
tags: [desired-state, deployment, operations, reconciliation, observability, llm-fleet]
series: issue-134-operations-arc-levels-05-08
---

# The Operations Arc — From Deployed to Self-Healing in Four Levels

Deploying an LLM fleet is the easy part. You write the YAML, the platform reconciles desired against actual, and your three agents are running. Levels 01 through 04 got us here — from a single app to a fleet of differentiated LLM instances, each with its own identity, archetype, and domain vocabulary.

But deployment is day zero. The real question is: what happens on day one?

Config changes. Lifecycle operations. Fleet mutations. Health degradation and recovery. These are the things that actually burn operator time, and they're the things most platforms leave as an exercise for the reader. Levels 05 through 08 are about making all of them declarative — the same YAML-and-reconcile pattern that handles initial deployment also handles everything that comes after.

![Level progression from Foundation Arc to Operations Arc](images/level-progression.svg)

## Config drift: change the YAML, not the fleet

Level 05 starts with the simplest operational scenario. Your fleet is running. The analyst's configuration needs to change — `capability: "deep"` becomes `capability: "analytical"`, `domain: "analysis"` becomes `domain: "data-analysis"`. In a traditional setup, you'd write a script, target the right instance, push the update, verify it took, and hope you didn't accidentally touch the other two.

With desired-state management, you edit the YAML. The platform diffs the new desired state against the running actual state, identifies that exactly one node changed, and reconciles only that node.

![Selective reconciliation — only the analyst node receives an update](images/selective-reconciliation.svg)

The integration test for this is specific: deploy the full fleet from Level 04's YAML, then re-reconcile from Level 05's YAML. Assert that the analyst's config was updated. Assert that the coder and assistant configs are untouched. Then exercise the fleet's LLM endpoints to verify the analyst's behaviour actually reflects the new configuration — not just that the config was pushed, but that the identity changed.

This is the pattern that matters. Not "did the API call succeed" but "does the LLM now behave as an analytical investigator in the data-analysis domain, and can you tell the difference?"

## Lifecycle: suspend without destroying

Level 06 addresses a gap that catches people by surprise. You want to take an instance offline temporarily — maintenance, cost management, testing a degraded fleet. Most deployment targets give you deploy and undeploy. There is no middle ground. Taking an instance down means losing its state and registration; bringing it back means a full redeploy.

Claudony supports stateful lifecycle: suspend pauses processing while preserving registration, and resume brings it back. The status transitions are explicit — `HEALTHY → SUSPENDED → HEALTHY` — and the platform can distinguish "suspended by operator" from "down due to failure." This distinction matters enormously for automated recovery: a suspended instance should not trigger a heal cycle.

The YAML for Level 06 is identical to Level 04. Lifecycle is an operational concern, not a topology change. The test exercises the real claudony API: deploy all three fleet instances, suspend the analyst, verify its status is `SUSPENDED`, verify the other two are still `HEALTHY`, resume the analyst, verify it returns to `HEALTHY`.

## Topology mutations: prune before grow

Level 07 is where the runtime's dependency awareness earns its keep. The fleet needs to change shape — the assistant is no longer needed, a researcher is. In an imperative world, you'd write the deregister call, write the register call, hope the ordering is right, and handle the error cases yourself.

In the declarative model, you change the data. The fleet CSV that drives the `forEach` expansion drops the assistant row and adds a researcher row. The platform diffs the graphs, identifies one removal and one addition, and plans the transition with dependency-aware ordering.

![Prune before grow — remove old nodes before adding new ones](images/prune-before-grow.svg)

The ordering is not arbitrary. The `TransitionPlanner` in the runtime always prunes before growing — removals execute before additions. If both the old and new node share a dependency slot or a resource constraint, growing first would fail. The planner enforces this automatically; the YAML author never needs to think about it.

The integration test deploys Level 04's fleet, then reconciles to Level 07's topology. It verifies the assistant is deregistered, the researcher is registered, and the surviving nodes (analyst, coder) are undisturbed. Then it exercises every LLM endpoint — including the new researcher — to verify the fleet is fully operational in its new shape.

## Fleet observability: detecting, classifying, and recovering

Level 08 brings the full signal chain. A running fleet needs continuous observation — not just "is it up" but "what kind of not-up is it?" The distinction between `HEALTHY`, `DEGRADED`, `SUSPENDED`, and `DOWN` determines what happens next.

The signal chain runs end-to-end:

| Signal | Status | Health Check | Action |
|--------|--------|-------------|--------|
| Health probe passes | `PRESENT` | `Healthy` | No action |
| Health probe shows degradation | `DRIFTED` | `Unhealthy(reason)` | Flag for attention |
| Instance suspended by operator | `SUSPENDED` | `Unhealthy(reason)` | No auto-recover |
| Instance down | `ABSENT` | `Unhealthy(reason)` | Reconcile to restore |

The integration test walks a fleet through the full cycle: healthy → degraded → down → recovered. Each transition is exercised against the real claudony API. Each status classification is asserted. And after recovery, the LLM fleet is exercised again to verify it is genuinely operational — not just showing a green status while returning garbage.

## The real point

These four levels are not just tutorials. Each YAML file is also an integration test that runs against real deployed instances. Level 05 proves the platform can selectively reconcile a config change. Level 06 proves it can manage lifecycle without data loss. Level 07 proves it can safely mutate a running topology. Level 08 proves it can detect degradation and recover.

The day-zero-to-operational path is: write a YAML, deploy, and from that moment on, every operational concern — drift, lifecycle, mutation, health — is handled through the same reconcile loop. No scripts. No imperative playbooks. No "that's an exercise for the reader." Change the desired state, and the platform converges.

This is what accountability-native operations looks like. Every deployment is audited. Every config change is a reconciliation event with a diff summary. Every lifecycle transition is a status record. The platform does not just deploy your fleet — it accounts for everything it does to your fleet, and everything your fleet does in response.

---
layout: post
title: "Config, Not Code"
date: 2026-11-05
entry_type: note
subtype: diary
projects: [casehubio/casehub-ops]
tags: [runtime, deployment, topology, architecture, desiredstate, service-boundaries]
series: issue-226-universal-runtime-rewrite
---

# Config, Not Code

This is the third entry in the [universal runtime rewrite series](2026-11-03-mdp01-one-image-to-rule-them-all.md). The first covered the initial insight — one image replaces four fictional ones. The second covered the [test dimension framework](2026-11-04-mdp01-the-test-is-the-tutorial.md) that proves each level works. This one covers what happens when you apply both across the remaining deployment-domain levels.

## The Sweep

Levels 05 through 09 each had the same problem the earlier levels had: fictional Docker images for services that don't exist as standalone deployables. Manifest, eidos, engine, my-llm-app — four to seven nodes per topology, each referencing an image nobody ever built.

The rewrite for each level is the same mechanical exercise: collapse the tightly coupled modules into a `platform` node, keep fleet members or service-boundary nodes as separate entries, point everything at `casehubio/casehub-runtime:latest`, and let config differentiate what each container does.

![Node count reduction across Levels 05–09 — fictional services collapse to real architecture](images/level-sweep-node-reduction.svg)

Five levels, five rewrites, five sets of compilation tests and dimension tests. The sweep took less time than rewriting Level 01 alone. That's the real point: once the packaging and testing infrastructure exist, topology changes become configuration changes. The YAML files are smaller. The tests are more precise. The containers run real code.

## Where the Design Decisions Actually Live

Most of these levels are structurally identical. Levels 04 through 08 all have the same shape: one platform node plus a data-driven fleet of LLM instances. What makes each level different isn't the graph structure — it's what the level tests against that structure:

- **Level 05** drifts the analyst's config (capability changes from `deep` to `analytical`) and verifies that the reconciliation loop detects and fixes only the drifted node.
- **Level 07** swaps the assistant for a researcher and verifies the transition planner prunes the old node before growing the new one.
- **Levels 06 and 08** add lifecycle and observability concerns on top of the same graph.

The structural sameness is a feature. It means the deployment topology is a stable foundation that operational concerns layer onto without changing the graph. Drift detection, lifecycle management, fleet observability — these are runtime behaviours, not topology shapes. The YAML doesn't change because the YAML was already right.

## Level 09: Where Boundaries Get Interesting

Level 09 breaks the fleet pattern. It's a multi-service topology: platform, ledger, qhorus, and a chat-viewer component. The original had seven nodes — manifest, eidos, engine, ledger, work, qhorus, and chat-viewer — each with its own fictional image.

The rewrite collapsed seven to four. But the interesting question isn't the count. It's why four and not one.

![Service boundary decisions — co-location vs. separation depends on state sovereignty](images/service-boundary-decisions.svg)

The platform node co-locates manifest, eidos, engine, and work. These modules call each other per-request. An LLM query hits the engine, which consults eidos for entity context, which resolves against the manifest. Splitting them across containers adds network hops for no architectural benefit — they share a request lifecycle.

Ledger stays separate because the audit trail must be independent of the platform's lifecycle. If the platform crashes, the audit record of what it was doing before the crash needs to survive. That's a genuine state-sovereignty argument.

Qhorus stays separate because channel and protocol state is sovereign. A channel exists independently of the applications that participate in it. Messages are append-only records that belong to the channel, not to the sender.

Chat-viewer stays separate because it's a UI component with no shared state — it reads from qhorus and renders conversations. It could co-locate with qhorus, but the dependency direction is one-way: chat-viewer depends on qhorus, qhorus knows nothing about chat-viewer. Keeping them separate means the chat viewer can scale, version, and deploy independently.

The decision rule that fell out of this: modules that call each other per-request belong in one container. Modules that manage sovereign state get their own container. Same image in both cases — only the config differs.

## The Wrong Node Type

Level 08 surprised me. The original topology used `claudony_app` node types instead of `casehub_application`. Every other level in the 03-09 range used `casehub_application`. The claudony type belongs to a different domain entirely — it's the SPI for the claudony fleet management system, not the deployment domain.

This was a quiet bug in the topology catalogue. The compilation tests would have caught it if Level 08 had any, but it didn't — it was one of the levels that existed as YAML with no test coverage. The rewrite fixed the node type, added compilation tests, and now it's consistent with the rest of the deployment domain.

## Where the Pattern Stops

The deployment-domain levels (03–09) are done. What remains are the claudony-domain levels — 10, 12, and 14 — plus Level 15 (governed interaction with channel nodes).

These aren't a packaging change. Claudony has its own node types (`claudony_app`, `claudony_pool`), its own SPI implementations, its own fleet API. The universal runtime image works as a deployment target for claudony-managed containers, but the topology YAML for claudony levels declares nodes in the claudony domain, not the deployment domain.

This is a genuine architectural boundary. The previous rewrites asked: "how do we package this?" The claudony levels ask: "does the universal image participate in this domain's lifecycle, or is it just what gets deployed?" That's a different design question, and it needs its own answer.

The deployment domain is done. The config-not-code pattern works. Now the question is whether it extends across domain boundaries, or whether some domains are structurally different enough to need their own approach.

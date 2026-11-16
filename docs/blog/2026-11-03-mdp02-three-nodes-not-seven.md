---
layout: post
title: "Three Nodes, Not Seven"
date: 2026-11-03
entry_type: note
subtype: diary
projects: [casehubio/casehub-ops]
tags: [topology, architecture, desiredstate, service-boundaries, deployment, microservices]
series: issue-226-universal-runtime-rewrite
---

# Three Nodes, Not Seven

The Level 03 topology used to have seven nodes. Manifest, eidos, engine, ledger, work, qhorus, my-llm — each with its own image, its own lifecycle, its own row in the dependency graph. It looked like a proper microservices architecture. It was fiction.

None of those images existed. The topology described a world where each CaseHub module ships independently. That world doesn't exist. Engine and work call each other on every request. Eidos is a framework, not a service. Manifest is a classpath resource loader. Splitting them into separate containers doesn't add resilience — it adds network hops between things that were never meant to be apart.

The universal runtime image from Level 01 and Level 02 already solved the packaging problem. One image, all modules on the classpath, config determines personality. But consolidating Level 03 from seven nodes to one would be wrong too. The case management topology has real service boundaries. The question is which ones.

## The Boundary Test

Not every module boundary is a service boundary. A code boundary separates concerns for developers — different packages, different responsibilities, different change frequencies. A service boundary separates deployment units for operators — different containers, different scaling needs, different failure domains.

![When does a module deserve its own node?](images/l03-boundary-decision.svg)

Two questions filter the seven nodes down to three:

**Does it manage its own state?** Engine doesn't — it reads from eidos and writes through work. Work doesn't — it persists through the same database as engine. Manifest doesn't — it loads classpath resources and caches them in memory. None of these have state worth isolating.

Ledger does. It's an append-only audit trail. Its state is the record of everything that happened, and that record must survive regardless of what happens to the rest of the system. Qhorus does. It manages normative protocol state — obligations, commitments, channel configurations. Protocol state has its own lifecycle independent of the cases that created it.

**Can it fail independently?** If the engine crashes, work is useless — they're coupled. If ledger crashes, the platform keeps running; it just stops producing audit records. If qhorus crashes, the platform can still create cases; it just can't manage protocol state for them. These are genuine failure domains.

## What Changed

![Level 03 before and after — seven fictional nodes become three real ones](images/l03-seven-to-three.svg)

The rewrite is mechanical but the reasoning behind it isn't. Seven nodes collapsed to three:

**Platform** runs engine, work, eidos, manifest, and the LLM capability. One container, one process, one failure domain. The `role: platform` config activates the full CaseHub platform stack. The `llm.capability` and `llm.identity-source` config keys tell the runtime how to wire up the LLM — the same keys that used to live on a separate `my-llm` node now co-locate naturally because the LLM is just another capability of the platform, not a separate service.

**Ledger** runs the audit trail. `role: ledger`. It depends on platform because it records what platform does, but it's a separate container because audit integrity requires isolation. A bug in the platform should produce audit records, not corrupt them.

**Qhorus** runs the normative protocol layer. `role: qhorus`. It depends on both platform and ledger — on platform because it manages protocol state for cases that platform creates, on ledger because every normative state transition must be audited. When a commitment is fulfilled or an obligation expires, the audit trail records it independently of the protocol engine that evaluated it.

## Why This Matters Beyond CaseHub

The microservices default is to split by code module. You have five packages, you create five services. This is backwards. You should start with one deployment unit and earn the right to split. The cost of a network boundary is real — latency, partial failure modes, distributed consistency, operational overhead per service. That cost needs a benefit that exceeds it.

The benefits that justify separation:

**Independent scaling.** If one component needs ten replicas while another needs one, they should be separate. Platform, engine, and work scale together — they process the same requests. Ledger could scale independently if audit write volume diverges from request volume.

**Independent failure domains.** If one component's failure shouldn't take down another, separate them. This is the strongest argument for ledger isolation: audit data is evidence, and evidence shouldn't be lost because the system it's monitoring had a bad day.

**Independent deployment cadence.** If one component ships weekly while another ships daily, coupling their deployment creates friction. But at this stage of CaseHub — a research platform with no production users — deployment cadence is uniform: everything ships together.

**Independent state lifecycle.** Protocol state (obligations, commitments) has a different lifecycle than case state. An obligation can outlive the case that created it. A commitment can be fulfilled after the originating session ends. Qhorus manages this lifecycle, and it needs to persist state independently of whether the platform is running.

The seven-node topology had none of these justifications. It was module decomposition masquerading as service architecture.

## The Test Structure Follows the Architecture

The topology tests mirror the three-level progression:

Level 01 proves one universal image can deploy and serve. Level 02 proves two roles (platform + ledger) can coexist with correct dependency ordering. Level 03 proves three nodes with genuine service boundaries deploy in the right order, execute successfully, and produce an idempotent plan — adding a node and then replanning produces no changes.

The compilation tests verify the graph structure: three nodes, all `casehub_application` type, correct dependencies (ledger depends on platform, qhorus depends on both), universal image with role-specific config. The reconciliation tests verify the transition plan: three additions from empty, platform before ledger before qhorus, all outcomes succeeded, idempotent after execution.

These aren't deployment smoke tests. They verify that the desired-state engine produces correct plans from the topology descriptor and executes them in the right order. The topology YAML is a specification. The tests prove the specification compiles and executes correctly.

## What Level 03 Opens Up

With qhorus deployed as a genuine service boundary, the path to normative protocol testing opens. The acceptance test already deploys the three-node topology to real infrastructure and exercises the LLM capability through the platform node. The next step — a separate issue — is exercising qhorus at runtime: creating obligations, fulfilling commitments, verifying protocol state transitions across the platform-qhorus-ledger chain.

This is the first topology level where the deployment architecture isn't just "put the software somewhere." It's "put the software in the right places, for the right reasons, with the right boundaries." That's the difference between running containers and operating a system.

Three nodes, not seven. Not because three is a magic number, but because three is how many genuine service boundaries the case management stack has. When a fourth boundary emerges — and it will — it'll earn its node the same way qhorus did: by demonstrating independent state, independent failure, and a lifecycle that doesn't belong to anyone else.

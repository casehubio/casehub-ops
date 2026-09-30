---
title: "The YAML everything question"
date: 2026-09-30
author: mdp
tags: [yaml, desired-state, architecture, design]
entry_type: note
subtype: diary
---

Three pieces of work today, each at a different altitude.

The ground floor was mechanical: `ApplicationEntity.findById()` doesn't exist because nobody converted the JPA entities to Panache. Twelve compilation errors across six files, all saying the same thing. Three entities, three `extends PanacheEntityBase` declarations, three static finder methods. Fixed, landed, closed. The kind of issue that blocks everything downstream and takes twenty minutes to resolve once you look at it.

The mid-level was `PodmanWatchManager` — the active event stream for the container domain. `PodmanClient.events("container")` opens an NDJSON stream from Podman's `/events` endpoint; the watch manager translates container lifecycle actions into `StateEvents` and feeds them to `ContainerEventSource`. Start means present. Die means drifted. Remove means absent. Health status maps both ways. Exponential backoff on disconnect. The same pattern as `K8sWatchManager`, just for a different runtime. Fourteen tests, all passing. Satisfying work — the kind where the pattern is established and you're extending it into a new domain.

The top floor was the question I'd been circling: what does a 100% YAML CaseHub application actually look like?

Not the deployment topology — we have that. Not the graph runtime — the `YamlGoalCompilerFactory` handles nodes, dependencies, forEach, modules, lifecycle phases. The question was bigger: can someone define agents, cases, situations, blocks, and infrastructure topology in YAML, point a runtime at it, and have a working application with no Java visible anywhere?

The answer is yes, with gaps. Twenty gaps across eight repos, to be specific. We mapped them all. The most interesting finding: fsitrading is closer than I expected. It already has a rich `casehub-deployment.yaml`, five playbooks, resolution patterns, simulation config — all YAML. Only six Java files stand between it and "no visible Java." Six registrar classes that do nothing but declare agent descriptors, situation definitions, and event types — exactly the kind of thing a YAML section replaces.

The design decisions that fell out of the conversation:

A custom Maven `casehub` packaging type. The POM declares what you depend on (fifteen lines), the YAML declares what you are. Flat single-file for demos, convention-based directories for production. Same schema either way — split when it grows, no migration.

Scaffold as the runtime, not Quarkus directly. A YAML-only app doesn't compile anything. It describes an application; it doesn't build one. Scaffold reads the directory, registers agents, compiles topology, starts reconciliation. Quarkus is an internal implementation detail the YAML author never sees.

Cases as state machines with blocks as the unit of work. Each block is an agent task or a human decision point with typed inputs and outputs, optional approval gates, and repeat semantics. Stages transition by fixed targets or by decision maps keyed on action outcomes. The full fsitrading market-investigation case — detect, review, respond, monitor — expressed as nested YAML with no ambiguity about what runs where and who decides what.

Three demo milestones. "Hello CaseHub" proves the Maven plugin and auto-bootstrap. "Trading desk" proves situations, playbooks, and multi-agent. "Full platform" proves blocks, channels, and CBR. Ten progressive examples in `casehub-examples/getting-started/`, each introducing exactly one concept.

The critical path is Phase 1: auto-bootstrap, Maven plugin, case definition loading, and scaffold app discovery. Everything else follows from those four.

I filed issues across engine, ras, qhorus, and casehub-examples. The spec is committed with thirteen sections covering schema, project structure, Maven packaging, CLI, testing, migration, gap analysis, and critical path. It's a project management document as much as a design doc — the next session needs to be able to pick up any gap and make progress without re-deriving the context.

Next: audit the consumer and contributor guides across all core repos for drift, then write the executive brief from accurate source material. The guide audit probably needs a multi-repo slot to avoid branch-mismatch headaches.

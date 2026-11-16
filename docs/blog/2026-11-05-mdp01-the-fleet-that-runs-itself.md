---
layout: post
title: "The Fleet That Runs Itself"
date: 2026-11-05
entry_type: note
subtype: diary
projects: [casehubio/casehub-ops]
tags: [fleet, runtime, horizontal-scaling, forEach, topology, desiredstate, testing, dimensions]
series: issue-226-universal-runtime-rewrite
---

# The Fleet That Runs Itself

*Continues from [The Test Is the Tutorial](2026-11-04-mdp01-the-test-is-the-tutorial.md).*

The universal runtime solved a packaging problem: four fictional Docker images became one real one. The dimension framework solved a verification problem: monolithic test classes became composable capability proofs. But both operated on single containers. One node, one personality.

Real systems don't work that way. You don't deploy one LLM and call it a platform. You deploy a fleet — analyst, coder, assistant — each configured for a different job. Same binary. Different behaviour. And the test needs to prove that the config actually produces different behaviour, not just that three containers started.

## What forEach Does

The desired-state YAML has a construct called `forEach`. It stamps out N instances of a node from a data source. The data is inline CSV:

```yaml
data:
  llm-fleet:
    inline: |
      name:STRING,role:STRING,capability:STRING
      analyst,analyst,deep
      coder,developer,fast
      assistant,assistant,balanced
```

The node template references each row's fields:

```yaml
my-llm:
  type: casehub_application
  dependsOn: [platform]
  forEach:
    as: llm
    in: llm-fleet
  spec:
    applicationId: my-llm-${each.llm.name}
    image: casehubio/casehub-runtime:latest
    config:
      llm.role: ${each.llm.role}
      llm.capability: ${each.llm.capability}
```

The compiler expands this into three concrete nodes: `my-llm.analyst`, `my-llm.coder`, `my-llm.assistant`. Each gets its own applicationId, its own config values, its own entry in the dependency graph. The platform node deploys first (it has no forEach — just one instance with `role: platform`). Then all three fleet members deploy in parallel, each depending on platform.

This is how Kubernetes Deployments work in practice — same container image, different environment variables — except here the desired-state compiler handles the expansion and the reconciliation loop handles the ordering. The YAML author writes the pattern once. The runtime stamps it out.

## What Changed in the Topology

![Level 04 before and after — six nodes with fictional images become four nodes with one real image](images/fleet-topology-before-after.svg)

The old Level 04 had six nodes. Three were fictional infrastructure — manifest, eidos, engine — each pretending to be a separate Docker image that didn't exist. The fourth fictional image, `my-llm-app`, got stamped out three times by forEach. Six nodes, four images, zero of them real.

The rewrite collapses the infrastructure into a single `platform` node running `casehub-runtime:latest`. The three fleet members run the same image with different config. Four nodes, one image, all of them real. The dependency graph is simpler and honest: fleet members depend on platform, not on a chain of fictional services.

## Why Config-Driven Roles Matter

![One universal runtime image deployed as three fleet members with different role configs](images/fleet-pattern-one-image-three-roles.svg)

The interesting part isn't that we removed fictional images. It's what the fleet pattern demonstrates about how real platforms scale.

A traditional approach would be: build `casehub-analyst:latest`, `casehub-coder:latest`, `casehub-assistant:latest`. Three Dockerfiles. Three CI pipelines. Three version matrices. When you patch a security vulnerability in the base image, you rebuild three times. When you add a module, you update three dependency lists. The images drift apart over time because nobody remembers which one got the last fix.

The universal runtime inverts this. One image contains all the modules. Config determines which capability surfaces. The analyst gets `llm.capability=deep` — it allocates more context, reasons longer, trades latency for depth. The coder gets `llm.capability=fast` — it optimises for turnaround. The assistant gets `llm.capability=balanced`. Same binary, three personalities.

This is the pattern that makes fleet management tractable. When every container runs the same code, rollouts are atomic. A security patch is one image push, not three. Canary deployments compare the same binary with different configs, which isolates config-driven regressions from code-driven ones. The operational surface area of the fleet is the operational surface area of one container, multiplied only by config.

## FleetDimension: Proving Distinct Behaviour

The dimension framework from Level 03 needed one extension: it had to know about multiple containers. The original `DimensionTestExtension` stored one container name in `DimensionState`. The fleet test deploys three.

The fix was two lines. Store all container names alongside the singular one:

```java
dimensionState.put("containerName", specs[0].name());
List<String> allNames = Arrays.stream(specs)
        .map(ContainerSpec::name).toList();
dimensionState.put("containerNames", allNames);
```

Existing dimensions that read `containerName` (singular) are unaffected. `FleetDimension` reads `containerNames` (plural) and exercises each one: health-check, submit a prompt, verify the response structure, assert that each fleet member's config produced distinct behaviour.

The test class is twelve lines of declaration:

```java
@DimensionTest(
    containers = {
        @ContainerSpec(name = "fleet-analyst",
            image = "localhost/casehubio/casehub-runtime:latest",
            env = {"llm.capability=deep", "llm.role=analyst"}),
        @ContainerSpec(name = "fleet-coder",
            image = "localhost/casehubio/casehub-runtime:latest",
            env = {"llm.capability=fast", "llm.role=developer"}),
        @ContainerSpec(name = "fleet-assistant",
            image = "localhost/casehubio/casehub-runtime:latest",
            env = {"llm.capability=balanced", "llm.role=assistant"})
    },
    dimensions = {DeployDimension.class, FleetDimension.class}
)
```

Three containers, two dimensions. DeployDimension proves the first container is alive. FleetDimension proves all three are alive and each one responds according to its config. The test makes a specific claim: the same image, given different config, produces genuinely different behaviour. If that claim is false, the test fails.

## The Curriculum Continues

Each topology level teaches one lesson. Level 01: you can deploy a CaseHub instance and talk to it. Level 02: the audit trail is independent. Level 03: service boundaries exist for a reason. Level 04's lesson is horizontal scaling: the same image, stamped out with forEach, configured differently per instance, produces a fleet of specialists from a single build artifact.

This is the operational pattern that makes desired-state management worth the abstraction. Not "deploy one thing" — that's a shell script. Not "deploy three different things" — that's three shell scripts. Deploy one thing configured three ways, with the runtime ensuring they start in the right order, health-check correctly, and drift back to the declared state when something goes wrong. That's the gap between a container orchestrator and a reconciliation engine.

Level 05 will test what happens when one of those fleet members drifts. Same image, same fleet, same forEach — but now the reconciliation loop detects the drift and corrects it. The fleet that runs itself is only interesting if it can also fix itself.

---
layout: post
title: "YAML-Declared Adaptive LLMs — When Your Fleet Rewires Itself"
date: 2026-10-25
entry_type: note
subtype: diary
projects: [casehubio/casehub-ops]
tags: [desired-state, adaptation, llm, yaml, claudony, qhorus, situation-recompiler]
series: issue-189-llm-triggers-cross-domain
---

# YAML-Declared Adaptive LLMs — When Your Fleet Rewires Itself

In the [previous entry](2026-10-25-mdp01-the-llm-that-deploys-llms.md), I described the uncomfortable outlier: Level 12's LLM deployer worked, but it was Java where everything else was YAML. The adaptation rule framework had the right shape — triggers match situations, actions mutate the graph — but two missing capabilities forced a Java SPI implementation.

Those capabilities now exist. The adaptation framework supports LLM-evaluated triggers and cross-domain node addition. Level 12's deployer is a YAML rule. The Java recompiler is deleted.

## What changed in the framework

Two extensions, both in the API layer.

`AdaptationTrigger` was a single record — situation name plus confidence threshold. Pattern matching only. It's now a sealed interface:

![AdaptationTrigger sealed interface](images/l12-trigger-types.svg)

`PatternTrigger` preserves all existing behavior. YAML that omits the `type` field deserializes as `PatternTrigger` — backward compatible. `LlmEvaluatedTrigger` is the new variant. It names a deployed agent, a catalog of options, and a timeout for the LLM call.

The second extension is `AddNodeActionSpec` — a fourth variant on `AdaptationActionSpec` alongside `scale`, `add`, and `update`. The existing `add` wraps `DeploymentGoals` and compiles through `DeploymentGoalCompiler`, which means it can only create deployment-domain nodes. `add_node` takes raw node specs — a type, a spec map, and dependencies. It resolves through the cross-domain `NodeSpecRegistry`, which knows about `claudony_app`, `agent`, `claudony_pool`, and every other registered node type. No domain-specific compiler required.

## The YAML rule

This is what Level 12's deployer looks like now:

```yaml
adaptations:
  - name: deploy-specialist-on-surge
    trigger:
      type: llm-evaluated
      agent: deployer
      catalog: specialist-catalog
      minConfidence: 0.5
      timeout: PT30S
    actions:
      - type: add_node
        template:
          nodeId: specialist-${catalog.name}
          nodeType: claudony_app
          spec:
            name: specialist-${catalog.name}
            displayName: ${catalog.name}
            description: Ad-hoc specialist for ${catalog.domain}
            image: casehubio/my-llm-app:latest
            config:
              llm.agent-id: specialist-${catalog.name}
              llm.name: ${catalog.name}
              llm.role: ${catalog.role}
              llm.domain: ${catalog.domain}
              llm.archetype: ${catalog.archetype}
              llm.knowledge-base: ${catalog.knowledge-base}
              llm.identity-source: eidos
              llm.deployed-by: deployer
              llm.source-situation: ${situation.id}
        dependsOn: [engine]
```

The `${catalog.*}` placeholders resolve against whichever catalog entry the LLM selects. The `${situation.id}` placeholder binds the triggering situation into the specialist's config — provenance tracking, so you can trace which alert caused which deployment.

The specialist catalog is an inline data block in the topology YAML:

```yaml
data:
  specialist-catalog:
    inline: |
      name:STRING,role:STRING,domain:STRING,archetype:STRING,knowledge-base:STRING
      product-x-specialist,specialist,product-x,expert,kb-product-x
      product-y-specialist,specialist,product-y,expert,kb-product-y
      escalation-specialist,escalation,incident-response,coordinator,kb-escalation
```

Adding a new specialist to the fleet's repertoire is a row in a CSV block. No code. No build. No deployment.

## How the LLM gets called

The trigger evaluation goes through qhorus (the situation-awareness mesh) and the standard platform agent infrastructure:

![YAML adaptation rule runtime flow](images/l12-yaml-rule-flow.svg)

A RAS situation arrives from qhorus with a confidence score. The framework checks `minConfidence` — if the score is too low, nothing happens. If it qualifies, the framework calls the deployer LLM through `AgentProvider`. Eidos handles identity. Manifest handles model routing.

That last part matters for testing. Manifest routes to real LLMs in production and simulated LLMs in test. The test path and the production path are the same code. There's no mock interface, no test-only seam. The simulated LLM receives the same prompt, returns the same JSON structure, and the framework processes it identically.

The LLM responds with JSON — `{"action": "deploy", "entry": "product-x-specialist"}` or `{"action": "reject"}`. The framework looks up the named entry in the catalog. If the LLM hallucinates an entry name that doesn't exist, the framework logs a warning and caches a reject decision. No repeated calls for the same hallucination.

## The non-determinism problem

LLMs aren't deterministic. The same prompt might produce "deploy product-x-specialist" on one call and "deploy product-y-specialist" on the next. If the framework re-evaluated the LLM on every reconciliation cycle, the desired state graph would oscillate — add specialist X, remove X and add Y, remove Y and add X. The reconciliation loop would thrash.

The solution is decision caching. Once the LLM makes a decision for a given (rule, situation) pair, that decision is cached and reused on every subsequent recompile. The LLM is never re-called for the same situation.

![Snapshot-evaluate-apply pattern](images/l12-decision-cache.svg)

The snapshot-evaluate-apply pattern keeps LLM calls out of the synchronized block. Phase 1 snapshots the state inside the lock — fast, no I/O. Phase 2 calls the LLM outside the lock — slow, but safe because the runtime dispatches situation events sequentially per tenant. Phase 3 re-acquires the lock, caches the decision, and rebuilds the graph deterministically from cached decisions.

When the situation resolves, `clearSituation()` removes the cached decision. The node disappears from the next graph. The reconciliation loop deprovisions it. Clean lifecycle, no Java.

## What this means for operational teams

The adaptation rule framework now has four action types and two trigger types. All YAML-declared:

```yaml
# Pattern trigger — existing behavior
trigger:
  type: pattern
  situation: high-load
  minConfidence: 0.7
  deactivateBelow: 0.4

# LLM-evaluated trigger — new
trigger:
  type: llm-evaluated
  agent: deployer
  catalog: specialist-catalog
  minConfidence: 0.5
  timeout: PT30S
```

```yaml
# Scale action — adjust instance count
- type: scale
  target: support-agent
  min: 1
  max: 5

# Update action — change node fields
- type: update
  target: risk-policy
  nodeType: trust_policy
  fields:
    threshold: 0.9

# Add action — deployment-domain nodes
- type: add
  nodes:
    agents:
      - spec: { ... }

# Add node action — any domain (new)
- type: add_node
  template:
    nodeId: specialist-${catalog.name}
    nodeType: claudony_app
    spec: { ... }
  dependsOn: [engine]
```

An ops team can define a rule that scales agents when load spikes, updates trust thresholds when risk patterns shift, or deploys entirely new LLM specialists when the fleet's AI decides they're needed — all without writing Java. The topology YAML is the single source of truth. The build is deterministic. The LLM decisions are audited through the existing CloudEvent trail.

The development velocity difference is real. A YAML edit iterates in seconds. LLM coding assistants generate correct YAML faster and more reliably than Java SPI implementations. The framework handles the graph mutation semantics, the state tracking, the cooldown gates, the decision caching — the rule author doesn't need to know any of that.

## The architecture that made this possible

The adaptation framework moved from the deployment module to the service module — making it domain-agnostic. `AdaptationRule`, the action implementations, and `TenantAdaptationState` now live in `service/`, which any domain module can depend on. The deployment module keeps `DeploymentAdaptiveSituationRecompiler` as a thin registrar that delegates to the shared framework.

The key design constraint: `AdaptationRule` references `GoalCompiler<DeploymentGoals>` as an interface, never the concrete `DeploymentGoalCompiler`. The concrete compiler is injected at registration time by the deployment-specific registrar. This avoids a circular dependency between the service and deployment modules while keeping the legacy `add` action working alongside the new `add_node`.

`AddNodeAction` resolves placeholders against two scopes: `${catalog.*}` from the LLM-selected catalog entry, and `${situation.*}` from the triggering situation. It creates the `NodeSpec` via Jackson `convertValue` — the same serialization path that the YAML topology compiler uses. If a placeholder can't be resolved, you get an error at evaluation time, not a silent misconfiguration.

## What's next

The one rough edge: the `adaptations:` section lives in a separate YAML file because `YamlGraph` in the desiredstate runtime doesn't tolerate unknown top-level keys. A one-line change — adding `@JsonIgnoreProperties(ignoreUnknown = true)` — would let adaptations live inline in the main topology file. That's a cross-repo change to casehub-desiredstate, which I'll do separately.

The more interesting direction is what this pattern enables beyond deployment. The adaptation framework is now domain-agnostic. An infrastructure topology could have rules that spin up monitoring nodes when anomaly detection fires. A compliance topology could escalate audit controls when risk thresholds shift. The trigger evaluation and action application are generic — the domain is just the node type and the catalog content.

The fleet that rewires itself was the proof of concept. The framework that lets any topology rewire itself — declared in YAML, evaluated by LLMs, audited by the platform — is the general capability.

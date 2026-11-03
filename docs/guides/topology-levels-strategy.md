# Topology Levels — Strategy

Living document. Covers the level progression, testing dimensions, and
the path toward interactive tutorials with browser-driven verification.

## The Three Layers

**Levels** are a progressive curriculum. Each level introduces one new
capability on top of everything before it. A learner who completes
Level 03 understands platform deployment, audit trails, and channel
mesh — not because they read about it, but because they deployed it,
exercised it, and saw it work.

**Testing dimensions** are the reusable assertion blocks that prove each
capability works. A dimension is defined once and inherited by every
level above it. Level 03 runs Deploy + Platform + LLM + Audit + Channels.
Level 06 adds Lifecycle. The dimensions accumulate.

**Tutorials** are the user-facing experience. The scaffold UI presents
each level as a guided sequence: deploy the topology, exercise the
capabilities, see the results. The tests that prove the system works
become the tutorial's feedback loop.

## Level Progression

| Level | Name | Introduces | Nodes | Dimensions |
|-------|------|------------|-------|------------|
| 01 | Hello LLM | Universal runtime, LLM capability | 1 (platform) | Deploy, LLM |
| 02 | Accountability | Audit trail, multi-node dependencies | 2 (platform, ledger) | + Platform, Audit |
| 03 | Case Management | Channel mesh, typed messages, service boundaries | 3 (platform, ledger, qhorus) | + Channels |
| 04 | LLM Fleet | Fleet scaling, per-instance routing | N (fleet of runtime instances) | + Fleet |
| 05 | Config Drift | Drift detection, reconciliation | 3+ | + Drift |
| 06 | Lifecycle | Suspend/resume, state preservation | 3+ | + Lifecycle |
| 07 | Topology Mutations | Runtime graph changes | variable | + Mutations |
| 08 | Fleet Observability | Metrics, health aggregation | fleet | + Observability |
| 09 | Chat UI | Browser-facing channel interaction | 3+ | + UI |
| 10 | Pool Deployment | Claudony pools, auto-scaling | claudony domain | + Pools |
| 12 | Ad-hoc Deployer | LLM-driven topology mutations | claudony domain | + Autonomy |
| 14 | Fault Recovery | Fault policy, self-healing | claudony domain | + Fault |
| 15 | Governed Interaction | Channels with protocols, normative form | deployment domain | + Protocols |

### What each level teaches

**Level 01** — The minimum honest deployment. One image, one container,
one LLM interaction. The learner sees: I can deploy a CaseHub instance
and talk to it.

**Level 02** — Why audit matters. Two nodes with a dependency. The
learner sees: ledger records what platform does, and the audit trail
is independent of the platform's lifecycle.

**Level 03** — Service boundaries aren't arbitrary. Three nodes, but
the third (qhorus) exists because it manages independent state (channels,
protocol). The learner sees: co-locate what's coupled, separate what's
genuinely independent.

**Level 04** — The fleet. Multiple LLM instances, each with different
knowledge and capabilities. Routing sends queries to the right
specialist. The learner sees: scaling isn't just "more containers" —
it's "the right container for the question."

**Level 05+** — Each level adds one operational concern (drift, lifecycle,
observability, fault recovery) and shows how the desired-state model
handles it declaratively.

## Testing Dimensions

Each dimension is a reusable assertion block. A dimension:
- Takes a service URL and any prior state from earlier dimensions
- Makes HTTP calls to exercise a specific capability
- Asserts the response proves the capability works
- Produces state that later dimensions can build on

| Dimension | Asserts | Depends on |
|-----------|---------|------------|
| Deploy | Container starts, health UP | — |
| Platform | Application CRUD persists via REST | Deploy |
| LLM | Submit prompt → well-formed response | Deploy |
| Audit | Events recorded, CloudEvent attribution correct | Platform |
| Channels | Create channel, post message, retrieve round-trip | Deploy |
| Fleet | Multiple instances, routing correctness | Deploy, LLM |
| Drift | Detect config change, reconcile back | Deploy, Platform |
| Lifecycle | Suspend/resume, state preserved | Deploy, Platform |
| State Lookup | Query case/app/channel, verify consistency | Platform, Channels |
| Protocols | Normative form — obligations, commitments | Channels |

### Dimension contract

```
interface TestDimension {
    String name();
    Set<String> requires();          // dimensions that must run first
    void execute(ServiceUrls urls,   // target-agnostic URL provider
                 DimensionState state); // shared state from prior dimensions
}
```

The test framework runs dimensions in dependency order. Each dimension
reads from `DimensionState` (e.g., the application ID created by
Platform) and writes results that later dimensions need.

### Target abstraction

Dimensions don't know how containers are deployed. A `ServiceUrls`
provider resolves `serviceUrl("platform")`, `serviceUrl("qhorus")`,
etc. Implementations exist for Podman, K8s, and local processes.

## Path to Interactive Tutorials

### Phase 1 — Java dimension tests (current)

Dimensions are Java test methods. Each level declares which dimensions
it exercises. Tests run via Maven with `-Pintegration-live`.

Ref: #237 (templated e2e dimensions)

### Phase 2 — YAML test descriptors

Extract dimension definitions from Java into YAML:

```yaml
level: 03
name: Case Management
topology: topologies/deployment/03-case-management/03-case-management.yaml

dimensions:
  - deploy
  - platform:
      create:
        path: /api/ops/applications
        body: { name: incident-mgmt, description: "..." }
        expect: { status: 200, body_contains: incident-mgmt }
      lookup:
        path: /api/ops/applications
        expect: { status: 200, body_contains: incident-mgmt }
  - channels:
      create:
        path: /api/channels
        body: { name: incident-channel, semantic: APPEND }
        expect: { status: [200, 201] }
      post_message:
        path: /api/channels/{channelId}/messages
        body: { sender: platform, type: COMMAND, content: "create case" }
        expect: { status: [200, 201] }
      retrieve:
        path: /api/channels/{channelId}/messages
        expect: { body_contains: "create case" }
```

A YAML runner interprets the descriptor, makes the HTTP calls, and
reports results. The Java dimension tests become the reference
implementation; the YAML descriptors are the portable version.

### Phase 3 — Scaffold UI integration

The scaffold UI loads level YAML descriptors and presents them as
interactive tutorials:

1. **Deploy step** — user clicks "Deploy Level 03." Scaffold provisions
   the 3-node topology via the configured target.
2. **Exercise steps** — each dimension becomes a tutorial step. The UI
   shows what the step will do ("Create an application called
   incident-mgmt"), the user clicks "Run," and the UI shows the
   HTTP request, response, and pass/fail.
3. **Visual feedback** — a topology diagram shows nodes lighting up as
   they're deployed. Dimension results appear as checkmarks or
   annotations on the relevant nodes. The audit trail dimension shows
   events flowing into ledger in real time.
4. **Narrative** — each step has explanatory text (from the level's
   "what it teaches" section above) that explains WHY this matters,
   not just WHAT happened.

### Phase 4 — Continuous verification

The same YAML descriptors run in CI as regression tests AND in the
scaffold UI as tutorials. One source of truth, two audiences:
- **CI** runs them headless, reports pass/fail
- **Scaffold** runs them interactively, shows the story

A level that fails in CI also fails as a tutorial. A tutorial that
works interactively also passes CI. The gap between "the tutorial works"
and "the system works" is zero.

## Current State

| Level | Topology | Compilation | Reconciliation | E2E behavior |
|-------|----------|-------------|----------------|--------------|
| 01 | universal runtime | 2 tests | 3 tests | HelloLlmPodmanE2eTest |
| 02 | platform + ledger | 4 tests | 4 tests | — |
| 03 | platform + ledger + qhorus | 5 tests | 4 tests | QhorusPodmanE2eTest |
| 04-15 | fictional images | existing | partial | — |

## References

- #226 — Epic: universal runtime rewrite
- #227 — Level 03 topology rewrite
- #236 — Level 03 qhorus runtime integration + e2e test
- #237 — Templated e2e test dimensions

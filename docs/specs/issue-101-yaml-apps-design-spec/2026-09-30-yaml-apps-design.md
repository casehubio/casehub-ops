# 100% YAML CaseHub Applications — Design Spec

**Issue:** casehubio/casehub-ops#101
**Parent epic:** casehubio/casehub-engine#1017
**Date:** 2026-09-30

## 1. Vision

A developer defines a complete CaseHub application — agents, cases, situations, topology — in YAML. No Java visible. The scaffold runtime reads the YAML and bootstraps everything. Maven handles dependencies and packaging, but the developer never writes Java code.

Three deployment models serve different audiences:
- **Runtime model** — `casehub run ./my-app/` or `docker run -v ./my-app:/app casehub/runtime`. YAML-only, no build step beyond validation.
- **Maven model** — `<packaging>casehub</packaging>`, `mvn package` produces a deployable artifact. For teams with CI/CD pipelines.
- **Hybrid model** — Maven project with `src/main/java/` alongside YAML. For apps that start declarative and add custom Java when needed.

## 2. YAML App Schema

### 2.1 Single-file format

```yaml
app:
  name: my-app
  version: 1.0

agents:
  - id: support-agent
    role: Handle customer support tickets
    capabilities: [ticket-triage, customer-response]
    disposition: helpful
    model: sonnet

cases:
  support-ticket:
    description: Customer support ticket lifecycle
    assign: [support-agent]
    data:
      priority: { type: enum, values: [low, medium, high, critical] }
      category: { type: string }
    stages:
      triage:
        blocks:
          - classify:
              agent: support-agent
              inputs: [raw-ticket]
              outputs: [priority, category]
        transition: respond
      respond:
        blocks:
          - draft-response:
              agent: support-agent
              inputs: [priority, category, ticket-content]
              outputs: [response-draft]
          - human-review:
              role: support-lead
              display: [response-draft]
              actions: [approve, revise, escalate]
        transition:
          approve: close
          revise: respond
          escalate: triage

situations:
  - id: ticket-spike
    severity: warning
    description: Unusual volume of incoming tickets
    detection:
      ganglia: [ticket-rate]
      threshold: any
    response: open-case(support-ticket)

topology:
  nodes:
    - id: my-app
      type: container:app
      spec: { name: my-app, image: my-app:latest, ports: { 8080: 8080 } }
      dependsOn: [my-app-db]
    - id: my-app-db
      type: container:database
      spec: { name: my-app-db, database: mydb }
```

### 2.2 Multi-file format (convention-based)

Same schema, split by directory. The runtime scans well-known directories and merges into one logical model. IDs are the join key — cross-references resolve by ID across files.

```
my-app/
├── app.yaml              # manifest: app.name, app.version
├── agents/
│   └── support-agent.yaml
├── cases/
│   └── support-ticket.yaml
├── situations/
│   └── monitoring.yaml
└── topology/
    └── default.yaml
```

### 2.3 Cases and blocks schema (full)

Cases are state machines. Stages contain blocks. Blocks are the unit of work — an agent task or a human decision point with typed inputs/outputs.

```yaml
cases:
  market-investigation:
    description: Investigate and respond to market anomaly
    assign: [market-sentinel, risk-assessor]

    # Typed data model — fields available across all stages
    data:
      instrument: { type: string, required: true }
      anomaly-type: { type: enum, values: [price, volume, spread, correlation] }
      severity-score: { type: decimal }
      portfolio-exposure: { type: decimal }

    stages:
      detect:
        blocks:
          # Agent block — agent executes, produces typed outputs
          - classify-anomaly:
              agent: market-sentinel
              inputs: [instrument, raw-event]
              outputs: [anomaly-type, severity-score]
          - assess-exposure:
              agent: risk-assessor
              inputs: [instrument, anomaly-type]
              outputs: [portfolio-exposure, affected-strategies]
        transition: review    # fixed transition — always goes to review

      review:
        blocks:
          # Human block — displays data, offers action choices
          - human-review:
              role: senior-trader
              display: [anomaly-summary, exposure-report, historical-context]
              actions: [dismiss, monitor, hedge, halt-strategy]
          # Agent recommendation block — runs alongside human review
          - recommend-action:
              agent: risk-assessor
              inputs: [anomaly-type, severity-score, portfolio-exposure]
              outputs: [recommended-action, reasoning]
        transition:           # decision transition — maps actions to stages
          dismiss: close
          monitor: monitor
          hedge: respond
          halt-strategy: respond

      respond:
        blocks:
          - execute-response:
              agent: strategy-executor
              inputs: [recommended-action, instrument, affected-strategies]
              outputs: [orders-placed, confirmation]
              gate: { approval: senior-trader, risk: high }  # approval gate
          - verify-execution:
              agent: market-sentinel
              inputs: [orders-placed]
              outputs: [execution-quality, residual-risk]
        transition: close

      monitor:
        blocks:
          - watch-instrument:
              agent: market-sentinel
              inputs: [instrument, anomaly-type]
              outputs: [status-update]
              repeat: { interval: 5m, until: normalised }  # repeat semantics
        transition:
          normalised: close
          escalated: review   # re-enter review if situation worsens
```

**Block types:**

| Block type | Key fields | Description |
|---|---|---|
| Agent block | `agent`, `inputs`, `outputs` | Agent executes a task, produces typed outputs |
| Human block | `role`, `display`, `actions` | Human decision point — displays data, offers choices |
| Playbook block | `playbook`, `inputs`, `outputs` | Invokes a playbook YAML by name |

**Block modifiers (optional on any block):**

| Modifier | Example | Description |
|---|---|---|
| `gate` | `{ approval: senior-trader, risk: high }` | Requires human approval before execution |
| `repeat` | `{ interval: 5m, until: normalised }` | Repeats on interval until condition met |
| `auto` | `true` | No approval gate — critical path, executes immediately |
| `timeout` | `30m` | Maximum time before escalation |

**Transition types:**

| Type | Example | Description |
|---|---|---|
| Fixed | `transition: review` | Always goes to named stage |
| Decision | `transition: { dismiss: close, hedge: respond }` | Maps action/outcome to stage |
| Terminal | `transition: close` | Closes the case |

### 2.4 Merge semantics

**Multi-file merging** — combining files within a section directory:

- Files within a directory are unioned (all agents from `agents/*.yaml` merge into one agent list)
- Duplicate IDs within a section are a **validation error** at build time
- Cross-references (`assign: [market-sentinel]`) resolve against the merged model
- File ordering within a directory is alphabetical — no ordering dependency between files

**Environment overlay merging** — overlay files modify the default topology:

| Operation | Overlay syntax | Effect |
|---|---|---|
| Modify scalar | `spec: { replicas: 3 }` | Replaces the scalar value |
| Modify nested object | `spec: { image: prod:2.1 }` | Deep-merges — only specified fields change, others retained |
| Add new node | New `id` not in default | Added to the topology |
| Remove node | `id: old-node, remove: true` | Removed from topology |
| Modify array element | Match by `id` field within array | Element with matching ID is deep-merged |
| Append to array | Element with new `id` | Added to the array |

**Merge precedence** (last wins):
1. Default `app.yaml` / `topology/default.yaml`
2. Environment overlay (`deploy/prod.yaml`)
3. CLI overrides (`--set topology.nodes.my-app.spec.replicas=5`)

**Validation after merge:** all cross-references re-validated after merge. An overlay that removes an agent referenced by a case is a validation error.

## 3. Project Structure

### 3.1 Flat root (demos, getting-started)

```
my-app/
├── pom.xml
├── app.yaml
└── application.properties
```

### 3.2 Convention-based (production)

```
my-app/
├── pom.xml
├── app.yaml
├── agents/
├── cases/
├── situations/
├── topology/
│   └── default.yaml
├── deploy/
│   ├── dev.yaml           # environment overlay
│   └── prod.yaml
└── application.properties
```

### 3.3 Maven hybrid (YAML + Java)

```
my-app/
├── pom.xml                     # <packaging>casehub</packaging>
├── app.yaml
├── agents/
├── cases/
├── src/main/java/              # custom Java when needed
│   └── com/example/
│       └── CustomProvisioner.java
└── application.properties
```

## 4. Maven Packaging Type

### 4.1 User POM

```xml
<project>
  <parent>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-app-parent</artifactId>
    <version>0.2</version>
  </parent>
  <artifactId>my-app</artifactId>
  <packaging>casehub</packaging>
  <dependencies>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-ops-deployment</artifactId>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-ops-container</artifactId>
    </dependency>
  </dependencies>
</project>
```

### 4.2 Lifecycle phases

| Phase | Action |
|-------|--------|
| `validate` | Schema-validate all YAML against registered schemas |
| `process-resources` | Generate ServiceLoader registrations from YAML (agent descriptors → AgentDescriptorRegistrar, situations → SituationDefinitionProvider, etc.) |
| `compile` | No-op for pure YAML; standard javac for hybrid projects |
| `test` | Topology validation — resolvable refs, valid DAG, case flow reachability |
| `package` | Delegate to Quarkus augmentation → uber-jar |

### 4.3 Plugin mechanics — YAML→SPI bridge

The `casehub-maven-plugin` at `process-resources` reads YAML and generates Java source files that implement platform SPIs. These generated files are compiled by Quarkus's augmentation step and discovered via Jandex at runtime.

| YAML section | Generated SPI implementation | Registration target |
|---|---|---|
| `agents:` | `YamlAgentDescriptorRegistrar implements AgentDescriptorRegistrar` | `AgentRegistry` |
| `situations:` | `YamlSituationDefinitionProvider implements SituationDefinitionProvider` | `SituationSource` |
| `cases:` | `YamlCaseDefinitionRegistrar` | `CaseDefinitionRegistry` |
| `topology:` | Copied to `META-INF/desiredstate/` for `YamlDesiredStateProcessor` discovery | `GoalCompiler` (synthetic CDI bean) |
| `events:` | `YamlEventTypeRegistrar` | `EventTypeRegistry` |

Generated sources go to `target/generated-sources/casehub/`. For hybrid projects, user Java and generated Java coexist — standard Maven source set merging.

The plugin also generates a `META-INF/casehub-app.json` manifest containing the merged app model — used by scaffold for app discovery and by the CLI for validation feedback.

### 4.4 casehub-app-parent

Configures both `quarkus-maven-plugin` and `casehub-maven-plugin`. The user's POM inherits this — no plugin declarations needed. The parent also declares the BOM for all platform modules.

The parent POM provides:
- `casehub-maven-plugin` configuration with default source directory
- `quarkus-maven-plugin` configuration for augmentation + packaging
- `dependencyManagement` importing the platform BOM
- Maven profiles for environment overlays (`-Pprod`, `-Pstaging`)
- `maven-enforcer-plugin` rules ensuring platform version consistency

## 5. Scaffold Runtime Bootstrap

For YAML-only apps (runtime model), scaffold IS the application. Bootstrap sequence:

1. Scan directory for `app.yaml` (manifest)
2. Discover `agents/`, `cases/`, `situations/`, `topology/` (or inline sections)
3. Register agent descriptors with `AgentRegistry`
4. Register situations with `SituationSource`
5. Register case definitions with `CaseDefinitionRegistry`
6. Compile topology → `DesiredStateGraph`
7. Start reconciliation loop
8. Serve console API (if ops module present)

No CDI discovery from the user's side — the runtime's beans are pre-wired. YAML feeds data into already-running services.

**Module composition:** Fat runtime (all modules on classpath) for initial demos. Modules are Jandex-activated-by-classpath-presence, so unused ones are inert. Modular runtime (selective module loading) is a future optimization.

## 6. CLI Specification

The `casehub` CLI is the developer on-ramp. It wraps the scaffold runtime for local execution.

### 6.1 Commands

| Command | Description |
|---|---|
| `casehub run [dir]` | Run the app in `dir` (default: current directory). Starts scaffold runtime, loads YAML, bootstraps, serves console. |
| `casehub validate [dir]` | Validate YAML without starting the runtime. Schema check, cross-reference check, DAG validation. |
| `casehub init [name]` | Scaffold a new project — creates `app.yaml` with manifest, `pom.xml`, `.gitignore`. |
| `casehub dev [dir]` | Development mode — hot-reload on YAML changes. Delegates to Quarkus dev mode. |

### 6.2 Discovery

`casehub run` discovers the app definition by searching (in order):
1. `app.yaml` in the target directory
2. `casehub-app.yaml` in the target directory
3. `src/main/casehub/app.yaml` (Maven convention layout)

If none found: error with "No app.yaml found. Run `casehub init` to create one."

### 6.3 Error reporting

Validation errors reference file and line:
```
ERROR agents/market-sentinel.yaml:5 — unknown capability "market-analyis" (did you mean "market-analysis"?)
ERROR cases/investigation.yaml:12 — agent "risk-assesor" not found in agents/ (available: market-sentinel, risk-assessor)
ERROR topology/default.yaml:8 — node "my-app" depends on "my-db" which is not defined
```

Levenshtein suggestions for typos (already implemented in `PluginValidator`). Cross-reference errors list available IDs.

### 6.4 Dev mode

`casehub dev` watches the YAML directory for changes and hot-reloads:
- Agent descriptor changes → re-register with `AgentRegistry`
- Case definition changes → re-register with `CaseDefinitionRegistry`
- Topology changes → recompile graph, trigger reconciliation
- Situation changes → re-register with `SituationSource`

Delegates to Quarkus dev mode internally. The scaffold runtime supports live reload via scaffold#28.

## 7. Testing

### 7.1 Declarative YAML tests

Tests live in `src/test/casehub/` (convention layout) or alongside the app (flat layout). They are YAML files that declare assertions about the app model.

```yaml
# test/topology-test.yaml
tests:
  - name: all nodes form valid DAG
    assert: topology.is-valid-dag

  - name: app depends on database
    assert: topology.node("my-app").dependsOn("my-app-db")

  - name: all agent refs resolve
    assert: cases.all-agent-refs-resolve

  - name: investigation reaches close from every stage
    assert: cases.case("market-investigation").all-stages-reach("close")

  - name: no orphan situations
    assert: situations.all-responses-resolve
```

### 7.2 Test categories

| Category | What it validates | Example |
|---|---|---|
| Schema | YAML conforms to registered schemas | Invalid field names, wrong types |
| Cross-reference | IDs referenced across sections exist | Agent in case exists in agents/ |
| Topology | DAG validity, no cycles, all deps defined | Node depends on undefined node |
| Case flow | Reachability, no dead-end stages | Stage with no transition to close |
| Situation wiring | Responses reference valid case types | `open-case(nonexistent)` |

### 7.3 Execution

Tests run at `mvn test` phase via the `casehub-maven-plugin`. No JUnit, no Java test classes. The plugin reads test YAML files, evaluates assertions against the merged app model, and reports pass/fail in standard Maven test output format.

For the CLI: `casehub validate` runs the same assertions without starting the runtime.

## 8. Migration Path

### 8.1 Fsitrading migration (existing `casehub-deployment.yaml`)

Fsitrading already has a rich `casehub-deployment.yaml` in the existing format. Migration is incremental — the existing format continues to work, new sections are added alongside.

| Step | What changes | What still works |
|---|---|---|
| 1. Add `app:` manifest section | Wrap existing YAML with app metadata | Existing deployment sections unchanged |
| 2. Move agent registrar to `agents:` | Delete `FsiStrategyAgentRegistrar.java`, add `agents:` section | Deployment topology unchanged |
| 3. Move situations to `situations:` | Delete `FsiTradingSituationDefinitionProvider.java` | Playbooks, cases unchanged |
| 4. Move event types to `events:` | Delete `FsiEventTypeRegistrar.java` | Everything else unchanged |
| 5. Delete bootstrap class | Auto-bootstrap replaces `FsiTradingDeploymentBootstrap.java` | Runtime behavior identical |
| 6. Add case definitions | New `cases:` section (currently Java case descriptors) | Additive — no existing code removed |

Each step compiles and runs. The app has fewer Java files after each step. After step 5, no Java files remain (assuming affordances and trust routing are already YAML).

### 8.2 General migration guide

For apps with existing Java SPIs:

1. **Verify** — confirm which Java registrars/providers exist (`*Registrar.java`, `*Provider.java`, `*Bootstrap.java`)
2. **Convert one at a time** — move the simplest registrar to YAML first (usually agent descriptors)
3. **Test** — verify the YAML-registered version behaves identically to the Java version
4. **Delete the Java** — remove the Java class, confirm build still passes
5. **Repeat** — next registrar until no Java remains

The Maven plugin detects both Java SPIs and YAML definitions. If both exist for the same SPI (e.g., a Java `AgentDescriptorRegistrar` AND an `agents:` YAML section), the plugin emits a warning: "Duplicate agent registration — both Java and YAML define agents. Remove one."

## 9. Cross-Repo Gap Analysis

### 6.1 Gaps by repo

| # | Gap | Repo | Existing issue | Blocks demo |
|---|-----|------|---------------|-------------|
| G1 | Auto-bootstrap from classpath YAML | engine | [#1203](https://github.com/casehubio/engine/issues/1203) | Demo 1 |
| G2 | `casehub` Maven packaging type + parent POM | engine | [#1019](https://github.com/casehubio/engine/issues/1019) | Demo 1 |
| G3 | YAML case definition loading | scaffold | [#27](https://github.com/casehubio/scaffold/issues/27) | Demo 1 |
| G4 | YAML agent descriptor loading (verify works) | eidos | — | Demo 1 |
| G5 | YAML situation definitions | ras | [#67](https://github.com/casehubio/casehub-ras/issues/67) | Demo 2 |
| G6 | YAML event type registration | engine | (new) | Demo 2 |
| G7 | Playbook→case stage dispatch | engine | [#1197](https://github.com/casehubio/engine/issues/1197) | Demo 2 |
| G8 | YAML affordance definitions | engine | (new) | Demo 2 |
| G9 | Block patterns in case YAML | blocks | (agentic-yaml exists) | Demo 3 |
| G10 | Channel configuration in app YAML | qhorus | [#464](https://github.com/casehubio/qhorus/issues/464) | Demo 3 |
| G11 | CBR ↔ playbook integration | engine | [#1193](https://github.com/casehubio/engine/issues/1193) | Demo 3 |
| G12 | YAML case definition live reload | scaffold | [#28](https://github.com/casehubio/scaffold/issues/28) | Demo 3 |
| G13 | Internal-API step primitive for plugins | desiredstate | [#131](https://github.com/casehubio/casehub-desiredstate/issues/131) child | Infra |
| G14 | Layer 2 YAML primitives (rest-call, auth-ref) | desiredstate | [#131](https://github.com/casehubio/casehub-desiredstate/issues/131) child | Infra |
| G15 | Deployment node types → YamlGraph unification | ops | (new) | Infra |
| G16 | YAML rules/invariants Drools backend | desiredstate | [#119](https://github.com/casehubio/casehub-desiredstate/issues/119) | Demo 2+ |
| G17 | CaseDefinition.yaml schema sync (26 diffs) | engine | [#1176](https://github.com/casehubio/engine/issues/1176) | Demo 1 |
| G18 | Scaffold ops perspective — deploy/drift/health wiring | scaffold | [#52](https://github.com/casehubio/scaffold/issues/52) | All demos |
| G19 | Scaffold app discovery — load app.yaml from artifact/directory | scaffold | (new, child of #52) | Demo 1 |
| G20 | YAML graph discovery path in Maven plugin | engine/ops | (new, child of #1019) | Demo 1 |

### 6.2 Fsitrading Java → YAML conversion (parallel track)

| Java file | YAML target | Gap |
|-----------|-------------|-----|
| `FsiStrategyAgentRegistrar` | `agents/` section | G4 |
| `FsiTradingSituationDefinitionProvider` | `situations/` section | G5 |
| `FsiTradingDeploymentBootstrap` | auto-bootstrap (eliminate) | G1 |
| `FsiEventTypeRegistrar` | `events/` section | G6 |
| `FsiAffordanceProvider` | `affordances/` or inline in cases | G8 |
| `FsiTrustRoutingPolicyProvider` | already in `casehub-deployment.yaml` | done |

## 10. Demo Milestones

### 7.1 Progressive examples in casehub-examples/getting-started/

| # | Example | New concept | Gaps required |
|---|---------|-------------|--------------|
| 01 | `manifest` | app.yaml with just a name | G1, G2 |
| 02 | `one-agent` | Single agent descriptor | G4 |
| 03 | `agent-case` | One case type, two stages | G3 |
| 04 | `case-blocks` | Blocks — agent tasks + human decisions | G9 |
| 05 | `situations` | RAS situation opens a case | G5 |
| 06 | `playbooks` | Playbook YAML invoked by case stage | G7 |
| 07 | `containers` | Container topology — app + db + network | — (exists) |
| 08 | `channels` | Qhorus channel routing | G10 |
| 09 | `adaptive` | Situation-driven scaling rule | — (exists) |
| 10 | `full-stack` | Everything combined | all |

Each is a standalone Maven project. 01–06 are pure app definition (no infrastructure). 07 introduces container provisioning. 10 is the capstone.

### 7.2 Demo milestones (deliverable gates)

**Demo 1: "Hello CaseHub"** — examples 01–03 working end-to-end.
- Proves: manifest, agent descriptor, case definition, Maven plugin, auto-bootstrap.
- Gaps: G1, G2, G3, G4.
- Repos: engine, scaffold, eidos, casehub-examples.

**Demo 2: "Trading desk"** — examples 04–06 + fsitrading conversion.
- Proves: blocks, situations, playbooks, multi-agent, adaptive scaling.
- Gaps: G5, G6, G7, G8, G9.
- Repos: ras, engine, blocks, fsitrading.

**Demo 3: "Full platform"** — examples 07–10.
- Proves: container deployment, channels, CBR, the complete YAML surface.
- Gaps: G10, G11, G12.
- Repos: qhorus, engine, scaffold, casehub-examples.

### 7.3 Fsitrading (parallel track)

Converts in parallel — its ops integration lives within its own app project. Not gated by the example progression, but shares the same gaps. Fsitrading is the proof that a real app works, not a teaching example.

## 11. Deployment Surfaces

Two deployment surfaces, same YAML app:

- **CLI** (`casehub run ./my-app/`) — developer on-ramp, quick demo, local dev
- **Scaffold console** — enterprise story: deploy through UI, monitor reconciliation, watch drift, manage health, view topology

The examples teach the YAML. Scaffold demonstrates that the same YAML drives a production-grade ops experience. The CLI is "look how simple this is." Scaffold is "and here's what it looks like at scale."

Scaffold already has UI shells for deploy, drift, health, situations, operations, and work-items (16 view modules total). The ops perspective (#52) adds YAML-parsed perspective definitions driving tab rendering. What's missing is wiring these views to DesiredState reconciliation state and app.yaml discovery.

## 12. Critical Path

```
Phase 1: Foundation (Demo 1 gate)
├── G2: casehub Maven packaging type ─────────────┐
├── G1: auto-bootstrap from classpath YAML ────────┤
├── G17: CaseDefinition.yaml schema sync ──────────┤
├── G3: YAML case definition loading ──────────────┼── Demo 1 ✓
├── G4: verify YAML agent descriptors ─────────────┤
├── G19: scaffold app discovery ───────────────────┤
└── G20: YAML graph discovery in Maven plugin ─────┘

Phase 2: Application surface (Demo 2 gate)
├── G5: YAML situation definitions ────────────────┐
├── G6: YAML event type registration ──────────────┤
├── G7: playbook→case dispatch ────────────────────┼── Demo 2 ✓
├── G8: YAML affordance definitions ───────────────┤
├── G9: block patterns in case YAML ───────────────┤
└── G16: YAML rules/invariants Drools backend ─────┘

Phase 3: Full platform (Demo 3 gate)
├── G10: channel configuration in app YAML ────────┐
├── G11: CBR ↔ playbook integration ───────────────┼── Demo 3 ✓
└── G12: YAML case definition live reload ─────────┘

Scaffold track (parallel, spans all demos)
├── G18: ops perspective — deploy/drift/health ────┐
├── G19: app discovery from artifact/directory ────┼── Enterprise demo ✓
├── wiring views to DesiredState reconciliation ───┤
└── claudony#208: pool provisioning (in flight) ───┘
    ↑ slot 202 — REST API, dashboard, metrics
      landing pool management into scaffold ops perspective

Infrastructure track (parallel, enables infra YAML demos)
├── G13: internal-API step primitive ──────────────┐
├── G14: Layer 2 YAML primitives ──────────────────┼── Infra YAML ✓
└── G15: deployment node types → YamlGraph ────────┘

Platform track (parallel, improves YAML quality)
├── G16: rules/invariants Drools backend ──────────┐
└── G17: CaseDefinition schema sync ───────────────┼── YAML fidelity ✓
```

**Phase 1 is the critical path.** G1 (auto-bootstrap), G2 (Maven plugin), and G20 (graph discovery) are the enabling infrastructure — nothing else works without them. G3, G4, G17 are the first content gaps to close. G19 (scaffold app discovery) enables the enterprise demo surface.

**Phase 2 depends on Phase 1** — you can't demonstrate situations/playbooks without a working case runtime.

**Phase 3, Scaffold, Infrastructure, and Platform tracks are parallel** — each advances independently. Scaffold wiring can progress as soon as Phase 1 lands. Infrastructure YAML (G13-G15) is needed for container provisioning via YAML plugins. Platform improvements (G16-G17) improve YAML quality across all phases.

## 13. Documentation Architecture

**Ops docs** (concepts, architecture, extension points):
- `docs/guides/consumer-guide.md` — for app builders
- `docs/guides/contributor-guide.md` — for platform builders
- Cross-reference: `→ See: casehub-examples/getting-started/NN-example/`

**Example READMEs** (working code, how-to):
- Each example has a README explaining what it demonstrates
- Cross-reference: `→ Learn more: ops/docs/guides/consumer-guide.md §Section`

**Neither tries to be both.** Ops guides are reference material. Examples are the on-ramp.

## References

- casehubio/engine#1017 — master epic: zero-authored-Java deployment
- casehubio/engine#1019 — casehub.yaml project descriptor
- casehubio/engine#1203 — auto-bootstrap from classpath YAML (G1)
- casehubio/casehub-ras#67 — YAML situation definitions (G5)
- casehubio/qhorus#464 — channel configuration in app YAML (G10)
- casehubio/examples#95 — getting-started progressive tutorial examples
- casehubio/casehub-engine#978 — epic: pure-YAML execution model
- casehubio/scaffold#27 — CaseDefinitionRegistry YAML shim
- casehubio/scaffold#28 — YAML case definition live reload
- casehubio/casehub-desiredstate#131 — plugin architecture
- casehubio/casehub-ops#83 — casehub-application NodeSpec
- casehubio/casehub-ops#15 — demo: deployment declarative topology
- casehubio/casehub-engine#1197 — StepFileCallableDispatcher
- casehubio/casehub-engine#1193 — yaml-core ↔ CBR integration
- fsitrading `casehub-deployment.yaml` — existing YAML topology
- fsitrading `playbooks/*.yaml` — existing playbook YAML (5 playbooks)

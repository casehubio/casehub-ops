# Design Decisions — 100% YAML CaseHub Applications

## D1: Application definition surface

**Choice:** Single unified YAML schema (`casehub-app.yaml`) covering manifest, agents, cases, situations, and topology
**Alternatives:**
- Separate schemas per concern (agent-descriptors.yaml, cases.yaml, topology.yaml) — more modular but harder to see the whole app
- Extend existing `casehub-deployment.yaml` format only — limits scope to deployment, doesn't cover cases/blocks
**Rationale:** A developer should be able to define their entire application in one place. The schema has natural sections (agents, cases, situations, topology) that map to platform capabilities. Extending the existing deployment YAML preserves compatibility.
**Trade-offs:** Single schema is larger to learn upfront; versioning the schema across platform releases requires coordination
**Exploration:** quick
**Status:** captured

## D2: File organisation — single-file vs convention-based directories

**Choice:** Support both. Single-file `app.yaml` for demos, convention-based directories (`agents/`, `cases/`, `situations/`, `topology/`) for larger projects. Same schema, just split.
**Alternatives:**
- Single-file only — simpler tooling but unwieldy at scale
- Directory-only — forces overhead on simple projects
**Rationale:** Like Kubernetes — accepts a single YAML with `---` separators or a directory of files. Progressive complexity: start flat, split when it grows.
**Trade-offs:** Plugin must handle both discovery modes; two "first experience" paths to document
**Exploration:** quick
**Status:** captured

## D3: Build system — Maven packaging type

**Choice:** Custom Maven `casehub` packaging type with `casehub-app-parent` POM. Lifecycle: validate → process-resources → package (no compile). User POM is ~15 lines.
**Alternatives:**
- CLI-first, POM generated from YAML — hides Maven entirely but adds bootstrap tooling to build
- Quarkus extension only — no plugin, no CLI, but no validation until runtime
**Rationale:** Maven is already the platform's build system. A thin POM with a plugin is honest infrastructure. POM declares dependencies (what you depend on), YAML declares application (what you are). No duplication.
**Trade-offs:** `pom.xml` is visible in a "100% YAML" project, which some may see as Java leaking through. Mitigated by the parent POM doing all configuration.
**Depends on:** D1
**Exploration:** quick
**Status:** captured

## D4: Project structure — root vs src/main/casehub

**Choice:** Both valid, plugin supports either. Flat root for examples/getting-started, `src/main/casehub/` for production projects.
**Alternatives:**
- Root only — cleaner for demos but messy with overlays/tests/CI artifacts
- `src/main/casehub/` only — consistent with Maven conventions but heavy for simple projects
**Rationale:** Maven's `sourceDirectory` can point anywhere. For production projects, `src/main/casehub/` gives separation (overlays in `src/main/casehub-prod/`, tests in `src/test/casehub/`). For demos, flat root is the right first impression.
**Trade-offs:** Two valid layouts means the plugin must auto-detect or be configured. Documentation must cover both.
**Depends on:** D3
**Exploration:** quick
**Status:** captured

## D5: Runtime bootstrap — scaffold as platform runtime

**Choice:** Scaffold project serves as the ops runtime that reads YAML and bootstraps everything. YAML-only apps don't need Quarkus — scaffold IS the Quarkus app, user provides configuration.
**Alternatives:**
- Each YAML app is its own Quarkus uber-jar — works but means every app carries framework weight
- Dedicated CLI binary (like terraform) — cleanest UX but separate tooling to build
**Rationale:** A YAML-only app describes an application, it doesn't compile one. The scaffold runtime reads the directory, registers agents/cases/situations, compiles topology, starts reconciliation. Quarkus is an internal implementation detail. For users who need Java, the Maven packaging type produces an uber-jar (graduation path).
**Trade-offs:** Scaffold becomes a critical-path dependency. Module composition (which domain modules are on the classpath) needs a resolution mechanism — fat runtime (all modules) is simplest for demos.
**Exploration:** quick
**Status:** captured

## D6: Environment overlays

**Choice:** Default topology in the app definition, environment-specific overlays merge over it. Overlay merges by node ID, same semantics as multi-file merging.
**Alternatives:**
- Helm-style values files with templating — more flexible but adds template complexity
- Separate deployment repo only — cleaner separation but harder for small teams
**Rationale:** App developer writes the default topology (works for local dev). Ops team writes thin overlays for prod (replicas, images, credentials). No templating language — pure merge semantics. Maven profiles activate overlays (`mvn package -Pprod`).
**Trade-offs:** Merge semantics must handle additions, modifications, and deletions cleanly. No conditional logic in overlays — if needed, use multiple overlay files.
**Depends on:** D3, D4
**Exploration:** quick
**Status:** captured

## D7: Example progression — in casehub-examples, not ops

**Choice:** Progressive examples live in `casehub-examples/getting-started/`, not in ops. They teach the platform, not ops specifically. Ops is infrastructure that happens to be there.
**Alternatives:**
- Examples in ops repo — closer to the integration code but conflates "learning ops" with "learning the platform"
- Examples in each repo — already exists (eidos-examples, desiredstate-examples) but those teach individual repo capabilities, not end-to-end
**Rationale:** The per-repo examples teach depth ("how does desiredstate forEach work?"). The getting-started examples teach the platform progressively ("how do I build a case app?"). Two audiences, two tracks, one repo.
**Trade-offs:** casehub-examples takes a dependency on ops (transitively). Cross-repo doc references needed between ops guides and examples.
**Depends on:** D5
**Exploration:** quick
**Status:** captured

## D8: Documentation cross-referencing

**Choice:** Ops docs explain what and why (concepts, architecture, extension points). Examples show how (working code). Cross-referenced bidirectionally, never duplicated.
**Alternatives:**
- Self-contained examples with embedded docs — no cross-references but content duplication
- Docs-only, no examples — cheaper to maintain but harder to learn from
**Rationale:** Ops consumer-guide links to examples that demonstrate each concept. Each example README links back to ops docs for deeper understanding. Neither tries to be both.
**Trade-offs:** Cross-references can go stale if either side changes without updating the other. Mitigated by doc-freshness checks in CI.
**Depends on:** D7
**Exploration:** quick
**Status:** captured

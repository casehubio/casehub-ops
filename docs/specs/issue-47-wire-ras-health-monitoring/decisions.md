# Decisions — #47 Wire RAS Health Monitoring

## D1: Situation registration granularity

**Choice:** Cadence-class grouping (~12-15 situations per managed application) — dimensions with mixed cadences split into realtime and periodic sub-situations; uniform-cadence dimensions stay as one situation
**Alternatives:**
- One situation per dimension (9 per app) — simpler registration, but SituationDefinition forces all ganglia within a situation to share correlationWindow, eventBufferDelay, and triggerMode. HEALTH_MONITORING has heartbeat-check (30s, FireOnce) alongside log-anomaly (5min, Repeating) — one situation can't serve both.
- One situation per ganglion (~35 per app) — maximum flexibility per detection concern, but high registration overhead and no practical benefit beyond cadence-class grouping
- One umbrella situation per app (1 per app) — simplest registration, but all ganglia share one trigger mode/cooldown which doesn't match reality
**Rationale:** SituationDefinition constrains all ganglia within it to share correlationWindow, eventBufferDelay, and triggerMode. Dimensions like HEALTH_MONITORING span sub-second reactive checks (heartbeat-check) to multi-minute pattern analysis (log-anomaly). Cadence-class grouping splits these into e.g. `health-realtime` (30s window, FireOnce) and `health-periodic` (5min window, Repeating). Dimensions with uniform cadence (e.g. DECOMMISSION) stay as one situation. The observer still parses dimension from the situationId prefix — the cadence suffix is transparent to the bridge.
**Trade-offs:** More situations than per-dimension (12-15 vs 9), slightly more registration/deregistration logic. Manageable — still far below per-ganglion (35).
**Exploration:** quick
**Status:** revised (R1-02: SituationDefinition temporal constraints; R1-03: cadence-class grouping alternative)

## D2: Ganglion implementation type

**Choice:** All ganglia as `ExpressionRules` descriptors
**Alternatives:**
- Mix of `ExpressionRules` and `NaiveBayes` — probabilistic reasoning for log-anomaly, incident-pattern, etc. Adds value but requires training data and tuning that is orthogonal to wiring
**Rationale:** ExpressionRules covers all 35 detection concerns adequately for the initial wiring. Each ganglion evaluates CloudEvent data against conditions and returns DETECTED/NOISE with confidence. NaiveBayes can be swapped in per-ganglion later without changing registration or bridge code — the GanglionDescriptor sealed interface supports both variants transparently.
**Trade-offs:** No probabilistic reasoning in initial ganglia. Pattern-detection ganglia (incident-pattern, scaling-pattern) will use rule-based heuristics rather than learned models. Sufficient for demonstrating the full detection surface; ML-based ganglia are a refinement, not a structural change.
**Exploration:** quick
**Status:** captured

## D3: CDI observer placement for SituationChangeEvent

**Choice:** Separate `RasSituationObserver` class that translates RAS concepts to ops concepts before calling `ServiceDetectionBridge.onDetection()`
**Alternatives:**
- Add `@ObservesAsync SituationChangeEvent` directly to `ServiceDetectionBridge` — fewer classes, but mixes RAS-specific translation (situationId parsing, SituationContext unpacking) with dimension routing logic
**Rationale:** The mapping from RAS concepts (situationId, correlationKey, SituationContext with detections) to ops concepts (situationType string, caseId UUID, detectionData Map) is a translation concern. ServiceDetectionBridge stays unchanged — it already works and is tested. The observer handles: parsing dimension from situationId prefix, resolving correlationKey to caseId, extracting detection data from SituationContext, and filtering on ChangeType.TRIGGERED.
**Trade-offs:** One more class. Trivial cost for clean separation.
**Depends on:** D1 (situationId format determines how the observer parses dimension)
**Exploration:** quick
**Status:** captured

## D4: CloudEvent type namespace

**Choice:** All event types under `io.casehub.ops.*` namespace — ops app owns the event contract
**Alternatives:**
- Mix ops-owned and external event types (K8s-native, CVE scanner-native, etc.) — more realistic but ganglia must handle multiple event formats and the type surface is unstable
**Rationale:** The ops app already has `K8sWatchManager` translating K8s events into its domain. A clean `io.casehub.ops.*` namespace gives ganglia a stable, well-defined contract regardless of upstream source. External event translation is an ingestion concern, not a detection concern. Ganglia evaluate normalized ops events.
**Trade-offs:** Requires translation layer for every external event source. Acceptable — ingestion adapters are simpler than ganglia that handle multiple formats.
**Exploration:** quick
**Status:** captured

## D5: Ganglion provider structure

**Choice:** Single `OpsMonitoringSituationDefinitionProvider` implementing `SituationDefinitionProvider` — returns all ~35 `GanglionDescriptor` instances from `ganglionDescriptors()` and empty `registrations()` (situations registered dynamically per-app via `SituationRegistrar`)
**Alternatives:**
- One provider per dimension (9 classes) — each declares its own ganglia. More files, each self-contained, but unnecessary fragmentation for static compile-time declarations
**Rationale:** Ganglia are static, known at compile time, deployed once with the app. One provider class with private methods per dimension (same pattern as `ServiceCaseDescriptor.healthBindings()`) keeps the full detection surface in one place. Situations are dynamic (per-app deploy/decommission) and use `SituationRegistrar` directly — they don't belong in the provider.
**Trade-offs:** Single file grows to ~35 ganglion descriptors. Manageable with per-dimension grouping methods.
**Exploration:** quick
**Status:** captured

## D6: Registration lifecycle organization

**Choice:** Extract to `ServiceMonitoringRegistrar` — dedicated class encapsulating both RAS situation registration and ganglion binding registration. `ApplicationLifecycleService` calls `registrar.register(...)` / `registrar.deregister(...)`
**Alternatives:**
- Inline in `ApplicationLifecycleService.deploy()` / `decommission()` — consistent with existing `DriftSignalBridge` and `ScalingEvaluator` registration pattern, but `ApplicationLifecycleService` is already 500+ lines and the RAS registration logic is non-trivial (building 9 SituationDefinitions with per-dimension event types, chain modes, trigger modes, plus corresponding GanglionBinding lists)
**Rationale:** Co-locates the two related registrations (RAS situations + ganglion bindings) that must stay in sync. Keeps `deploy()` focused on orchestration. The registrar owns the mapping from DimensionType to situation configuration — a single place to change when dimensions evolve.
**Trade-offs:** `ApplicationLifecycleService` now delegates to one more collaborator. Consistent with its existing pattern (it already delegates to `DriftSignalBridge`, `ScalingEvaluator`, `DecommissionCompletionHandler`, etc.).
**Depends on:** D1 (per-dimension situations define the registration surface), D5 (provider declares ganglia; registrar builds situations that reference them)
**Exploration:** quick
**Status:** captured

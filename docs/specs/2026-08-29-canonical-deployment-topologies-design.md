# Canonical Deployment Topologies — Design Spec

**Date:** 2026-08-29
**Issue:** TBD — to be created before Phase 1 begins
**Research:** `docs/research/2026-08-29-canonical-deployment-topologies.md`
**Chapter:** C6 — Canonical Deployment Topologies (Journey: Infrastructure maturity, extends L2 Infra)
**Status:** Design — pending review

---

## 1. Goal

Prove that the CaseHub desired-state system can express and manage the full range of
real-world deployment architectures — from a single-service dev blog to a multi-region
active-passive banking core — using **YAML as the primary interface** and the existing
desiredstate YAML frontend primitives (modules, invariants, rules, forEach, lifecycle
phases).

The topology exemplars simultaneously serve as integration tests, tutorial material,
and the reference catalogue for how to deploy software with CaseHub.

---

## 2. Scope

### In Scope

- 5 application architectures × 4 infrastructure topologies (~14 meaningful
  intersections)
- New `InfraNodeSpec` sealed variants for topology infrastructure types (Java records)
- Supporting enum types: `LoadBalancerType`, `FailoverPolicy`, `ReplicationMode`
- New `InfraNodeProvisioner` handlers for those types
- `NodeSpecFactory` SPI in YAML frontend for `InfraDesiredNodeSpec` wrapping
- Reusable YAML topology modules shipping in `casehub-ops-infra.jar`
- YAML topology exemplars for each matrix intersection with real-domain language
- 3-layer test pyramid (compilation, reconciliation, live) gated by Maven profiles
- New `topology-tests/` module for exemplars and tests

### Out of Scope (this spec)

- Multi-region active-active (requires CRDT/consensus — a data architecture problem)
- GOAP migration planning (Phase 6 in research — separate spec)
- Service lifecycle integration (Phase 7 — separate spec)
- Deployment strategies (blue-green, canary, rolling — orthogonal concern, later)
- Eidos platform generator extraction (in-progress, separate workstream)

---

## 3. Architecture

### 3.1 Layer Map

```
┌─────────────────────────────────────────────────────────────────┐
│                    End-User YAML Declaration                     │
│  topology modules, invariants, rules, forEach, lifecycle phases  │
└──────────────────────────┬──────────────────────────────────────┘
                           │ imports
┌──────────────────────────▼──────────────────────────────────────┐
│              Topology Modules (YAML — ship in infra.jar)         │
│  load-balancer.yaml  ha-multi-az.yaml  multi-region.yaml         │
│  service-mesh.yaml                                               │
└──────────────────────────┬──────────────────────────────────────┘
                           │ type: references
┌──────────────────────────▼──────────────────────────────────────┐
│           InfraNodeSpec Sealed Hierarchy (Java records)           │
│  LoadBalancerSpec  SidecarProxySpec  MeshControlPlaneSpec         │
│  DnsFailoverSpec   DataReplicationSpec                           │
│  + existing: K8sNamespaceSpec, K8sDeploymentSpec, K8sServiceSpec  │
│              K8sIngressSpec, ComputeInstanceSpec, ...             │
└──────────────────────────┬──────────────────────────────────────┘
                           │ @NodeTypeId + NodeSpecFactory
┌──────────────────────────▼──────────────────────────────────────┐
│           YamlGraphRecorder + NodeSpecFactory wrapping            │
│  Variables → Conditions → ForEach → Modules → Rules → Invariants │
└──────────────────────────┬──────────────────────────────────────┘
                           │ produces
┌──────────────────────────▼──────────────────────────────────────┐
│                     DesiredStateGraph                             │
│                  (nodes + dependencies)                           │
└──────────────────────────┬──────────────────────────────────────┘
                           │
            ┌──────────────▼──────────────┐
            │      TransitionPlanner       │
            │   (desired vs actual diff)   │
            └──────────────┬──────────────┘
                           │
            ┌──────────────▼──────────────┐
            │     InfraNodeProvisioner     │
            │    (backend-id dispatch)     │
            └─────────────────────────────┘
```

### 3.2 What's New vs What Exists

| Component | Status | Work Required |
|---|---|---|
| YamlGraphRecorder | Exists | Use `NodeSpecFactory` for spec resolution instead of direct class cast |
| ForEachExpander | Exists | Use `NodeSpecFactory` for spec resolution instead of direct class cast |
| ModuleExpander | Exists | None |
| GraphRuleEngine | Exists | None |
| GraphInvariantEngine | Exists | None |
| YamlLifecycleCompiler | Exists | None |
| NodeSpecRegistry | Exists | Generalized: maps type string → `NodeSpecFactory` (backwards-compatible) |
| YamlDesiredStateProcessor | Exists | Extended `scanNodeTypes()`: discover `@NodeTypeId` on `InfraNodeSpec` types, register with wrapping factory |
| VariableResolver | Exists | None |
| TransitionPlanner | Exists | None |
| InfraNodeProvisioner | Exists | Add 5 types to `handledTypes()` |
| InfraActualStateAdapter | Exists | Add 5 types to `handledTypes()` |
| InfraGoalCompiler | Exists | Add 5 `parseSpec()` cases for new types |
| InfraNodeSpec hierarchy | Exists | 5 new sealed variants + `@NodeTypeId` on all variants (new and existing) |
| NodeSpecFactory | **New** | SPI: `NodeSpec create(ObjectMapper, Map<String,Object>)` — wrapping hook for non-NodeSpec types |
| Supporting enums | **New** | `LoadBalancerType`, `FailoverPolicy`, `ReplicationMode` |
| Topology modules | **New** | 4 YAML modules |
| Topology exemplars | **New** | ~14 YAML declarations |
| topology-tests module | **New** | Maven module + test pyramid |

### 3.3 YAML-to-Infra Compilation Pathway

`InfraNodeSpec` does **not** extend `NodeSpec` — this is intentional (ARC42STORIES
§9.4 L1). The YAML frontend's `NodeSpecRegistry` maps type strings to
`Class<? extends NodeSpec>`, so `InfraNodeSpec` types cannot be registered directly.

The solution is a `NodeSpecFactory` SPI that hooks into YAML type resolution:

```java
public interface NodeSpecFactory {
    NodeSpec create(ObjectMapper mapper, Map<String, Object> rawProperties);
}
```

**Build-time discovery:** `YamlDesiredStateProcessor.scanNodeTypes()` is extended to
detect `@NodeTypeId` annotations on `InfraNodeSpec` implementors (not just `NodeSpec`
implementors). For each discovered infra type, it registers a wrapping factory that:

1. Deserializes the raw YAML map into the `InfraNodeSpec` record
   (`mapper.convertValue(rawProperties, K8sIngressSpec.class)`)
2. Wraps in `InfraDesiredNodeSpec(infraSpec, backendId)` — `backendId` comes from
   the YAML spec map or defaults to the configured backend

**Registry evolution:** `NodeSpecRegistry` is generalized from
`Map<String, Class<? extends NodeSpec>>` to `Map<String, NodeSpecFactory>`.
Existing `NodeSpec` types use a direct-cast factory (backwards-compatible):

```java
// Legacy path: type extends NodeSpec directly
class DirectCastFactory implements NodeSpecFactory {
    private final Class<? extends NodeSpec> specClass;
    public NodeSpec create(ObjectMapper mapper, Map<String, Object> raw) {
        return mapper.convertValue(raw, specClass);
    }
}

// InfraNodeSpec path: wrap in InfraDesiredNodeSpec
class InfraWrappingFactory implements NodeSpecFactory {
    private final Class<? extends InfraNodeSpec> infraClass;
    public NodeSpec create(ObjectMapper mapper, Map<String, Object> raw) {
        var backendId = (String) raw.getOrDefault("backendId", defaultBackend);
        var infraSpec = mapper.convertValue(raw, infraClass);
        return new InfraDesiredNodeSpec(infraSpec, backendId);
    }
}
```

This preserves the compile-time safety of the `InfraNodeSpec`/`NodeSpec` separation
while enabling YAML-native topology declarations without a custom goal compiler.

---

## 4. New InfraNodeSpec Sealed Variants

Each is a Java record in `api/src/main/java/io/casehub/ops/api/infra/`, extending the
existing `InfraNodeSpec` sealed interface. Each record carries `@NodeTypeId` matching
its `resourceType()` return value — this is how `YamlDesiredStateProcessor` discovers
the type at build time and registers it with the wrapping `NodeSpecFactory` (§3.3).

**Why sealed variants, not `GenericResourceSpec`?** The existing sealed hierarchy
includes `GenericResourceSpec(String resourceType, JsonNode config)` — an untyped
escape hatch. These five types are sealed variants because:

1. **Compile-time exhaustiveness** — `switch` on `InfraNodeSpec` forces handling
   each type; `GenericResourceSpec` collapses everything into one branch.
2. **Record field validation** — compact constructors enforce required fields and
   coalesce optional defaults. `GenericResourceSpec` defers all validation to
   runtime JSON inspection.
3. **Schema generation** — the platform generator derives YAML schemas and
   TypeScript types from record fields. `JsonNode config` is opaque.
4. **Domain modelling** — load balancers, service meshes, and DNS failover are
   first-class infrastructure concepts with stable, well-understood schemas.
   `GenericResourceSpec` is for genuinely ad-hoc resources where the schema is
   unknown or provider-specific at design time.

`GenericResourceSpec` remains available for extension points — if a backend needs a
resource type not worth promoting to a sealed variant, it uses the generic escape
hatch. The threshold: does the type appear in reusable topology modules? If yes,
it should be a sealed variant for schema safety.

### 4.1 LoadBalancerSpec

Represents a load balancer (application or network layer) that distributes traffic
across target services.

```java
@NodeTypeId("load_balancer")
public record LoadBalancerSpec(
    String name,
    String namespace,
    LoadBalancerType type,        // APPLICATION, NETWORK
    String healthCheckPath,
    int healthCheckIntervalSeconds,
    List<String> targetServices,
    Labels labels
) implements InfraNodeSpec {
    public String resourceType() { return "load_balancer"; }
}
```

**YAML usage:**
```yaml
store-lb:
  type: load_balancer
  spec:
    name: storefront-lb
    namespace: storefront
    type: APPLICATION
    healthCheckPath: /health
    healthCheckIntervalSeconds: 30
    targetServices: [storefront-nginx]
```

### 4.2 ServiceMeshControlPlaneSpec

Represents the control plane of a service mesh (Istio pilot, Linkerd control plane).

```java
@NodeTypeId("mesh_control_plane")
public record ServiceMeshControlPlaneSpec(
    String name,
    String namespace,
    String image,
    int replicas,
    Labels labels
) implements InfraNodeSpec {
    public String resourceType() { return "mesh_control_plane"; }
}
```

### 4.3 SidecarProxySpec

Represents a sidecar proxy container paired with an application service.

```java
@NodeTypeId("sidecar_proxy")
public record SidecarProxySpec(
    String name,
    String namespace,
    String image,
    String targetService,
    ResourceRequirements resources,
    Labels labels
) implements InfraNodeSpec {
    public String resourceType() { return "sidecar_proxy"; }
}
```

### 4.4 DnsFailoverSpec

Represents DNS-based failover configuration between a primary and secondary cluster.

```java
@NodeTypeId("dns_failover")
public record DnsFailoverSpec(
    String name,
    String primaryEndpoint,
    String secondaryEndpoint,
    int ttlSeconds,
    String healthCheckPath,
    FailoverPolicy policy           // AUTOMATIC, MANUAL
) implements InfraNodeSpec {
    public String resourceType() { return "dns_failover"; }
}
```

### 4.5 DataReplicationSpec

Represents data replication between clusters/regions.

```java
@NodeTypeId("data_replication")
public record DataReplicationSpec(
    String name,
    String sourceCluster,
    String targetCluster,
    ReplicationMode mode,           // ASYNC, SEMI_SYNC
    String sourceDatabase,
    int lagThresholdSeconds
) implements InfraNodeSpec {
    public String resourceType() { return "data_replication"; }
}
```

All five variants are wrapped in `InfraDesiredNodeSpec` (existing composite pattern)
to carry `backendId` for backend routing. `InfraNodeProvisioner.handledTypes()` and
`InfraActualStateAdapter.handledTypes()` must be extended to include the 5 new
`resourceType()` values (`load_balancer`, `mesh_control_plane`, `sidecar_proxy`,
`dns_failover`, `data_replication`), so that `DefaultNodeProvisionerRouter` routes
to the infra provisioner and actual-state reads return meaningful status.

**Dispatch mechanism:** `InfraNodeProvisioner.provision()` unwraps the
`InfraDesiredNodeSpec`, looks up the `InfraBackend` by `backendId`, and delegates.
There is no sealed type switch in the provisioner — dispatch is by backend, not by
spec type. The backend (`StandaloneBackend`) locates a `ResourceProvisioner` via
`handles(spec)`, with `InMemoryResourceProvisioner` at `@Priority(0)` as the
catch-all fallback.

**Provisioning scope:** These new types represent a data model for topology
expression and YAML compilation. The `InMemoryResourceProvisioner` handles all
`InfraNodeSpec` types and records them as HEALTHY in-memory — sufficient for
compilation and reconciliation testing. Real infrastructure backends (cloud LB
provisioners, DNS API provisioners, replication managers) are separate
implementation work beyond this spec's scope.

**YAML deserialization contract:** All new records must null-coalesce optional fields
in their compact constructors rather than `requireNonNull`, because the YAML path
(`mapper.convertValue()` in `ForEachExpander`) passes `null` for absent YAML keys.
`Labels` defaults to `Labels.empty()`. Example pattern for `LoadBalancerSpec`:

```java
public LoadBalancerSpec {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(targetServices, "targetServices");
    targetServices = List.copyOf(targetServices);
    if (labels == null) labels = Labels.empty();
}
```

All five records follow this pattern — required fields use `requireNonNull`,
optional fields (`Labels`, `ResourceRequirements`) null-coalesce to empty defaults.

### 4.6 Supporting Enum Types

Three new enums in `api/src/main/java/io/casehub/ops/api/infra/types/`:

```java
public enum LoadBalancerType { APPLICATION, NETWORK }

public enum FailoverPolicy { AUTOMATIC, MANUAL }

public enum ReplicationMode { ASYNC, SEMI_SYNC }
```

These are referenced by `LoadBalancerSpec`, `DnsFailoverSpec`, and
`DataReplicationSpec` respectively. Jackson deserializes enum values from YAML
strings directly (`APPLICATION`, `ASYNC`, etc.).

### 4.7 ResourceRequirements YAML Contract

The existing `ResourceRequirements` record has four fields (`cpuRequest`,
`cpuLimit`, `memoryRequest`, `memoryLimit`). YAML declarations using
`SidecarProxySpec` or any type with `ResourceRequirements resources` must specify
all four fields:

```yaml
resources:
  cpuRequest: "100m"
  cpuLimit: "500m"
  memoryRequest: "128Mi"
  memoryLimit: "256Mi"
```

The research document's shorthand (`cpu`, `memory`) was incorrect — it does not
match the Java record structure.

---

## 5. Topology Modules (YAML)

Ship in `infra/src/main/resources/META-INF/desiredstate/modules/`.

### 5.1 load-balancer.yaml

```yaml
module:
  name: load-balancer
  parameters:
    target_service:
      type: string
      required: true
    namespace:
      type: string
      required: true
    health_check_path:
      type: string
      default: /health
    lb_type:
      type: string
      default: APPLICATION

nodes:
  lb:
    type: load_balancer
    dependsOn: ["${var.target_service}"]
    spec:
      name: "${var.target_service}-lb"
      namespace: ${var.namespace}
      type: ${var.lb_type}
      healthCheckPath: ${var.health_check_path}
      healthCheckIntervalSeconds: 30
      targetServices: ["${var.target_service}"]

  ingress:
    type: k8s_ingress
    dependsOn: [lb]
    spec:
      namespace: ${var.namespace}
      name: "${var.target_service}-ingress"
      host: "${var.target_service}.example.com"
      rules:
        - path: /
          serviceName: ${var.target_service}
          servicePort: 80

invariants:
  lb-has-target:
    match:
      lb: { type: load_balancer }
    directDep:
      target: { type: "*", of: lb, direction: DEPENDENCIES }
    message: "Load balancer must route to at least one target service"
```

### 5.2 ha-multi-az.yaml

```yaml
module:
  name: ha-multi-az
  parameters:
    namespace:
      type: string
      required: true
    region:
      type: string
      required: true
    zones:
      type: list
      required: true

nodes:
  ha-control-plane:
    type: k8s_deployment
    spec:
      namespace: ${var.namespace}
      name: ha-control-plane
      image: k8s-control-plane:latest
      replicas: 3
      labels:
        component: control-plane
        region: ${var.region}
        anti-affinity: zone-spread

invariants:
  ha-control-plane-in-namespace:
    match:
      cp: { type: k8s_deployment }
    directDep:
      ns: { type: k8s_namespace, of: cp, direction: DEPENDENCIES }
    message: "HA control plane must be deployed within a managed namespace"
```

**Note on zone count validation:** The original `minimum-three-zones` invariant
was non-functional — an invariant with only a `message:` and no `match:` pattern
never evaluates (the engine requires at least one MATCH pattern to bind nodes).
The replacement validates structural correctness. Minimum zone count enforcement
requires parameter-level constraints (`minLength: 3` on the `zones` parameter),
which the module parameter system does not yet support. Tracked as a future
enhancement.

### 5.3 service-mesh.yaml

```yaml
module:
  name: service-mesh
  parameters:
    namespace:
      type: string
      required: true
    control_plane_image:
      type: string
      default: istio/pilot:1.20
    control_plane_replicas:
      type: integer
      default: 3

nodes:
  mesh-control-plane:
    type: mesh_control_plane
    spec:
      name: mesh-control-plane
      namespace: ${var.namespace}
      image: ${var.control_plane_image}
      replicas: ${var.control_plane_replicas}

rules:
  sidecar-depends-on-control-plane:
    match:
      proxy: { type: sidecar_proxy }
    notExists:
      cp: { type: mesh_control_plane, of: proxy, direction: DEPENDENCIES }
    actions:
      - addDependency:
          from: "${match.proxy.id}"
          to: mesh-control-plane
```

**Sidecar injection scoping:** The sidecar-injection rule in the research
exemplar matches ALL `k8s-deployment` nodes. The YAML rule system's `match:`
clause filters by node type only — not by spec fields or labels. This is by
design: importing the `service-mesh` module means the entire topology is
meshed. For mixed workloads (some meshed, some not), use separate topology
declarations or conditional nodes (`when:`) on the deployment definitions.

### 5.4 multi-region.yaml

```yaml
module:
  name: multi-region
  parameters:
    primary_cluster:
      type: string
      required: true
    dr_cluster:
      type: string
      required: true
    source_database:
      type: string
      required: true
    failover_health_check:
      type: string
      default: /health
    replication_mode:
      type: string
      default: ASYNC

nodes:
  data-replication:
    type: data_replication
    spec:
      name: "${var.primary_cluster}-to-${var.dr_cluster}"
      sourceCluster: ${var.primary_cluster}
      targetCluster: ${var.dr_cluster}
      mode: ${var.replication_mode}
      sourceDatabase: ${var.source_database}
      lagThresholdSeconds: 30

  dns-failover:
    type: dns_failover
    dependsOn: [data-replication]
    spec:
      name: "${var.primary_cluster}-failover"
      primaryEndpoint: "${var.primary_cluster}.example.com"
      secondaryEndpoint: "${var.dr_cluster}.example.com"
      ttlSeconds: 60
      healthCheckPath: ${var.failover_health_check}
      policy: AUTOMATIC

invariants:
  replication-before-failover:
    match:
      fo: { type: dns_failover }
    directDep:
      repl: { type: data_replication, of: fo, direction: DEPENDENCIES }
    message: "DNS failover requires data replication to be configured first"
```

---

### 5.5 Module + Lifecycle Phase Interaction

When a topology uses both `lifecycle:` phases and `imports:`, module expansion must
occur before phase compilation. Currently, `createYamlLifecycleGoalCompiler()` does
not call `ModuleExpander` — this is a gap that must be addressed in Phase 2.

**Design:** Module imports are expanded at the graph level before lifecycle phasing.
Module-imported nodes that have `dependsOn` on a phased node are assigned to the
same phase as their latest dependency. Module-imported nodes with no phase-internal
dependencies are placed in the final phase. This preserves the lifecycle ordering
guarantee while allowing modules to compose with phased rollouts.

**Exemplar impact:** The e-commerce exemplar (T4) uses lifecycle phases for ordered
rollout AND the `load-balancer` module. The module-imported nodes (`lb`, `ingress`)
depend on `storefront-nginx` in the `web-tier` phase, so they are placed in or after
the web tier.

---

## 6. Topology Matrix — Exemplar Catalogue

Each exemplar is a complete YAML declaration using the modules above. Full YAML
content is in the research document; this section lists the catalogue with key
characteristics.

| # | App Architecture | Infra Topology | Domain | Key Primitives Used |
|---|---|---|---|---|
| T1 | Single Service | Single Node | Dev blog (Ghost) | Minimal: 1 deployment, 1 service |
| T2 | Single Service | LB Cluster | Company marketing site | load-balancer module |
| T3 | Multi-Tier | Single Node | Local dev stack | Lifecycle phases, variables |
| T4 | Multi-Tier | LB Cluster | E-commerce storefront | load-balancer module, lifecycle phases, invariants |
| T5 | Multi-Tier | HA Multi-AZ | Hospital records | ha-multi-az module, forEach AZs |
| T6 | Multi-Tier | Multi-Region A/P | Retail banking core | multi-region module, lifecycle phases |
| T7 | Microservices | Single Node | Local dev env | Variables (replicas=1) |
| T8 | Microservices | LB Cluster | Food delivery platform | Auto-wiring rules, load-balancer |
| T9 | Microservices | HA Multi-AZ | Equities trading | forEach AZs, ha-multi-az module |
| T10 | Microservices | Multi-Region A/P | Global payments | multi-region module, lifecycle phases |
| T11 | Event-Driven | Single Node | Local dev env | Broker invariant |
| T12 | Event-Driven | LB Cluster | IoT telemetry pipeline | Broker + producers/consumers, fault policy |
| T13 | Sidecar/Mesh | LB Cluster | Logistics fleet tracking | service-mesh module, sidecar injection rule |
| T14 | Sidecar/Mesh | HA Multi-AZ | Insurance claims | service-mesh + ha-multi-az modules, forEach |

### Exemplar Design Principles

- **Real domains, real terminology.** "restaurant-catalog," not "service-a."
- **Each exemplar tells a story** a reader recognises — why this topology fits this
  domain is obvious without explanation.
- **Each exemplar exercises different YAML primitives** — the catalogue collectively
  covers every frontend capability.
- **Exemplars double as tutorials** — they're the onboarding material for users
  learning to deploy with CaseHub.

---

## 7. Test Pyramid

### 7.1 Module: `topology-tests/`

New Maven module, test-scope only. Depends on `casehub-ops-infra` (which ships the
topology modules) and `casehub-desiredstate-yaml` (for `YamlGraphRecorder`).

```xml
<dependencies>
    <dependency>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-ops-infra</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-desiredstate-yaml</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-desiredstate</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 7.2 Layer 1: Compilation Tests (default profile)

For each exemplar YAML (T1–T14):
- Parse YAML through `YamlGraphRecorder`
- Assert correct number of nodes
- Assert correct node types (LoadBalancerSpec, K8sDeploymentSpec, etc.)
- Assert correct dependency edges
- Assert invariants pass (and that violating invariants fails)
- Assert rules fire (auto-wired nodes appear)
- Assert forEach stamps correct number of copies

```java
@Test
void t4_multiTierEcommerceLbCluster() {
    var graph = compileExemplar("multi-tier/lb-cluster/ecommerce-storefront.yaml");

    assertThat(graph.nodes()).hasSize(8);  // ns, lb, ingress, 3 deploys, 2 svc
    assertNodeType(graph, "storefront-lb.lb", "load_balancer");
    assertNodeType(graph, "product-db", "k8s_deployment");
    assertDependency(graph, "catalog-api", "product-db");
    assertDependency(graph, "storefront-nginx", "catalog-api");
    assertDependency(graph, "storefront-lb.lb", "storefront-nginx");
}
```

### 7.3 Layer 2: Reconciliation Tests (`-Preconciliation`)

Wire compiled graphs through the full reconciliation loop with a
`FailableResourceProvisioner` test stub that supports deterministic failure
injection:

```java
public class FailableResourceProvisioner implements ResourceProvisioner {
    private final Map<NodeId, Integer> failUntilAttempt = new ConcurrentHashMap<>();

    public void failNode(NodeId nodeId, int failForAttempts) {
        failUntilAttempt.put(nodeId, failForAttempts);
    }

    @Override
    public ProvisionOutcome execute(ProvisionTask task) {
        Integer remaining = failUntilAttempt.get(task.nodeId());
        if (remaining != null && remaining > 0) {
            failUntilAttempt.put(task.nodeId(), remaining - 1);
            return new ProvisionOutcome(false, null, null, "injected failure");
        }
        // delegate to InMemoryResourceProvisioner for success path
    }
}
```

- Assert `TransitionPlanner` produces correct provision/deprovision steps
- Assert topological ordering (namespace before deployments, etc.)
- Assert drift detection works (modify a spec, verify DRIFTED status)
- Assert fault policies fire on repeated failures (using `failNode()` injection)
- Assert escalation path: threshold breach → review node → human gating

### 7.4 Layer 3: Live Deployment Tests (`-Pinfra-live`)

Deploy real Docker images to K8s (kind/minikube for CI). Scoped to K8s-native
types (`k8s_namespace`, `k8s_deployment`, `k8s_service`, `k8s_ingress`) — the
new topology types (`load_balancer`, `dns_failover`, `data_replication`,
`mesh_control_plane`, `sidecar_proxy`) are verified at Layer 1 (compilation)
and Layer 2 (reconciliation) only, since real cloud/mesh infrastructure
provisioners are out of scope:

- Assert namespace creation
- Assert deployments reach Ready state
- Assert services resolve
- Assert health checks pass
- Assert reconciliation loop converges after manual drift injection

---

## 8. Decisions

| # | Decision | Choice | Alternatives | Rationale |
|---|---|---|---|---|
| D1 | Scope | App arch × infra topo matrix | Single dimension | Intersections prove real-world expressiveness |
| D2 | App architectures | All 5 | Start with 2-3 | Complete coverage, each exercises different primitives |
| D3 | Infra topologies | All 4 | Start with 2 | Dev through enterprise DR |
| D4 | Verification | 3-layer pyramid, Maven profiles | Single layer | Confidence at each level without slowing every build |
| D5 | Toy services | Real Docker images | Synthetic stubs | Health checks, communication, replication testable |
| D6a | Topology modules | `infra/` resources | New module, api/ | Ships with infra.jar, natural home for infra types |
| D6b | Tests + exemplars | New `topology-tests/` module | Inside app/ or testing/ | Clean separation |
| D7 | YAML format | Topology-aware first-class | Metadata, sugar | Topology informs validation and node generation |
| D8 | Implementation | YAML-native composition | New TopologyGoalCompiler | Existing frontend has every primitive needed |
| D9 | Gap analysis | Extend, don't reinvent | Build new compiler | Only InfraNodeSpec + modules + GOAP actions are new |
| D10 | CaseHub stack | Full: TransitionPlanner + GOAP + Engine + Lifecycle | Parallel machinery | Each layer handles different timescale |
| D11 | YAML role | Primary interface; Java is escape hatch | Parity goal | Mass appeal, tutorials, onboarding — YAML-first |

---

## 9. Implementation Phases

| Phase | What | Type | Depends On |
|---|---|---|---|
| 1 | InfraNodeSpec extensions (5 new records + 3 supporting enums + `@NodeTypeId` on all variants) + `handledTypes()` registration in InfraNodeProvisioner and InfraActualStateAdapter + `parseSpec()` cases in InfraGoalCompiler + `NodeSpecFactory` SPI + `NodeSpecRegistry` generalization + `YamlDesiredStateProcessor` InfraNodeSpec discovery | Java | — |
| 2 | Topology modules (4 YAML modules) + `createYamlLifecycleGoalCompiler` module expansion support | YAML + Java | Phase 1 |
| 3 | Topology exemplars (14 YAML declarations) + compilation tests | YAML + Java | Phase 2 |
| 4 | Reconciliation integration tests | Java | Phase 3 |
| 5 | Live K8s deployment tests | Java + infra | Phase 4 |
| 6 | GOAP migration actions (separate spec) | Java | Phase 3 |
| 7 | Service lifecycle integration (separate spec) | Java | Phase 5 |

Phases 1-5 are this spec. Phases 6-7 are separate specs building on this foundation.

---

## 10. References

- `docs/research/2026-08-29-canonical-deployment-topologies.md` — full research
- `casehub-desiredstate/yaml/runtime/` — YAML frontend implementation
- `casehub-desiredstate/examples/webapp-yaml/` — tutorial YAML examples
- `api/src/main/java/io/casehub/ops/api/infra/InfraNodeSpec.java` — sealed hierarchy
- `infra/src/main/java/io/casehub/ops/infra/InfraNodeProvisioner.java` — backend-id dispatch
- `casehub-engine/api/src/main/java/io/casehub/engine/plan/goap/GoapPlanner.java` — GOAP planner
- `casehub-desiredstate/runtime/src/main/java/io/casehub/desiredstate/runtime/TransitionPlanner.java` — steady-state planner

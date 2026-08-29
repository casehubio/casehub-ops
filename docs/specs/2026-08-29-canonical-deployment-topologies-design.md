# Canonical Deployment Topologies — Design Spec

**Date:** 2026-08-29
**Research:** `docs/research/2026-08-29-canonical-deployment-topologies.md`
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
- New `InfraNodeProvisioner` handlers for those types
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
                           │ Jandex + NodeSpecRegistry
┌──────────────────────────▼──────────────────────────────────────┐
│              YamlGraphRecorder (existing — no changes)            │
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
            │   (sealed type dispatch)     │
            └─────────────────────────────┘
```

### 3.2 What's New vs What Exists

| Component | Status | Work Required |
|---|---|---|
| YamlGraphRecorder | Exists | None |
| ForEachExpander | Exists | None |
| ModuleExpander | Exists | None |
| GraphRuleEngine | Exists | None |
| GraphInvariantEngine | Exists | None |
| YamlLifecycleCompiler | Exists | None |
| NodeSpecRegistry | Exists | None |
| VariableResolver | Exists | None |
| TransitionPlanner | Exists | None |
| InfraNodeProvisioner | Exists | New handler cases for new spec types |
| InfraNodeSpec hierarchy | Exists | 5 new sealed variants |
| Topology modules | **New** | 4 YAML modules |
| Topology exemplars | **New** | ~14 YAML declarations |
| topology-tests module | **New** | Maven module + test pyramid |

---

## 4. New InfraNodeSpec Sealed Variants

Each is a Java record in `api/src/main/java/io/casehub/ops/api/infra/`, extending the
existing `InfraNodeSpec` sealed interface. Builder DSLs and annotations enable the
platform generator to produce YAML schema and TS types.

### 4.1 LoadBalancerSpec

Represents a load balancer (application or network layer) that distributes traffic
across target services.

```java
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
  type: load-balancer
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
to carry `backendId` for backend routing. The `InfraNodeProvisioner` gets new cases
in its sealed type switch.

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
    type: load-balancer
    spec:
      name: "${var.target_service}-lb"
      namespace: ${var.namespace}
      type: ${var.lb_type}
      healthCheckPath: ${var.health_check_path}
      healthCheckIntervalSeconds: 30
      targetServices: ["${var.target_service}"]

  ingress:
    type: k8s-ingress
    dependsOn: [lb]
    spec:
      namespace: ${var.namespace}
      name: "${var.target_service}-ingress"
      rules:
        - host: "${var.target_service}.example.com"
          path: /
          serviceName: ${var.target_service}
          servicePort: 80

invariants:
  lb-has-target:
    match:
      lb: { type: load-balancer }
    message: "Load balancer must have at least one target service"
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
    type: k8s-deployment
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
  minimum-three-zones:
    message: "HA multi-AZ requires at least 3 availability zones"
```

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
    type: mesh-control-plane
    spec:
      name: mesh-control-plane
      namespace: ${var.namespace}
      image: ${var.control_plane_image}
      replicas: ${var.control_plane_replicas}

rules:
  sidecar-depends-on-control-plane:
    match:
      proxy: { type: sidecar-proxy }
    notExists:
      cp: { type: mesh-control-plane, of: proxy, direction: DEPENDENCIES }
    actions:
      - addDependency:
          from: "${match.proxy.id}"
          to: mesh-control-plane
```

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
    type: data-replication
    spec:
      name: "${var.primary_cluster}-to-${var.dr_cluster}"
      sourceCluster: ${var.primary_cluster}
      targetCluster: ${var.dr_cluster}
      mode: ${var.replication_mode}
      sourceDatabase: ${var.source_database}
      lagThresholdSeconds: 30

  dns-failover:
    type: dns-failover
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
      fo: { type: dns-failover }
    directDep:
      repl: { type: data-replication, of: fo, direction: DEPENDENCIES }
    message: "DNS failover requires data replication to be configured first"
```

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

Wire compiled graphs through the full reconciliation loop with stubbed
`InfraBackend` implementations:
- Assert `TransitionPlanner` produces correct provision/deprovision steps
- Assert topological ordering (namespace before deployments, etc.)
- Assert drift detection works (modify a spec, verify DRIFTED status)
- Assert fault policies fire on repeated failures

### 7.4 Layer 3: Live Deployment Tests (`-Pinfra-live`)

Deploy real Docker images to K8s (kind/minikube for CI):
- Assert namespace creation
- Assert deployments reach Ready state
- Assert services resolve
- Assert load balancer routes traffic
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
| 1 | InfraNodeSpec extensions (5 new records) + provisioner handlers | Java | — |
| 2 | Topology modules (4 YAML modules) | YAML | Phase 1 |
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
- `infra/src/main/java/io/casehub/ops/infra/InfraNodeProvisioner.java` — sealed dispatch
- `casehub-engine/api/src/main/java/io/casehub/engine/plan/goap/GoapPlanner.java` — GOAP planner
- `casehub-desiredstate/runtime/src/main/java/io/casehub/desiredstate/runtime/TransitionPlanner.java` — steady-state planner

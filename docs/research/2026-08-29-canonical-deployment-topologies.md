# Canonical Deployment Topologies — Research

**Date:** 2026-08-29
**Author:** Mark Proctor + Claude
**Status:** Research — informing design spec
**Scope:** General software deployment first; CaseHub layers on top

---

## 1. Problem Statement

The CaseHub desired-state system has matured significantly. The YAML frontend now
unifies Terraform, Ansible, and Helm-style declarations into a single desired-state
model. Four domain modules (infra, deployment, compliance, IoT) validate the generic
runtime across diverse operational domains. The `ApplicationGoalCompiler` can compile
service definitions into K8s namespace → deployment → service node chains with
dependency ordering.

What's missing: **a systematic proof that this system can express and manage the
full range of real-world deployment architectures.** We need to identify the canonical
deployment topologies that exist in production software, build representative examples
with real domain language, and verify that the desired-state system handles them
correctly — from YAML compilation through reconciliation to live deployment.

---

## 2. Two Orthogonal Dimensions

Deployment topologies decompose into two independent dimensions. Every real-world
deployment is a point in the matrix formed by these two axes.

### 2.1 Application Architecture — How Services Relate

The structural patterns that define how an application's components interact.

| Architecture | Description | Characteristic Pattern |
|---|---|---|
| **Single Service** | One container, one responsibility. A blog engine, a static site, a CLI tool packaged as a service. | No inter-service dependencies. One deployment, one service, optional ingress. |
| **Multi-Tier** | Classic layered architecture: presentation → application → data. Each tier scales independently. The most common production pattern globally. | Linear dependency chain. Web tier depends on app tier; app tier depends on data tier. Tiers are structurally different (proxy vs API vs database). |
| **Microservices** | 3+ independent services organised around business capabilities. Each is independently deployable, often with its own data store. Service discovery is a first-class concern. | Mesh of dependencies. No single linear chain. Services communicate via REST/gRPC. Registry or DNS-based discovery. |
| **Event-Driven** | Services communicate through an event bus or message broker rather than direct calls. Producers emit events; consumers react asynchronously. Decouples deployment from communication. | Broker infrastructure (RabbitMQ, Kafka, NATS) as a shared dependency. Services depend on the broker, not on each other. Fan-out and fan-in patterns. |
| **Sidecar / Service Mesh** | Each application container is paired with a proxy sidecar (Envoy, Linkerd) that handles cross-cutting concerns: mTLS, observability, traffic shaping, retries. The mesh is the network. | N+1 containers per service (app + sidecar). Control plane as a shared dependency. Traffic policies declared separately from application code. |

### 2.2 Infrastructure Topology — How Services Are Physically Arranged

The physical/logical arrangement of compute, networking, and storage.

| Topology | Description | Characteristic Pattern |
|---|---|---|
| **Single Node** | Everything runs on one machine. Development environments, edge deployments, IoT gateways, small-scale production (a personal blog, an internal tool). | One cluster (or bare metal), one namespace. No redundancy. k3s, MicroK8s, Docker Compose. |
| **Load-Balanced Cluster** | Multiple nodes behind a load balancer in a single region/AZ. The baseline for "production" in most organisations. | N worker nodes, 1+ control plane nodes. Ingress controller or cloud LB distributes traffic. Horizontal scaling within one failure domain. |
| **HA Multi-AZ** | Replicated across 2-3 availability zones within a single region. Survives AZ failure without manual intervention. The standard for regulated industries. | Per-AZ node pools. Anti-affinity rules spread replicas across zones. Persistent storage uses zone-aware provisioners. Control plane is HA (stacked or external etcd). |
| **Multi-Region Active-Passive** | Primary region serves all traffic. Secondary region maintains a warm standby for disaster recovery. Data replication is async or semi-sync. | Two independent clusters. DNS-based failover (Route53, Cloud DNS). Database replication lag is the binding constraint. RTO/RPO targets drive the architecture. |

**Deferred:** Multi-Region Active-Active requires data replication semantics (conflict
resolution, CRDTs, consensus protocols) that go beyond infrastructure topology. It's a
data architecture problem layered on top of infra topology.

---

## 3. The Topology Matrix

Not every intersection of application architecture × infrastructure topology is
meaningful. The following matrix identifies the ~14 intersections worth proving,
each mapped to a recognisable real-world domain.

| | Single Node | LB Cluster | HA Multi-AZ | Multi-Region A/P |
|---|---|---|---|---|
| **Single Service** | Personal dev blog (Ghost) | Company marketing site | — | — |
| **Multi-Tier** | Local dev stack | E-commerce storefront (Shopify-like) | Hospital records system | Retail banking core |
| **Microservices** | Local dev environment | Food delivery platform (Deliveroo-like) | Equities trading platform | Global payments processor |
| **Event-Driven** | Local dev environment | IoT sensor telemetry pipeline | — | — |
| **Sidecar / Mesh** | — | Logistics fleet tracking | Insurance claims processing | — |

### Why These Domains

Each domain was chosen because it makes the topology constraints obvious to the reader:

- **Hospital records on HA Multi-AZ:** Healthcare data can't go down. AZ failover isn't
  optional — it's regulatory (HIPAA, NHS Digital). A reader understands why HA matters here
  without explanation.

- **Equities trading on HA Multi-AZ microservices:** Sub-millisecond matters. Independent
  services (pricing, order routing, risk, settlement) must be independently deployable but
  collectively resilient. Service discovery is mission-critical.

- **Retail banking on Multi-Region A/P:** Banks must survive regional outages. Active-passive
  with async replication is the standard pattern because consistency trumps availability for
  financial transactions.

- **IoT telemetry on event-driven:** Sensors emit events. The pipeline ingests, transforms,
  and stores. The broker (Kafka/RabbitMQ) is the architectural centre of gravity. No request/
  response — pure event flow.

- **Logistics fleet tracking on sidecar/mesh:** Hundreds of microservices tracking vehicles,
  routes, parcels. mTLS between services is non-negotiable (PII in transit). Service mesh
  handles the cross-cutting security and observability that no individual service should own.

---

## 4. Three Verification Layers

Each topology intersection is verified at three levels of confidence, gated by Maven
profiles so they don't all run on every build.

| Layer | Maven Profile | What It Proves | Speed |
|---|---|---|---|
| **Compilation** | (default) | YAML → DesiredStateGraph. Correct nodes, correct dependencies, correct node types. | Seconds |
| **Reconciliation** | `-Preconciliation` | Full reconciliation loop with stubbed backends. TransitionPlanner produces correct transition plans. Drift detection works. Fault policies fire correctly. | Seconds–minutes |
| **Live Deployment** | `-Pinfra-live` | Real Docker images (nginx, postgres, redis, RabbitMQ) deployed to real K8s. Health checks pass. Services communicate. Load balancers route. | Minutes |

### Toy Services (Real Images)

| Role | Image | Why |
|---|---|---|
| Web tier / reverse proxy | `nginx:1.25` | Industry standard, tiny, configurable |
| Application tier / API | `kennethreitz/httpbin` or `hashicorp/http-echo` | Returns request info, useful for verifying routing |
| Data tier / relational DB | `postgres:16` | Stateful, supports replication, industry standard |
| Data tier / cache | `redis:7` | In-memory, fast health checks, replication support |
| Message broker | `rabbitmq:3-management` | Lightweight, management UI, supports clustering |
| Sidecar proxy | `envoyproxy/envoy:v1.28` | Industry standard service mesh data plane |

---

## 5. The DesiredState YAML Frontend — What Already Exists

### Critical Discovery

The `casehub-desiredstate-yaml` module provides a YAML frontend far more powerful
than what the ops domain modules currently use. Auditing the code reveals a complete
declarative toolkit:

| Capability | Mechanism | Location |
|---|---|---|
| **Variables** | `${var.x}` substitution with per-environment overrides | `VariableResolver` |
| **Conditional nodes** | `when: "${var.feature_enabled}"` — node vanishes from graph when false | `YamlGraphRecorder` |
| **Optional deps** | `{ node: x, optional: true }` — silently dropped when target excluded | `YamlNode` |
| **ForEach** | Named iteration groups, stamps copies per item | `ForEachExpander` |
| **Modules** | Parameterised imports with aliases, promoted rules + invariants | `ModuleExpander` |
| **Invariants** | Compile-time structural validation with match/directDep patterns | `GraphInvariantEngine` |
| **Rules** | Auto-wiring that fires in a loop until graph stabilises | `GraphRuleEngine` |
| **Fault policies** | Tiered escalation from YAML (threshold → review node → human gating) | `YamlFaultPolicyBuilder` |
| **Lifecycle phases** | Ordered rollout stages with carry-forward nodes | `YamlLifecycleCompiler` |
| **NodeSpec registry** | Pluggable type→class mapping — `NodeSpecRegistry.of(map)` | `NodeSpecRegistry` |

The webapp tutorial examples (`casehub-desiredstate/examples/webapp-yaml/`) demonstrate
all of these working together: an e-commerce order pipeline with variables, conditional
gift-wrapping, auto-wiring notification rules, invariant-enforced fraud checks, forEach
warehouse replication, and reusable notification modules.

### The Generation Architecture — Java Canonical, YAML and TS Generated

CaseHub follows a layered generation architecture across all repos:

```
Java records + annotations     (canonical — the source of truth)
        │
        ├── Builder DSLs       (ergonomic Java API for programmatic use)
        │
        ├──── generates ────►  YAML schema    (declarative API for end users)
        │                          │
        │                          └──►  NodeSpecRegistry auto-populates via Jandex
        │
        └──── generates ────►  TypeScript types  (UI layer, frontend tooling)
```

Eidos is currently extracting the shared generator to `casehub-platform` — all repos
will use the same pipeline. This means:

1. **New InfraNodeSpec variants are Java records** — that's the extension point
2. **YAML schema follows automatically** — the generator produces it from the Java types
3. **NodeSpecRegistry populates via Jandex** — no manual registration needed
4. **TS types follow automatically** — UI can render and validate any topology

**YAML parity with Java is a deliberate design goal.** End users should be able to do
almost anything they can do programmatically from pure YAML documents. This drives mass
appeal — YAML-first tutorials are accessible to DevOps engineers, platform teams, and
compliance officers who may never write Java.

This validates the approach: define topology types as Java records in
`casehub-ops-api`, let the generation pipeline produce YAML schema, and write topology
modules as pure YAML using the generated schema. The topology work exercises the full
generation pipeline end-to-end.

### Implication: No New Compiler Needed

**The "TopologyGoalCompiler" as a new Java class is the wrong approach.** The existing
YAML frontend already provides every primitive needed to express deployment topologies.

A deployment topology is a composition of existing primitives:

| YAML Primitive | Topology Role |
|---|---|
| **Module** | Reusable infra pattern (LB module, HA module, mesh module) |
| **Invariant** | Topology constraint enforcement ("multi-tier must have web→app→data chain") |
| **Rule** | Auto-wiring ("for every service with `sidecar: true`, add envoy proxy") |
| **forEach** | AZ replication ("stamp this service across 3 availability zones") |
| **when:** | Optional features ("include mesh control plane only when `mesh_enabled`") |
| **Lifecycle phase** | Ordered rollout ("infra first, then services, then monitoring") |
| **Variables** | Per-environment config ("replicas: 1 in dev, 3 in prod") |
| **NodeSpec registry** | Pluggable infra types ("k8s-deployment", "load-balancer", "sidecar") |

### What's Actually New — The Real Gap Analysis

The gaps are much smaller than initially assumed:

| Gap | Type | Description |
|---|---|---|
| **InfraNodeSpec extensions** | Java | New sealed variants: `LoadBalancerSpec`, `ServiceMeshControlPlaneSpec`, `SidecarSpec`, `DnsFailoverSpec`, `DataReplicationSpec`. (`K8sIngressSpec` already exists.) |
| **NodeProvisioner handlers** | Java | Handlers for the new spec types in `InfraNodeProvisioner` |
| **Topology modules** | YAML | Pre-built modules for each infra pattern: `load-balancer.yaml`, `ha-multi-az.yaml`, `multi-region.yaml`, `service-mesh.yaml` |
| **Topology invariants** | YAML | Per-topology structural validation rules |
| **Topology rules** | YAML | Auto-wiring rules per topology (e.g., sidecar injection) |
| **GOAP migration actions** | Java | Actions for topology type transitions (single-node → HA, etc.) |
| **topology-tests module** | Java + YAML | YAML exemplars + 3-tier test pyramid |

Items 3, 4, and 5 are **pure YAML** — no Java code required. The existing YAML
frontend compiles them into DesiredStateGraphs using the existing `YamlGraphRecorder`.
This is the power of the desiredstate YAML system: new deployment topologies are
declared, not programmed.

### Design Direction: Topology as YAML-Native Composition

Instead of a new `TopologyGoalCompiler`, the topology is expressed as a YAML
declaration that composes modules, enforces invariants, applies rules, and
uses forEach for replication — all within the existing YAML frontend.

### Example: Multi-Tier E-Commerce on Load-Balanced Cluster

Uses lifecycle phases for ordered rollout, the `load-balancer` module for traffic
management, and a multi-tier invariant to enforce the dependency chain.

```yaml
desiredState:
  namespace: ecommerce
  name: storefront-prod

variables:
  replicas: 3
  db_storage: 50Gi
  payment_provider: stripe

# Ordered rollout: infra → data → application → web → traffic
lifecycle:
  phases:
    - id: infrastructure
      completionCondition: allPresent
      nodes:
        storefront-namespace:
          type: k8s-namespace
          spec:
            name: storefront
            labels: { managed-by: casehub-ops }

    - id: data-tier
      completionCondition: allPresent
      nodes:
        product-db:
          type: k8s-deployment
          dependsOn: [storefront-namespace]
          spec:
            namespace: storefront
            name: product-db
            image: postgres:16
            replicas: 1
            ports: [{ containerPort: 5432, servicePort: 5432 }]
            env: { POSTGRES_DB: catalog }

    - id: application-tier
      completionCondition: allPresent
      nodes:
        catalog-api:
          type: k8s-deployment
          dependsOn: [product-db]
          spec:
            namespace: storefront
            name: catalog-api
            image: ecom/catalog-api:2.1
            replicas: ${var.replicas}
            ports: [{ containerPort: 8080, servicePort: 8080 }]
            env:
              DB_HOST: product-db
              DB_NAME: catalog
            healthCheck: { path: /health, port: 8080 }

        catalog-service:
          type: k8s-service
          dependsOn: [catalog-api]
          spec:
            namespace: storefront
            name: catalog-api
            port: 8080
            targetPort: 8080

    - id: web-tier
      completionCondition: allPresent
      nodes:
        storefront-nginx:
          type: k8s-deployment
          dependsOn: [catalog-api]
          spec:
            namespace: storefront
            name: storefront-nginx
            image: nginx:1.25
            replicas: ${var.replicas}
            ports: [{ containerPort: 80, servicePort: 80 }]

# Reusable module adds LB + ingress wired to the web tier
imports:
  - module: load-balancer
    as: storefront-lb
    parameters:
      target_service: storefront-nginx
      health_check_path: /health
      namespace: storefront

# Multi-tier invariant: web → app → data dependency chain must exist
invariants:
  web-depends-on-app:
    match:
      web: { type: k8s-deployment, spec: { name: storefront-nginx } }
    directDep:
      app: { type: k8s-deployment, of: web, direction: DEPENDENCIES }
    message: "Web tier must depend on application tier"

  app-depends-on-data:
    match:
      app: { type: k8s-deployment, spec: { name: catalog-api } }
    directDep:
      db: { type: k8s-deployment, of: app, direction: DEPENDENCIES }
    message: "Application tier must depend on data tier"

faultPolicy:
  - faultTypes: [PROVISION_FAILED]
    nodeTypes: [k8s-deployment]
    ignoreTypes: [deployment-review]
    namespace: deployment-escalation
    tiers:
      - threshold: 3
        reviewNode:
          type: deployment-review
          humanGating: ALL
          spec:
            targetNodeId: "${fault.nodeId}"
            errorDetail: "${fault.detail}"
```

### Example: Microservices Food Delivery on HA Multi-AZ

Uses `forEach` to stamp services across availability zones, the `ha-multi-az`
module for HA infrastructure, and a rule to auto-wire service discovery.

```yaml
desiredState:
  namespace: delivery
  name: food-delivery-platform

variables:
  replicas_per_az: 1
  region: eu-west-1

iterations:
  availability-zones:
    as: az
    in: ["eu-west-1a", "eu-west-1b", "eu-west-1c"]

nodes:
  delivery-namespace:
    type: k8s-namespace
    spec:
      name: delivery
      labels: { managed-by: casehub-ops }

  # Each service stamped across 3 AZs
  restaurant-catalog:
    type: k8s-deployment
    forEach: availability-zones
    dependsOn: [delivery-namespace]
    spec:
      namespace: delivery
      name: "restaurant-catalog-${each.az}"
      image: delivery/restaurant-catalog:3.2
      replicas: ${var.replicas_per_az}
      ports: [{ containerPort: 8080, servicePort: 80 }]
      labels: { app: restaurant-catalog, az: "${each.az}" }
      healthCheck: { path: /health, port: 8080 }

  order-service:
    type: k8s-deployment
    forEach: availability-zones
    dependsOn: [restaurant-catalog, rider-dispatch]
    spec:
      namespace: delivery
      name: "order-service-${each.az}"
      image: delivery/order-service:2.8
      replicas: ${var.replicas_per_az}
      ports: [{ containerPort: 8080, servicePort: 80 }]
      env:
        CATALOG_URL: http://restaurant-catalog
        RIDER_URL: http://rider-dispatch
      labels: { app: order-service, az: "${each.az}" }

  rider-dispatch:
    type: k8s-deployment
    forEach: availability-zones
    dependsOn: [delivery-namespace]
    spec:
      namespace: delivery
      name: "rider-dispatch-${each.az}"
      image: delivery/rider-dispatch:1.5
      replicas: ${var.replicas_per_az}
      ports: [{ containerPort: 8080, servicePort: 80 }]
      labels: { app: rider-dispatch, az: "${each.az}" }

  payment-gateway:
    type: k8s-deployment
    forEach: availability-zones
    dependsOn: [order-service]
    spec:
      namespace: delivery
      name: "payment-gateway-${each.az}"
      image: delivery/payment-gateway:4.0
      replicas: ${var.replicas_per_az}
      ports: [{ containerPort: 8080, servicePort: 80 }]
      labels: { app: payment-gateway, az: "${each.az}" }

imports:
  - module: ha-multi-az
    as: delivery-ha
    parameters:
      namespace: delivery
      region: ${var.region}
      zones: ["eu-west-1a", "eu-west-1b", "eu-west-1c"]

# Auto-wire: every deployment gets a ClusterIP service
rules:
  auto-service-per-deployment:
    match:
      deploy: { type: k8s-deployment }
    notExists:
      svc: { type: k8s-service, of: deploy, direction: DEPENDENTS }
    actions:
      - addNode:
          id: "svc-${match.deploy.id}"
          type: k8s-service
          spec:
            namespace: delivery
            name: "${match.deploy.spec.name}"
            port: 80
            targetPort: 8080
      - addDependency:
          from: "svc-${match.deploy.id}"
          to: "${match.deploy.id}"
```

### Example: Event-Driven IoT Telemetry Pipeline

Uses the broker as the architectural centre. An invariant enforces that every
producer/consumer depends on the broker. A rule auto-wires dead-letter queues.

```yaml
desiredState:
  namespace: telemetry
  name: iot-sensor-pipeline

variables:
  broker_replicas: 3
  timeseries_storage: 100Gi

nodes:
  telemetry-namespace:
    type: k8s-namespace
    spec:
      name: telemetry
      labels: { managed-by: casehub-ops }

  # The broker — architectural centre of gravity
  sensor-broker:
    type: k8s-deployment
    dependsOn: [telemetry-namespace]
    spec:
      namespace: telemetry
      name: sensor-broker
      image: rabbitmq:3-management
      replicas: ${var.broker_replicas}
      ports:
        - { containerPort: 5672, servicePort: 5672 }
        - { containerPort: 15672, servicePort: 15672 }

  # Producers — ingest sensor data
  sensor-ingestion:
    type: k8s-deployment
    dependsOn: [sensor-broker]
    spec:
      namespace: telemetry
      name: sensor-ingestion
      image: telemetry/sensor-ingestion:1.0
      replicas: 2
      ports: [{ containerPort: 8080, servicePort: 80 }]
      env:
        BROKER_URL: amqp://sensor-broker:5672
        EXCHANGE: sensor.readings

  # Consumers — process sensor data
  anomaly-detector:
    type: k8s-deployment
    dependsOn: [sensor-broker]
    spec:
      namespace: telemetry
      name: anomaly-detector
      image: telemetry/anomaly-detector:2.1
      replicas: 3
      env:
        BROKER_URL: amqp://sensor-broker:5672
        QUEUE: sensor.readings.anomaly

  timeseries-writer:
    type: k8s-deployment
    dependsOn: [sensor-broker, timeseries-db]
    spec:
      namespace: telemetry
      name: timeseries-writer
      image: telemetry/timeseries-writer:1.3
      replicas: 2
      env:
        BROKER_URL: amqp://sensor-broker:5672
        QUEUE: sensor.readings.store
        TIMESCALEDB_URL: jdbc:postgresql://timeseries-db:5432/readings

  timeseries-db:
    type: k8s-deployment
    dependsOn: [telemetry-namespace]
    spec:
      namespace: telemetry
      name: timeseries-db
      image: timescale/timescaledb:latest-pg16
      replicas: 1
      ports: [{ containerPort: 5432, servicePort: 5432 }]

# Event-driven invariant: every non-broker deployment must depend on the broker
invariants:
  all-services-depend-on-broker:
    match:
      svc: { type: k8s-deployment }
    when: "${match.svc.spec.name != 'sensor-broker'}"
    directDep:
      broker: { type: k8s-deployment, of: svc, direction: DEPENDENCIES }
    message: "Service '${match.svc.id}' must depend on the message broker"

faultPolicy:
  - faultTypes: [PROVISION_FAILED]
    nodeTypes: [k8s-deployment]
    ignoreTypes: [telemetry-review]
    namespace: telemetry-escalation
    tiers:
      - threshold: 3
        reviewNode:
          type: telemetry-review
          humanGating: ALL
          spec:
            targetNodeId: "${fault.nodeId}"
            errorDetail: "${fault.detail}"
```

### Example: Sidecar/Mesh Logistics Tracking on HA Multi-AZ

Uses a rule to auto-inject sidecar proxies alongside every service, and the
`service-mesh` module for control plane infrastructure.

```yaml
desiredState:
  namespace: logistics
  name: fleet-tracking-mesh

variables:
  replicas: 3
  sidecar_image: envoyproxy/envoy:v1.28
  sidecar_cpu: 100m
  sidecar_memory: 128Mi

iterations:
  availability-zones:
    as: az
    in: ["us-east-1a", "us-east-1b", "us-east-1c"]

nodes:
  logistics-namespace:
    type: k8s-namespace
    spec:
      name: logistics
      labels: { managed-by: casehub-ops }

  fleet-tracker:
    type: k8s-deployment
    forEach: availability-zones
    dependsOn: [logistics-namespace]
    spec:
      namespace: logistics
      name: "fleet-tracker-${each.az}"
      image: logistics/fleet-tracker:3.0
      replicas: 1
      ports: [{ containerPort: 8080, servicePort: 80 }]
      labels: { app: fleet-tracker, az: "${each.az}", mesh-inject: "true" }
      env:
        ROUTE_URL: http://route-optimizer
        PARCEL_URL: http://parcel-service

  route-optimizer:
    type: k8s-deployment
    forEach: availability-zones
    dependsOn: [logistics-namespace]
    spec:
      namespace: logistics
      name: "route-optimizer-${each.az}"
      image: logistics/route-optimizer:2.5
      replicas: 1
      ports: [{ containerPort: 8080, servicePort: 80 }]
      labels: { app: route-optimizer, az: "${each.az}", mesh-inject: "true" }

  parcel-service:
    type: k8s-deployment
    forEach: availability-zones
    dependsOn: [logistics-namespace]
    spec:
      namespace: logistics
      name: "parcel-service-${each.az}"
      image: logistics/parcel-service:4.1
      replicas: 1
      ports: [{ containerPort: 8080, servicePort: 80 }]
      labels: { app: parcel-service, az: "${each.az}", mesh-inject: "true" }
      env:
        WAREHOUSE_URL: http://warehouse-api

  warehouse-api:
    type: k8s-deployment
    forEach: availability-zones
    dependsOn: [logistics-namespace]
    spec:
      namespace: logistics
      name: "warehouse-api-${each.az}"
      image: logistics/warehouse-api:1.8
      replicas: 1
      ports: [{ containerPort: 8080, servicePort: 80 }]
      labels: { app: warehouse-api, az: "${each.az}", mesh-inject: "true" }

imports:
  - module: service-mesh
    as: logistics-mesh
    parameters:
      namespace: logistics
      control_plane_image: istio/pilot:1.20
      control_plane_replicas: 3

# Auto-inject sidecar proxy for every deployment with mesh-inject label
rules:
  sidecar-injection:
    match:
      app: { type: k8s-deployment }
    notExists:
      proxy: { type: sidecar-proxy, of: app, direction: DEPENDENTS }
    actions:
      - addNode:
          id: "proxy-${match.app.id}"
          type: sidecar-proxy
          spec:
            image: ${var.sidecar_image}
            targetService: "${match.app.id}"
            resources: { cpu: ${var.sidecar_cpu}, memory: ${var.sidecar_memory} }
      - addDependency:
          from: "proxy-${match.app.id}"
          to: "${match.app.id}"

# Mesh invariant: control plane must exist
invariants:
  mesh-control-plane-exists:
    match:
      proxy: { type: sidecar-proxy }
    directDep:
      cp: { type: mesh-control-plane, of: proxy, direction: DEPENDENCIES }
    message: "Sidecar proxy requires mesh control plane — import the service-mesh module"

---

## 6. The Architectural Breakthrough — CaseHub's Own Stack as the Deployment Engine

The most significant finding in this research is not the topology matrix itself — it's
the realisation that CaseHub already has every abstraction needed to manage deployment
topologies, and they compose naturally.

### Three Distinct Planning Problems

Deployment management involves three planning problems that operate at different
timescales and require different reasoning:

| Problem | Timescale | Reasoning Required |
|---|---|---|
| **Day-to-day reconciliation** | Seconds–minutes | Simple diff: desired vs actual → add/remove nodes |
| **Topology migration** | Minutes–hours | Multi-step planning: ordered actions with preconditions |
| **Ongoing operations** | Days–months | Continuous monitoring across multiple dimensions |

Each of these maps to an existing CaseHub component:

### 6.1 TransitionPlanner — Steady-State Reconciliation

The `TransitionPlanner` in `casehub-desiredstate` handles the simple case: compare the
desired state graph against actual state, classify nodes as needing PROVISION or
DEPROVISION, topologically sort additions (Kahn's algorithm), and execute.

This is correct and sufficient for **day-to-day drift**: a container crashes, a config
changes, a node goes DRIFTED. The planner detects the divergence and converges back to
desired state. Single-pass, no multi-step reasoning needed.

```
TransitionPlanner.plan(desired, actual) → TransitionPlan(removals, additions)
```

**Strength:** Fast, simple, deterministic. Runs on every reconciliation cycle (seconds
to minutes).

**Limitation:** Cannot handle topology *type* changes. Migrating from single-node to
HA multi-AZ is not a diff — it's a multi-step plan where ordering matters and
intermediate states must be valid.

### 6.2 GOAP Planner — Topology Migration Planning

The `GoapPlanner` in `casehub-engine` is a full A* search planner with:

- **Preconditions:** What must be true before an action can execute
- **Effects:** What becomes true after an action executes
- **Costs:** Numeric cost per action (enables optimal plan selection)
- **Soft preconditions:** Preferred but not required conditions (penalty-based)
- **Backward pruning:** Eliminates irrelevant actions before search
- **Forward simulation:** Removes redundant actions from the plan

This is exactly the reasoning engine needed for topology migrations. Consider migrating
a retail banking system from single-node to multi-region active-passive:

```
World State (initial):
  single-node-running: TRUE
  primary-region-provisioned: FALSE
  dr-region-provisioned: FALSE
  data-replicated: FALSE
  failover-configured: FALSE
  traffic-migrated: FALSE

Goal Conditions:
  primary-region-provisioned: TRUE
  dr-region-provisioned: TRUE
  data-replicated: TRUE
  failover-configured: TRUE
  traffic-migrated: TRUE

Available Actions:
  provision-primary-region:
    preconditions: {}
    effects: {primary-region-provisioned: TRUE}
    cost: 5.0

  provision-dr-region:
    preconditions: {}
    effects: {dr-region-provisioned: TRUE}
    cost: 5.0

  setup-data-replication:
    preconditions: {primary-region-provisioned: TRUE, dr-region-provisioned: TRUE}
    effects: {data-replicated: TRUE}
    cost: 8.0

  configure-dns-failover:
    preconditions: {data-replicated: TRUE}
    effects: {failover-configured: TRUE}
    cost: 3.0

  migrate-traffic:
    preconditions: {failover-configured: TRUE}
    soft-preconditions: {health-verified: TRUE}
    effects: {traffic-migrated: TRUE}
    cost: 2.0

  decommission-old:
    preconditions: {traffic-migrated: TRUE}
    effects: {single-node-running: FALSE}
    cost: 1.0
```

The GOAP planner finds the optimal action sequence, respects ordering constraints
(can't replicate data before both regions exist), and handles soft preconditions
(prefer to verify health before migrating traffic, but don't block on it).

**Key insight:** The `TransitionPlanner` handles steady-state convergence within a
topology. The `GoapPlanner` handles transitions *between* topology types. They are
complementary, not competing.

### 6.3 Engine Cases — Orchestration with Human Gates

Each deployment or migration is a **case** in the CaseHub engine. The engine provides:

- **Lifecycle management:** A deployment case is created, progresses through stages,
  and completes. If it fails, it can be retried or rolled back.
- **Human approval gates:** High-risk operations (production migration, data
  replication, DNS switchover) require human approval before proceeding. The approval
  workflow already exists in `casehub-ops-api/approval/`.
- **Audit trail:** Every action, approval, and state change is recorded. The ledger
  provides tamper-evident history.
- **Trust-weighted execution:** Agents performing provisioning actions are trust-scored.
  Low-trust agents face additional approval gates.

A topology migration case might look like:

```
Case: migrate-banking-core-to-multi-region
  Stage 1: Provision primary region    [auto-approved — low risk]
  Stage 2: Provision DR region         [auto-approved — low risk]
  Stage 3: Setup data replication      [requires human approval — HIGH risk]
  Stage 4: Configure DNS failover      [requires human approval — CRITICAL risk]
  Stage 5: Verify health               [auto — monitoring check]
  Stage 6: Migrate traffic             [requires human approval — CRITICAL risk]
  Stage 7: Decommission old            [requires human approval — HIGH risk]
```

The engine orchestrates this. GOAP planned the sequence. The approval evaluator
(`ApprovalEvaluator`) classifies risk. Humans approve or reject at each gate.

### 6.4 Service Lifecycle (Chapter 5) — Ongoing Operations

Once a topology is deployed, the service becomes a **long-lived case** with nine
operational dimensions:

| Dimension | What It Monitors |
|---|---|
| Health | Service health, uptime, response times |
| Configuration | Config drift from desired state |
| Compliance | Regulatory compliance posture |
| Scaling | Auto-scaling rules, capacity thresholds |
| Change Management | Deployment history, rollback readiness |
| Security | CVE exposure, certificate expiry, access policies |
| Maintenance | Scheduled maintenance windows, patching |
| Problem Management | Incident tracking, root cause analysis |
| Decommission | End-of-life planning, data migration |

The `ServiceDetectionBridge` routes ganglion detections to the appropriate dimension.
The `DimensionStatusService` computes composite status. Child cases handle incidents
within each dimension.

**This closes the loop:** The topology system declares and deploys services. The
service lifecycle system monitors and manages them. When drift is detected, the
reconciliation loop (TransitionPlanner) converges back to desired state. When a
topology change is needed (scaling up, migrating regions), GOAP plans the migration,
and the engine orchestrates it with human gates.

### 6.5 The Full Architecture

```
                    DECLARE                    PLAN                     EXECUTE
                ┌──────────────┐         ┌──────────────┐         ┌──────────────┐
                │  Topology    │         │  Transition   │         │    Node      │
  YAML ────────►│    Goal      │────────►│   Planner     │────────►│ Provisioner  │
                │  Compiler    │         │  (day-to-day) │         │  (per-type)  │
                └──────────────┘         └──────────────┘         └──────────────┘
                       │                        │                        │
                       │                 ┌──────────────┐                │
                       │                 │    GOAP       │                │
                       │                 │   Planner     │                │
                       │                 │ (migrations)  │                │
                       │                 └──────┬───────┘                │
                       │                        │                        │
                       │                 ┌──────────────┐                │
                       │                 │   Engine      │                │
                       │                 │    Case       │────────────────┘
                       │                 │ (orchestrate) │
                       │                 └──────┬───────┘
                       │                        │
                       │                 ┌──────────────┐
                       │                 │   Service     │
                       └────────────────►│  Lifecycle    │
                                         │  (monitor)   │
                                         └──────────────┘

                    OPERATE
```

**Layer composition:**

| Layer | Component | Responsibility | Existing? |
|---|---|---|---|
| Declaration | TopologyGoalCompiler | YAML → DesiredStateGraph with topology-aware validation | New |
| Steady-state | TransitionPlanner | Desired vs actual → provision/deprovision | Exists |
| Migration | GoapPlanner | Multi-step topology change planning | Exists |
| Orchestration | Engine Case + ApprovalEvaluator | Human gates, audit trail, trust scoring | Exists |
| Operations | Service Lifecycle (L6) | Nine-dimension monitoring of running services | Exists (Chapter 5) |

**What's new:** Only the `TopologyGoalCompiler` and the topology-aware YAML format.
Everything else — the planner, the GOAP engine, the case orchestration, the service
lifecycle — already exists. The topology work plugs into the existing stack rather
than building parallel machinery.

---

## 7. Module Structure

A new `topology-tests/` module (test-scope only, like `testing/`) contains:

| Directory | Contents |
|---|---|
| `src/test/resources/topologies/` | All YAML exemplars, organised by `<arch>/<infra>/` |
| `src/test/java/.../compilation/` | Compilation tests: YAML → DesiredStateGraph assertions |
| `src/test/java/.../reconciliation/` | Integration tests: full reconciliation loop with stubbed backends |
| `src/test/java/.../live/` | Live deployment tests: real K8s with real Docker images |

Maven profiles control which layers run:

```xml
<profiles>
  <!-- Default: compilation tests only -->
  <profile>
    <id>reconciliation</id>
    <!-- Activates reconciliation integration tests -->
  </profile>
  <profile>
    <id>infra-live</id>
    <!-- Activates live K8s deployment tests -->
  </profile>
</profiles>
```

---

## 8. Implementation Sequencing

### Phase 1: InfraNodeSpec Extensions (Java Canonical → YAML/TS Generated)

Extend the `InfraNodeSpec` sealed hierarchy with new Java records for the topology
infrastructure types. The shared platform generator (from eidos) produces YAML schema
and TypeScript types. Jandex indexing auto-populates the `NodeSpecRegistry`.

New sealed variants (Java records with builder DSLs and annotations):
- `LoadBalancerSpec` — type (application/network), health check, target services
- `ServiceMeshControlPlaneSpec` — image, replicas, mesh config
- `SidecarProxySpec` — image, target service, resources
- `DnsFailoverSpec` — primary/secondary endpoints, TTL, failover policy
- `DataReplicationSpec` — source cluster, target cluster, mode (async/sync)

New `InfraNodeProvisioner` handlers for each type.

This phase exercises the full generation pipeline: Java record → YAML schema →
NodeSpecRegistry → YAML `type:` resolution.

### Phase 2: Topology Modules (Pure YAML)

Write reusable YAML modules for each infrastructure pattern. No Java code — these
compose existing primitives:

- `modules/load-balancer.yaml` — adds LB + ingress, wires to target service
- `modules/ha-multi-az.yaml` — HA control plane, anti-affinity, AZ-aware storage
- `modules/multi-region.yaml` — primary + DR clusters, data replication, DNS failover
- `modules/service-mesh.yaml` — control plane nodes, sidecar injection rule

Each module includes its own invariants (promoted to the importing graph) to validate
correct usage.

### Phase 3: Topology Exemplars + Compilation Tests

Write YAML exemplars for each of the ~14 matrix intersections. These are complete
desiredState YAML files using the modules from Phase 2. Compilation tests assert
correct nodes, dependencies, and invariant enforcement using the existing
`YamlGraphRecorder`.

### Phase 4: Reconciliation Integration Tests

Wire compiled DesiredStateGraphs through the full reconciliation loop with stubbed
backends. Verify TransitionPlanner produces correct plans, drift detection works,
and fault policies fire correctly. Gated by `-Preconciliation` Maven profile.

### Phase 5: Live Deployment (Real K8s)

Deploy real Docker images to K8s (minikube/kind for CI, real cluster for manual
verification). Verify health checks, service communication, load balancer routing,
and reconciliation loop convergence. Gated by `-Pinfra-live` Maven profile.

### Phase 6: GOAP Migration Planning

Define GOAP actions for topology transitions (single-node → HA, LB cluster →
multi-region). Verify GoapPlanner produces correct migration plans. Wire into
engine cases with approval gates.

### Phase 7: Service Lifecycle Integration

Connect deployed topologies to Chapter 5 service lifecycle. Deployed services
automatically become long-lived cases with nine-dimension monitoring.

---

## 9. Key Design Decisions (Captured)

| # | Decision | Choice | Rationale |
|---|---|---|---|
| D1 | Scope | Matrix of app architectures × infra topologies | Real value is proving intersections, not isolated dimensions |
| D2 | App architectures | All 5: single, multi-tier, microservices, event-driven, sidecar | Covers the major patterns people actually deploy |
| D3 | Infra topologies | All 4: single-node, LB cluster, HA multi-AZ, multi-region A/P | Covers dev through enterprise DR. Active-active deferred. |
| D4 | Verification | 3-layer pyramid with Maven profiles | Compilation (always), reconciliation (profile), live (profile) |
| D5 | Toy services | Real Docker images (nginx, postgres, redis, RabbitMQ, envoy) | Maximum confidence; health checks, communication, replication all testable |
| D6 | Module location | New `topology-tests/` module | Clean separation, doesn't bloat existing modules |
| D7 | YAML format | Topology-aware as first-class construct | Topology informs validation and node generation, not just metadata |
| D8 | Implementation | ~~TopologyGoalCompiler~~ → YAML-native composition | Existing YAML frontend (modules, invariants, rules, forEach, lifecycle phases) provides every primitive needed. No new compiler. |
| D9 | Gap analysis | Extend, don't reinvent | Only InfraNodeSpec extensions (Java) + topology modules (YAML) + GOAP actions (Java) are genuinely new. Everything else exists. |
| D10 | CaseHub integration | Full stack: TransitionPlanner + GOAP + Engine + Service Lifecycle | Each layer handles a different timescale/problem. No parallel machinery. |

---

## 10. Sources

### Deployment Architecture Patterns
- [Modern Software Architecture Patterns That Scale In 2026](https://upcloud.com/blog/modern-software-architecture-patterns-2026-scales-production/)
- [10 Software Architecture Patterns Engineers Need in 2026](https://blog.patoliyainfotech.com/software-architecture-patterns-guide/)
- [7 Software Architecture Patterns: Examples and Diagrams](https://architecturediagram.ai/blog/software-architecture-patterns)
- [Understanding Software Architecture Patterns](https://suvra1.medium.com/understanding-software-architecture-patterns-a-summary-of-monolithic-layered-microservices-and-1e050d29cbf4)
- [Types of Application Development Architectures — IBM](https://www.ibm.com/think/topics/application-architecture-types)

### Deployment Topologies and Strategies
- [Overview: Deployment Environment Topologies and Patterns — IBM](https://www.ibm.com/docs/en/baw/24.0.x?topic=environment-overview-deployment-topologies-topology-patterns)
- [Deployment Strategies: Rolling, Blue-Green, Canary](https://dev.to/godofgeeks/deployment-strategies-rolling-blue-green-canary-4ob0)
- [Zero-Downtime Deployments — HashiCorp Well-Architected Framework](https://developer.hashicorp.com/well-architected-framework/define-and-automate-processes/deploy/zero-downtime-deployments)
- [Canonical OpenStack Architecture](https://canonical-openstack.readthedocs-hosted.com/en/latest/explanation/architecture/)

### Multi-Region and HA
- [Multi-Region Deployment Models for AKS — Microsoft](https://learn.microsoft.com/en-us/azure/aks/reliability-multi-region-deployment-models)
- [Google Cloud Multi-Regional Deployment Archetype](https://docs.google.com/architecture/deployment-archetypes/multiregional)
- [High Availability Kubernetes Clusters — Tigera](https://www.tigera.io/learn/guides/kubernetes-security/high-availability-kubernetes/)
- [Options for Highly Available Topology — Kubernetes](https://kubernetes.io/docs/setup/production-environment/tools/kubeadm/ha-topology/)
- [AKS Baseline for Multi-Region Clusters — Microsoft](https://learn.microsoft.com/en-us/azure/architecture/reference-architectures/containers/aks-multi-region/aks-multi-cluster)

### Edge and Hybrid
- [Kubernetes Deployment Models for Edge Applications — Red Hat](https://www.redhat.com/en/blog/kubernetes-edge-applications)
- [KubeEdge — CNCF](https://www.cncf.io/blog/2022/08/18/kubernetes-on-the-edge-getting-started-with-kubeedge-and-kubernetes-for-edge-computing/)
- [Deploy Single-Node Kubernetes at the Edge with OpenShift — Red Hat](https://www.redhat.com/en/blog/deploy-openshift-at-the-edge-with-single-node-openshift)

### Terraform, Helm, Kubernetes
- [Using Terraform, Kubernetes, and Helm — env zero](https://www.envzero.com/blog/terraform-kubernetes-and-helm-the-power-trio)
- [Deploy Applications with the Helm Provider — HashiCorp](https://developer.hashicorp.com/terraform/tutorials/kubernetes/helm-provider)
- [Cloud Posse Reference Architecture — Helm](https://docs.cloudposse.com/resources/legacy/helm/)

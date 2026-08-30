# Infrastructure Operations — Problem Taxonomy and Desired-State Coverage

**Date:** 2026-08-29
**Author:** Mark Proctor + Claude
**Status:** Research — informing design direction
**Related:** `2026-08-29-canonical-deployment-topologies.md`

---

## 1. The Industry Problem

Infrastructure management is fragmented across tools that each handle a slice of the
lifecycle. Provisioning tools create cloud resources but can't configure them.
Configuration tools manage host-level state but can't provision infrastructure.
Orchestration platforms coordinate both but don't own the state model for either.

The result: every production system runs 2-4 tools in a handoff chain, with state
management gaps between each handoff. Drift accumulates in the gaps. Audit trails
break at tool boundaries. Governance policies apply to one phase but not the next.

The question is not "which tool is best" but **"can a single desired-state model
express the full range of infrastructure operations problems?"** If it can, the
handoff gaps disappear.

---

## 2. The Lifecycle Model

Infrastructure operations decompose into three phases based on when they occur and
how long they run:

| Phase | Duration | What happens | Problem character |
|-------|----------|-------------|-------------------|
| **Day 0 — Design** | Weeks | Architecture decisions, policy definition, template authoring | Human, creative |
| **Day 1 — Deployment** | Hours | Provision infrastructure, configure hosts, deploy applications | Automated, convergent |
| **Day 2 — Operations** | Years | Monitor, patch, rotate, scale, respond, decommission | Continuous, reactive |

**The critical insight:** Day 0 and Day 1 are time-bounded — they happen once per
release cycle. Day 2 never ends. It runs for the entire lifetime of the system, which
for most production systems is measured in years. Yet most engineering investment goes
into Day 0 and Day 1. Day 2 is where incidents, drift, cost overruns, and compliance
failures accumulate.

### The Declarative Boundary

The industry has drawn an invisible line — the **Declarative Boundary** — between what
can be managed declaratively (desired-state reconciliation) and what requires
imperative execution (run this script, perform this operation).

Current state of the art:

```
   Declarative                    │               Imperative
   (desired-state reconciliation) │    (task execution, scripting)
                                  │
   Cloud resource provisioning    │    Host-level configuration
   Container orchestration        │    Application deployment
   Network policy                 │    Database migrations
   DNS records                    │    Credential rotation
                                  │    Certificate renewal
                                  │    Rolling operations
                                  │    Incident response
                                  │    Backup/restore
```

**Everything to the right of the boundary is a gap.** Teams fill it with imperative
automation scripts, configuration management tools, and manual runbooks. Each gap
accumulates its own state management debt: no drift detection, no audit trail, no
approval gates, no continuous reconciliation.

**CaseHub's thesis: push the declarative boundary right.** Model host configuration,
periodic operations, and orchestrated sequences as desired-state problems — not by
making everything declarative, but by using three complementary techniques matched to
the problem character.

---

## 3. Problem Domain Taxonomy

Six problem domains cover the full range of infrastructure operations. Each has a
distinct character that determines which technique solves it best.

### Domain 1: Resource Provisioning

**What:** Creating, updating, and removing infrastructure resources — compute
instances, networks, storage volumes, databases, container clusters, message brokers,
DNS records, certificates.

**Character:** Declarative. The desired state is a set of resources with specific
configurations. Drift is detectable via API queries. Convergence is achieved by
creating, updating, or removing resources.

**Examples:**
- "The production cluster should have 3 worker nodes in eu-west-1"
- "A PostgreSQL 16 database cluster with 100GB storage should exist"
- "DNS record api.example.com should point to 203.0.113.42"
- "TLS certificate for *.example.com should exist and be valid for >30 days"

**Lifecycle phase:** Day 1 (initial creation) + Day 2 (drift detection, scaling)

### Domain 2: Host Configuration

**What:** Managing the state of software and configuration *inside* provisioned
machines — packages, services, files, users, firewall rules, kernel parameters,
security baselines.

**Character:** Declarative. Each configuration item has a desired state that can be
checked and converged to. A package is either installed or not. A service is either
running or not. A file either has the right content or doesn't.

This domain is currently treated as imperative by the industry (run tasks on hosts),
but the underlying problems are inherently declarative. The question "is nginx
installed?" has a boolean answer, checkable at any time.

**Examples:**
- "nginx 1.25 should be installed on all web servers"
- "The nginx service should be enabled and running"
- "/etc/nginx/nginx.conf should contain this template-rendered content"
- "User 'deploy' should exist with these SSH keys and sudo access"
- "UFW should allow ports 80, 443, and 9100 (from 10.0.0.0/8 only)"
- "kernel.vm.swappiness should be 10"

**Lifecycle phase:** Day 1 (initial configuration) + Day 2 (drift remediation,
patching, hardening updates)

### Domain 3: Application Lifecycle

**What:** Deploying, updating, and managing applications on provisioned and
configured infrastructure — artifact delivery, configuration injection, schema
migrations, health verification, feature toggling.

**Character:** Mixed. Artifact delivery and configuration injection are declarative
("this version should be running with these settings"). Schema migrations and data
operations are imperative one-shot operations ("apply migration V42 exactly once").

**Examples:**
- "catalog-api v2.8 should be running with 3 replicas"
- "Environment variable DB_HOST should be set to product-db.internal"
- "Database schema should be at migration V42"
- "Seed data for regulatory lookup tables should be loaded"
- "Health endpoint /health should return 200 within 5 seconds"

**Lifecycle phase:** Day 1 (initial deployment) + Day 2 (updates, rollbacks)

### Domain 4: Periodic Operations

**What:** Operations that must occur on a schedule or in response to staleness —
credential rotation, certificate renewal, backup execution, security scanning,
compliance evidence collection, log rotation.

**Character:** Declarative with time-based drift. The desired state is "this
operation has been performed recently enough." Staleness triggers drift, which
triggers re-execution. The operation itself may be imperative, but the *need*
for it is a desired-state question.

**Examples:**
- "Database credentials should have been rotated within the last 90 days"
- "TLS certificates should have >30 days until expiry"
- "Full database backup should have been taken within the last 24 hours"
- "Vulnerability scan should have been run within the last 7 days"
- "SOC2 encryption evidence should be <30 days old"

**Lifecycle phase:** Day 2 (continuous — the defining characteristic of Day 2)

### Domain 5: Orchestrated Sequences

**What:** Multi-step operations where ordering, coordination, and intermediate
verification matter — rolling updates, blue-green deployments, failover procedures,
scaling operations, maintenance windows, incident response.

**Character:** Imperative planning with declarative goals. The *goal* is declarative
("all hosts should be running v2.8, with zero downtime"). The *plan* to achieve it
is a sequence of ordered steps with preconditions, effects, and verification gates.
This is a planning problem, not a convergence problem.

**Examples:**
- "Upgrade all hosts from v2.7 to v2.8, one at a time, with health verification
  between each" (rolling update)
- "Deploy v2.8 to a parallel environment, verify it works, switch traffic, keep
  the old environment for rollback" (blue-green)
- "Rotate the database master password: generate new credential, update all
  consumers with dual-credential overlap, revoke old credential" (zero-downtime
  credential rotation)
- "Migrate from single-region to multi-region: provision DR, replicate data,
  configure failover, verify, switch DNS" (topology migration)

**Lifecycle phase:** Day 1 (deployment strategies) + Day 2 (maintenance, incident
response, migrations)

### Domain 6: Continuous Governance

**What:** Ongoing verification that infrastructure, configuration, and operations
comply with policies — compliance posture, security baselines, cost governance,
audit trails, approval workflows, drift detection across all domains.

**Character:** Declarative + evidence-based. The desired state is a set of policies
("all data at rest must be encrypted with AES-256"). Compliance is verified by
collecting evidence. Staleness of evidence is drift. Tamper-evident audit trails
prove the chain of custody.

**Examples:**
- "SOC2 AC-1: All production databases must have encryption at rest"
- "Every provisioning operation on a production namespace requires human approval"
- "Configuration drift should be detected and remediated within 1 hour"
- "All infrastructure changes should be recorded in a tamper-evident audit trail"
- "Cloud spend should not exceed the budget threshold without approval"

**Lifecycle phase:** Day 0 (policy definition) through Day 2 (continuous enforcement)

---

## 4. Three Techniques for Six Domains

The six problem domains decompose into three distinct problem characters. Each
requires a different technique — but all three share a common state model.

### Technique 1: Declarative Convergence

**Problem character:** "This should exist in this state."

**Mechanism:** Declare desired state → query actual state → compute diff →
converge. Repeat continuously. Drift is detected by comparing desired against
actual. Convergence is achieved by creating, updating, or removing resources.

**Applies to:**
- Domain 1 (Resource Provisioning) — all of it
- Domain 2 (Host Configuration) — all of it
- Domain 3 (Application Lifecycle) — artifact delivery, configuration injection
- Domain 6 (Continuous Governance) — drift detection, policy enforcement

**Properties:**
- Idempotent — running the reconciliation loop twice produces the same state
- Continuous — not a one-shot operation; runs on a schedule or in response to events
- Auditable — every convergence action is recorded
- Self-healing — drift triggers automatic remediation

### Technique 2: Staleness-Based Re-execution

**Problem character:** "This should have been done recently enough."

**Mechanism:** Record when an operation was last performed. Define a staleness
threshold. When the time since last execution exceeds the threshold, the operation
is "drifted" and re-executes. The operation itself may be imperative, but the
*trigger* is a desired-state staleness check.

**Applies to:**
- Domain 4 (Periodic Operations) — all of it
- Domain 3 (Application Lifecycle) — schema migrations (once per version)
- Domain 6 (Continuous Governance) — evidence freshness

**Properties:**
- Time-aware — staleness is measured against configurable thresholds
- Event-driven — can be triggered by staleness *or* by external events
  (e.g., certificate renewal triggered by approaching expiry, not just schedule)
- Evidence-based — the result of each execution is recorded as evidence
- Tamper-evident — when combined with a ledger, the evidence trail is
  cryptographically verifiable

**Relationship to Technique 1:** Staleness-based re-execution IS declarative
convergence with a time dimension. "This certificate should be valid" is the
desired state. "Certificate expires in 5 days" is drift. Renewal is convergence.
The technique is identical — the only difference is that drift detection includes
a temporal component.

### Technique 3: Goal-Oriented Action Planning

**Problem character:** "Get from this state to that state, optimally."

**Mechanism:** Define a world state (current conditions). Define goal conditions
(what should be true). Define available actions with preconditions, effects, and
costs. A planner (A* search) finds the optimal action sequence that transitions
from the current state to the goal state. A case orchestrator executes the plan
with human approval gates at high-risk steps.

**Applies to:**
- Domain 5 (Orchestrated Sequences) — all of it
- Domain 3 (Application Lifecycle) — complex deployment strategies
- Domain 1 (Resource Provisioning) — topology migrations

**Properties:**
- Optimal — the planner finds the lowest-cost action sequence
- Safe — preconditions prevent invalid actions; human gates prevent high-risk ones
- Recoverable — if a step fails, the planner can replan from the current state
- Auditable — every action, approval, and state transition is recorded

### The Blended Model

No single technique handles all six domains. But all three techniques share a
common foundation: **a state model that distinguishes desired from actual.**

```
                          ┌─────────────────────┐
                          │   Desired State      │
                          │   (declared in YAML)  │
                          └─────────┬───────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    │               │               │
           ┌────────▼──────┐ ┌─────▼─────┐ ┌───────▼───────┐
           │  Technique 1   │ │ Technique 2│ │  Technique 3   │
           │  Declarative   │ │ Staleness  │ │  Goal-Oriented │
           │  Convergence   │ │ Re-execute │ │  Planning      │
           └────────┬──────┘ └─────┬─────┘ └───────┬───────┘
                    │               │               │
           ┌────────▼──────┐ ┌─────▼─────┐ ┌───────▼───────┐
           │ Transition     │ │ Evidence   │ │    GOAP        │
           │ Planner        │ │ Staleness  │ │   Planner      │
           │ (diff → steps) │ │ (time →    │ │ (A* → optimal  │
           │                │ │  re-exec)  │ │   sequence)    │
           └────────┬──────┘ └─────┬─────┘ └───────┬───────┘
                    │               │               │
                    └───────────────┼───────────────┘
                                    │
                          ┌─────────▼───────────┐
                          │   Actual State       │
                          │   (queried/observed)  │
                          └─────────────────────┘
```

**All three techniques:**
- Start from a declared desired state
- Query or observe actual state
- Determine what action to take
- Execute with audit trail and optional human gates
- Record the result as evidence

The difference is HOW they determine the action:
- Technique 1 diffs desired vs actual and converges
- Technique 2 checks time since last execution and re-executes if stale
- Technique 3 plans an optimal multi-step sequence

---

## 5. Domain × Technique Coverage Matrix

This matrix maps each problem domain to the technique(s) that handle it, and
identifies the node types and provisioner behaviours needed.

### Domain 1: Resource Provisioning — Technique 1

Already implemented in CaseHub. The infra module provides sealed NodeSpec variants
for cloud resources (compute, networking, storage, K8s workloads). The
TransitionPlanner diffs desired vs actual. The NodeProvisioner converges via
backend-specific APIs.

**Coverage:** Complete for K8s-native resources. Topology research extends to load
balancers, service mesh, DNS failover, data replication. Cloud provider backends
(AWS, GCP, Azure) are future work.

### Domain 2: Host Configuration — Technique 1

**Not yet implemented.** This is the largest gap.

The industry treats this as imperative (run tasks on hosts), but every host
configuration problem is a desired-state question:

| Config Item | Desired State | Actual State Query | Convergence Action |
|-------------|--------------|-------------------|-------------------|
| Package | installed, version X | `dpkg -l` / `rpm -q` | `apt install` / `yum install` |
| Service | enabled, running | `systemctl is-active` | `systemctl start/enable` |
| File | content matches template | `sha256sum` comparison | write file, set permissions |
| User | exists, correct groups | `id username` | `useradd` / `usermod` |
| Firewall rule | port open from source | `ufw status` / `iptables -L` | `ufw allow` |
| Kernel param | value = X | `sysctl -n param` | `sysctl -w param=X` |
| Cron job | exists, correct schedule | `crontab -l` | `crontab` write |

**New node types needed:**

```yaml
# Packages: "these packages should be installed"
web-packages:
  type: host_packages
  dependsOn: [web-server]
  spec:
    host: web-server
    packages:
      - { name: nginx, version: "1.25.*", state: present }
      - { name: certbot, state: present }
      - { name: fail2ban, state: present }

# Services: "these services should be running"
web-services:
  type: host_services
  dependsOn: [web-packages]
  spec:
    host: web-server
    services:
      - { name: nginx, enabled: true, state: running }
      - { name: fail2ban, enabled: true, state: running }

# Files: "these files should have this content"
nginx-config:
  type: host_file
  dependsOn: [web-packages]
  spec:
    host: web-server
    path: /etc/nginx/nginx.conf
    content: |
      server {
          listen 80;
          server_name api.example.com;
          location / { proxy_pass http://catalog-api:8080; }
      }
    owner: root
    group: root
    mode: "0644"
    notifyService: nginx   # restart nginx when content changes

# Users: "these users should exist"
deploy-user:
  type: host_user
  dependsOn: [web-server]
  spec:
    host: web-server
    username: deploy
    groups: [sudo, docker]
    shell: /bin/bash
    sshKeys:
      - "ssh-ed25519 AAAA... deploy@ci"

# Firewall: "these rules should be active"
web-firewall:
  type: host_firewall
  dependsOn: [web-server]
  spec:
    host: web-server
    defaultPolicy: deny
    rules:
      - { port: 80, proto: tcp, action: allow }
      - { port: 443, proto: tcp, action: allow }
      - { port: 22, proto: tcp, action: allow, source: 10.0.0.0/8 }
      - { port: 9100, proto: tcp, action: allow, source: 10.0.0.0/8 }

# Security baseline: "these kernel parameters should be set"
security-hardening:
  type: host_sysctl
  dependsOn: [web-server]
  spec:
    host: web-server
    parameters:
      net.ipv4.ip_forward: 0
      net.ipv4.conf.all.rp_filter: 1
      kernel.randomize_va_space: 2
      vm.swappiness: 10
```

**Provisioner:** The `HostConfigProvisioner` connects to the target host (SSH, agent,
or cloud-init depending on the backend) and converges the configuration. The
`HostActualStateAdapter` queries the host to determine current state.

**Connection model:** The `host:` field in each spec references a compute instance
node in the same graph. The provisioner resolves the node's actual IP/hostname from
the infra actual-state adapter. Connection credentials come from a secret store
reference (outside the spec — operational concern, not domain concern).

**Drift detection:** The adapter SSHs into the host and checks: is the package
installed at the right version? Is the service running? Does the file hash match?
Is the user's group membership correct? This runs on every reconciliation cycle.

**Dependency ordering:** Package nodes depend on the compute instance. Service nodes
depend on package nodes. File nodes depend on package nodes. This is the natural
ordering: you can't start nginx if it's not installed, and you can't install it if
the server doesn't exist.

**Service notification:** When a file node changes (content drift detected and
reconverged), the `notifyService` field triggers a service restart. This is modeled
as a side-effect of file provisioning, not as a separate node — because the restart
is a consequence of convergence, not a desired state in itself.

### Domain 3: Application Lifecycle — Techniques 1 + 2

**Partially implemented.** The deployment module handles CaseHub-specific application
topology (agents, channels, case types). The topology research extends to general
application deployments (K8s deployments, services, ingress). What's missing:

**Schema migrations (Technique 2):**

```yaml
catalog-db-migration:
  type: schema_migration
  dependsOn: [catalog-db]
  spec:
    database: catalog-db
    migrationType: flyway
    migrationSource: classpath:db/migration
    targetVersion: V42
    maxStalenessHours: 0    # run immediately when version changes
```

The provisioner runs the migration tool (Flyway, Liquibase). The actual state
adapter queries the migration history table for the current version. Drift is
detected when the target version in the spec differs from the applied version.
A spec hash change (new target version) triggers re-provisioning (running the
new migration). Historical migrations are never re-run — only the delta.

**Health verification (Technique 1):**

```yaml
catalog-api-health:
  type: health_check
  dependsOn: [catalog-api]
  spec:
    target: catalog-api
    endpoint: /health
    expectedStatus: 200
    timeoutSeconds: 5
    intervalSeconds: 30
```

Continuous health verification as a desired-state node. PRESENT = healthy.
DRIFTED = unhealthy. The "convergence action" for a failed health check is
escalation (fault policy → review node → human investigation), not
automatic remediation — because the fix depends on what's wrong.

### Domain 4: Periodic Operations — Technique 2

**Partially implemented.** The compliance module already implements this pattern:
evidence collection on a schedule, staleness-based drift, ledger-backed audit trail.

The pattern generalises beyond compliance:

```yaml
# Credential rotation: "credentials should be fresh"
db-credential-rotation:
  type: credential_rotation
  dependsOn: [catalog-db]
  spec:
    target: catalog-db
    credentialType: database_password
    rotationIntervalDays: 90
    dualCredentialOverlap: true    # keep old credential valid during transition
    consumers: [catalog-api, analytics-worker]

# Certificate renewal: "certificates should be valid"
api-certificate:
  type: certificate_renewal
  spec:
    domain: "*.api.example.com"
    issuer: letsencrypt
    minRemainingDays: 30
    renewalAction: acme_challenge
    deployTargets: [web-server]

# Backup verification: "backups should be recent"
catalog-db-backup:
  type: backup_schedule
  dependsOn: [catalog-db]
  spec:
    target: catalog-db
    schedule: daily
    maxStalenessHours: 26    # 24h + 2h grace
    retentionDays: 30
    verifyRestore: weekly    # periodically verify backup restores correctly
```

**Staleness as drift:** The actual-state adapter checks when the operation was last
performed (from ledger entries, metadata, or API queries). If the elapsed time
exceeds `maxStalenessHours` or `rotationIntervalDays`, the node is DRIFTED. The
provisioner re-executes the operation.

**Credential rotation specifics:** The `dualCredentialOverlap` flag means the
provisioner generates a new credential, updates all consumers (listed in the spec),
verifies they work, THEN revokes the old credential. This is a multi-step process
within a single provisioning action — not a GOAP plan, because the steps are always
the same and don't require inter-node coordination.

**Evidence recording:** Every periodic operation records its result as a
tamper-evident ledger entry. This serves dual purposes: the staleness check reads
it to determine freshness, and the compliance domain reads it as evidence for
regulatory frameworks.

### Domain 5: Orchestrated Sequences — Technique 3

**Infrastructure exists but not yet applied to operations.**

The GOAP planner exists in `casehub-engine`. Engine cases provide orchestration with
human approval gates. What's missing: pre-built action libraries for common
operational sequences.

**Rolling update:**

```
World State:
  host-1-version: v2.7
  host-1-in-lb: true
  host-2-version: v2.7
  host-2-in-lb: true
  host-3-version: v2.7
  host-3-in-lb: true

Goal:
  host-1-version: v2.8
  host-2-version: v2.8
  host-3-version: v2.8
  host-1-in-lb: true
  host-2-in-lb: true
  host-3-in-lb: true

Actions:
  remove-from-lb(host):
    preconditions: { host-in-lb: true }
    effects: { host-in-lb: false }
    cost: 1.0

  drain(host):
    preconditions: { host-in-lb: false }
    effects: { host-drained: true }
    cost: 3.0    # takes time

  upgrade(host):
    preconditions: { host-drained: true }
    effects: { host-version: v2.8, host-drained: false }
    cost: 2.0

  health-check(host):
    preconditions: { host-version: v2.8 }
    effects: { host-healthy: true }
    cost: 1.0

  add-to-lb(host):
    preconditions: { host-healthy: true }
    effects: { host-in-lb: true }
    cost: 1.0
```

The GOAP planner finds the optimal sequence: remove host-1 → drain → upgrade →
health check → add to LB → repeat for host-2 → repeat for host-3. The engine
case orchestrates execution with approval gates at high-risk steps (upgrade,
DNS switch).

**Credential rotation (complex variant):**

When `dualCredentialOverlap` can't be handled within a single provisioner call
(e.g., rotating a shared secret across multiple independent services that must
be coordinated), the rotation becomes a GOAP plan:

```
Actions:
  generate-new-credential:
    effects: { new-credential-exists: true }

  update-consumer(service):
    preconditions: { new-credential-exists: true }
    effects: { service-uses-new: true }

  verify-consumer(service):
    preconditions: { service-uses-new: true }
    effects: { service-verified: true }

  revoke-old-credential:
    preconditions: { all-services-verified: true }
    effects: { old-credential-revoked: true }
```

### Domain 6: Continuous Governance — Techniques 1 + 2

**Implemented.** The compliance module covers this domain:
- Evidence-based compliance posture (Technique 2 — staleness-based)
- Tamper-evident ledger entries (audit trail)
- Framework scoring (SOC2, GDPR, EU-AI-Act, DORA, NIS2, ISO27001)
- Approval workflow for high-risk operations (Technique 3 integration)

The topology research adds governance for infrastructure changes:
- Every provisioning operation through the reconciliation loop is auditable
- Approval gates classify risk and require human sign-off for critical changes
- Service lifecycle monitoring (Chapter 5) provides continuous operational governance

---

## 6. Coverage Summary

| Domain | Technique | CaseHub Status | Gap |
|--------|-----------|---------------|-----|
| 1. Resource Provisioning | Declarative convergence | **Implemented** (infra module) | Cloud provider backends |
| 2. Host Configuration | Declarative convergence | **Not implemented** | New domain module + host-level provisioner |
| 3a. App Deployment | Declarative convergence | **Partially** (K8s via topology spec) | General application types |
| 3b. Schema Migrations | Staleness-based | **Not implemented** | Migration node type + Flyway/Liquibase provisioner |
| 4. Periodic Operations | Staleness-based | **Partially** (compliance evidence) | Credential rotation, certificate renewal, backup |
| 5. Orchestrated Sequences | GOAP planning | **Infrastructure exists** | Pre-built action libraries |
| 6. Continuous Governance | Declarative + evidence | **Implemented** (compliance module) | Cross-domain governance integration |

### What's Genuinely New

| New Work | Type | Complexity |
|----------|------|-----------|
| Host configuration node types (packages, services, files, users, firewall, sysctl) | New domain module | High — host-level provisioner with SSH/agent connection |
| Schema migration node type | New node type in app/infra | Medium — integrate with Flyway/Liquibase APIs |
| Credential rotation node type | New node type | Medium — dual-credential pattern, consumer coordination |
| Certificate renewal node type | New node type | Medium — ACME challenge, deploy to targets |
| Backup schedule node type | New node type | Low — record-keeping, staleness check |
| Health check node type | New node type | Low — HTTP/TCP probe |
| GOAP action libraries (rolling update, migration, rotation) | Action definitions | Medium — domain-specific preconditions/effects |

### What Already Exists and Extends Naturally

| Existing Capability | Extends To |
|---------------------|-----------|
| DesiredStateGraph + TransitionPlanner | All declarative convergence (Domains 1, 2, 3a) |
| ComplianceEvidenceService + staleness checks | All periodic operations (Domain 4) |
| GOAP Planner + Engine Cases | All orchestrated sequences (Domain 5) |
| Approval Workflow + Ledger | Cross-domain governance (Domain 6) |
| Service Lifecycle (9 dimensions) | Continuous monitoring of all deployed services |
| YAML frontend (modules, invariants, rules, forEach) | All node types — YAML as primary interface |

---

## 7. The Blended Architecture

The three techniques are not alternatives — they compose. A single deployment
topology can use all three simultaneously:

```yaml
# TECHNIQUE 1: Declarative convergence — provision and configure
nodes:
  web-server:
    type: compute_instance
    spec: { image: ubuntu-22.04, size: m5.large }

  web-packages:
    type: host_packages
    dependsOn: [web-server]
    spec:
      host: web-server
      packages: [{ name: nginx, state: present }]

  catalog-api:
    type: k8s_deployment
    dependsOn: [web-server]
    spec:
      name: catalog-api
      image: catalog-api:2.8
      replicas: 3

# TECHNIQUE 2: Staleness-based re-execution — periodic operations
  api-cert:
    type: certificate_renewal
    spec:
      domain: api.example.com
      minRemainingDays: 30

  db-backup:
    type: backup_schedule
    dependsOn: [catalog-db]
    spec:
      target: catalog-db
      maxStalenessHours: 26

  db-credentials:
    type: credential_rotation
    dependsOn: [catalog-db]
    spec:
      target: catalog-db
      rotationIntervalDays: 90

# TECHNIQUE 3 happens automatically via GOAP when:
# - The topology TYPE changes (e.g., single-node → HA)
# - A rolling update is triggered (new image version)
# - A credential rotation spans multiple consumers
```

**All three techniques share:**
- The same YAML declaration format
- The same DesiredStateGraph as the compilation target
- The same reconciliation loop for drift detection
- The same ledger for audit trail
- The same approval workflow for human gates
- The same service lifecycle for ongoing monitoring

**The user sees one system.** They declare what they want. The system figures
out which technique applies, converges to the desired state, detects drift,
and maintains governance. No handoff between tools. No gaps in the audit trail.
No declarative boundary.

---

## 8. Implementation Roadmap

| Priority | Domain | Why |
|----------|--------|-----|
| 1 | Resource Provisioning (topology) | Already in spec — proves the YAML frontend |
| 2 | Host Configuration | Largest gap — 70-80% of Day 1+ operations |
| 3 | Periodic Operations (credential, cert, backup) | Extends existing compliance pattern |
| 4 | Orchestrated Sequences (GOAP actions) | Infrastructure exists — needs action libraries |
| 5 | Application Lifecycle (migrations, health) | Fills remaining gaps |

### Separate Design Specs Needed

1. **Host Configuration Domain** — new `casehub-ops-hostconfig` module with sealed
   NodeSpec variants for packages, services, files, users, firewall, sysctl. SSH/agent
   provisioner. Host actual-state adapter. Topology module integration.

2. **Periodic Operations Extensions** — credential rotation, certificate renewal,
   backup schedule node types. Extends the staleness-based pattern from compliance.

3. **GOAP Operational Actions** — pre-built action libraries for rolling updates,
   topology migrations, complex credential rotations. Engine case definitions.

---

## 9. Sources

- [Day 2 Operations: A Practical Guide (2026)](https://www.cycloid.io/blog/day-2-operations-a-practical-guide-for-managing-post-deployment-complexity/)
- [How Platform Teams Govern the Full Infrastructure Lifecycle](https://www.cycloid.io/blog/how-platform-teams-govern-the-full-infrastructure-lifecycle/)
- [Day 2 Operations Architecture and the Declarative Boundary](https://www.rack2cloud.com/ansible-day-2-operations-strategy-guide/)
- [Lifecycle Management: Day 0, Day 1, Day 2, and Day N](https://sergiopichardo.com/posts/infra-lifecycle-management)
- [The Software Lifecycle in the Cloud Age](https://codilime.com/blog/day-0-day-1-day-2-the-software-lifecycle-in-the-cloud-age/)
- [Certificate Rotation Strategies: Zero-Downtime Renewal](https://axelspire.com/vault/operations/certificate-rotation-strategies/)
- [Declarative vs Imperative Automation](https://opsmill.com/blog/declarative-vs-imperative-automation/)
- [Certificate Renewal Is a Deployment Workflow, Not a Cron Job](https://devops.com/certificate-renewal-is-a-deployment-workflow-not-a-cron-job/)
- [Release Orchestration in 2026](https://octopus.com/devops/software-deployments/release-orchestration/)
- [Secret Rotation Strategies](https://oneuptime.com/blog/post/2026-01-30-security-secret-rotation-strategies/view)

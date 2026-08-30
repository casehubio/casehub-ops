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

### Domain 7: Secret & Identity Management

**What:** Managing the infrastructure of trust — secret stores, IAM roles and
policies, service accounts, OAuth/OIDC client registrations, API key lifecycle,
mTLS certificate chains, zero-trust network policies.

**Character:** Declarative (the identity infrastructure) + staleness-based (credential
lifecycle) + orchestrated (rotation across consumers). This domain cross-cuts all
others — every resource, host, and application depends on identity and secrets.

**Examples:**
- "A Vault secret engine for database credentials should exist at path db/catalog"
- "IAM role 'catalog-api-role' should have read access to S3 bucket 'assets'"
- "Service account 'deploy-agent' should exist with these scopes"
- "OAuth client 'dashboard-app' should be registered with redirect URI https://..."
- "mTLS certificate chain: root CA → intermediate → service certificates for all meshed services"
- "API key for payment gateway should exist, rotated every 30 days"

**Why it's a separate domain:** Identity is not a resource you provision once (Domain 1)
or a host you configure (Domain 2). It's a living graph of trust relationships that
evolves with the system. Adding a new service requires a new service account, new IAM
bindings, new secret paths, and new certificate entries. Removing a service requires
revoking all of these. The identity graph must be reconciled independently.

**Lifecycle phase:** Day 0 (policy design) through Day 2 (rotation, revocation, audit)

### Domain 8: Observability Infrastructure

**What:** Managing the monitoring, alerting, logging, and tracing infrastructure as
desired state — not just deploying agents (Domain 2), but managing the rules,
dashboards, pipelines, and SLO definitions that make observability useful.

**Character:** Declarative. Alert rules, dashboard definitions, log routing rules, and
SLO targets are all desired-state objects. "This alert should exist and fire when
error rate exceeds 5%" is a declarative statement with drift detection (someone
deleted the alert rule → DRIFTED → re-create).

"Observability as code" is an emerging industry practice in 2026 — managing
observability systems through version-controlled configuration files.

**Examples:**
- "Prometheus alert 'HighErrorRate' should fire when error_rate > 0.05 for 5 minutes"
- "Grafana dashboard 'Service Health' should exist with panels for latency, errors, throughput"
- "Log pipeline should route 'audit.*' to long-term storage and 'debug.*' to 7-day retention"
- "SLO: catalog-api availability should be ≥99.9% over 30-day window"
- "Trace sampling: 100% for errors, 10% for successful requests, 1% for health checks"
- "PagerDuty escalation policy: page on-call after 5 min, escalate to lead after 15 min"

**Why it's a separate domain:** Observability configuration is not infrastructure
(Domain 1) or host configuration (Domain 2). It's a meta-layer — configuration about
how you observe and respond to the other domains. Alert rules reference services from
Domain 3. SLOs span resources from Domain 1. Log pipelines touch hosts from Domain 2.
Observability is the nervous system that connects all other domains.

**Lifecycle phase:** Day 1 (initial setup) + Day 2 (rule evolution, threshold tuning,
dashboard updates)

### Domain 9: Environment Lifecycle

**What:** Managing complete environments — dev, staging, production, ephemeral PR
environments — as composed units that span resources (Domain 1), host configuration
(Domain 2), and application deployment (Domain 3). Environment provisioning,
promotion, parity enforcement, and orderly teardown.

**Character:** Declarative at the environment level — "this environment should exist
with these services at these versions" — with orchestrated promotion between
environments (staging → production is a Technique 3 problem).

**Examples:**
- "Dev environment should mirror production topology with replicas=1 and smaller instance sizes"
- "PR environment pr-423 should exist with the branch's container images, auto-destroyed after merge"
- "Staging should be promoted to production after smoke tests pass and human approves"
- "All environments should use the same YAML topology declaration with environment-specific variables"
- "Decommission: drain traffic → archive data → revoke secrets → remove DNS → destroy resources"

**Why it's a separate domain:** An environment is not a single resource or a single host
— it's a composed graph of everything from Domains 1-8. The YAML frontend's variable
system (`${var.replicas}`, `${var.instance_size}`) already supports environment
parameterisation. Lifecycle phases support ordered rollout. But environment
promotion, parity enforcement, and orderly teardown are higher-level orchestration
problems.

**Lifecycle phase:** Day 1 (provisioning) + Day 2 (promotion, parity, teardown)

### Domain 10: Data Management

**What:** Managing data lifecycle beyond the database resource itself (Domain 1) and
schema migrations (Domain 3) — replication policies, backup retention, data masking
for non-production environments, cache configuration, message queue/topic
provisioning, data sovereignty enforcement.

**Character:** Mixed. Replication policies and queue topics are declarative (Technique
1). Backup execution is staleness-based (Technique 2). Data migration across regions
is orchestrated (Technique 3). Data masking is a periodic operation applied when
environments are refreshed.

**Examples:**
- "PostgreSQL replication should be configured: primary → 2 read replicas, async, lag <30s"
- "Redis cache should be configured: 6GB, eviction policy allkeys-lru, persistence RDB every 15min"
- "Kafka topic 'orders.placed' should exist with 12 partitions, retention 7 days, replication factor 3"
- "Non-production databases should have PII columns masked (email → hash, phone → redacted)"
- "Data in eu-west-1 must not be replicated to non-EU regions (GDPR data sovereignty)"
- "Backup retention: daily for 30 days, weekly for 90 days, monthly for 1 year"

**Why it's a separate domain:** Data has unique constraints that don't fit neatly into
resource provisioning or application lifecycle. Data sovereignty is a legal
constraint. Masking is a privacy requirement. Replication topology is an availability
decision. These cross-cut multiple domains and have their own lifecycle (data outlives
the applications that create it).

**Lifecycle phase:** Day 1 (initial setup) + Day 2 (replication monitoring, retention
enforcement, masking updates)

### Cross-Cutting Concerns

Three concerns span all 10 domains:

**Multi-tenancy:** Every domain must support per-tenant isolation. Resources, hosts,
applications, secrets, observability, and governance are all tenant-scoped. CaseHub's
`tenancyId` propagation (already implemented across all domain modules) provides the
foundation. Topology declarations can use variables (`${var.tenant_id}`) and forEach
(stamp per tenant) to express multi-tenant deployments.

**Edge constraints:** Edge deployments impose constraints on all domains — limited
compute (smaller instances, fewer replicas), intermittent connectivity (must operate
autonomously), heterogeneous hardware (ARM vs x86, varying memory). These are
deployment-context constraints expressed as variables and conditional nodes
(`when: "${var.edge_mode}"`), not a separate domain.

**Cost governance:** Budget thresholds, right-sizing, reserved capacity, resource
tagging enforcement, and chargeback allocation are governance concerns (Domain 6)
applied to cost. They extend the compliance model: "cloud spend should not exceed
$X/month" is a compliance control with evidence (the invoice) and staleness (monthly).

---

## 4. Three Techniques for Ten Domains

The ten problem domains decompose into three distinct problem characters. Each
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

Already implemented. The infra module provides sealed NodeSpec variants for cloud
resources (compute, networking, storage, K8s workloads). The TransitionPlanner diffs
desired vs actual. The NodeProvisioner converges via backend-specific APIs.

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

### Domain 7: Secret & Identity Management — Techniques 1 + 2

**Not yet implemented as a distinct domain.** Currently, secrets are consumed by
other domains (database passwords in app deployment, TLS certs in host config) but
the identity infrastructure itself — the vault paths, IAM roles, service accounts,
OAuth clients — is not managed as desired state.

**New node types needed:**

```yaml
# Secret store path: "this secret engine should exist"
catalog-db-secrets:
  type: secret_engine
  spec:
    provider: vault
    path: db/catalog
    engineType: database
    connection:
      host: catalog-db.internal
      port: 5432
    roles:
      - { name: catalog-api-read, statements: ["GRANT SELECT ON ALL TABLES..."] }
      - { name: catalog-api-write, statements: ["GRANT ALL ON ALL TABLES..."] }

# IAM role: "this role should exist with these permissions"
catalog-api-role:
  type: iam_role
  spec:
    provider: aws
    name: catalog-api-role
    assumeRolePolicy: ec2-service
    policies:
      - { action: "s3:GetObject", resource: "arn:aws:s3:::assets/*" }
      - { action: "secretsmanager:GetSecretValue", resource: "arn:aws:secretsmanager:*:*:db/catalog" }

# Service account: "this service account should exist"
deploy-agent-sa:
  type: service_account
  spec:
    provider: kubernetes
    name: deploy-agent
    namespace: platform
    annotations:
      iam.amazonaws.com/role: deploy-agent-role
    secrets: [deploy-agent-token]

# mTLS chain: "this certificate chain should exist"
service-mesh-ca:
  type: certificate_chain
  spec:
    rootCA:
      commonName: "CaseHub Internal CA"
      validity: 3650d
    intermediateCA:
      commonName: "Service Mesh Intermediate"
      validity: 365d
    leafCertificates:
      - { service: catalog-api, validity: 90d }
      - { service: order-service, validity: 90d }
    autoRenewBeforeDays: 30
```

**Provisioner:** The `IdentityProvisioner` delegates to provider-specific backends
(Vault API, AWS IAM API, K8s API). Drift detection queries the provider: does the
role exist with these policies? Does the secret engine have these roles?

**Certificate chain specifics:** The `certificate_chain` node type combines Technique
1 (the chain should exist) with Technique 2 (leaf certificates auto-renew when
approaching expiry). The `autoRenewBeforeDays` field triggers staleness-based drift
for certificate renewal — the same pattern as compliance evidence staleness.

### Domain 8: Observability Infrastructure — Technique 1

**Not yet implemented.** Observability configuration is a natural desired-state problem
— alert rules, dashboards, and SLO definitions are objects that should exist and can
drift (someone deletes an alert rule, modifies a threshold, or removes a dashboard
panel).

**New node types needed:**

```yaml
# Alert rule: "this alert should exist and fire correctly"
high-error-rate:
  type: alert_rule
  dependsOn: [catalog-api]
  spec:
    provider: prometheus
    name: CatalogApiHighErrorRate
    expression: |
      rate(http_requests_total{service="catalog-api",status=~"5.."}[5m])
      / rate(http_requests_total{service="catalog-api"}[5m]) > 0.05
    duration: 5m
    severity: critical
    annotations:
      summary: "Catalog API error rate above 5%"
    labels:
      team: platform

# Dashboard: "this dashboard should exist with these panels"
service-health-dashboard:
  type: dashboard
  spec:
    provider: grafana
    title: "Service Health Overview"
    folder: platform
    panels:
      - { title: "Request Rate", type: graph, query: "rate(http_requests_total[5m])" }
      - { title: "Error Rate", type: graph, query: "rate(http_requests_total{status=~'5..'}[5m])" }
      - { title: "P99 Latency", type: graph, query: "histogram_quantile(0.99, http_duration_seconds_bucket)" }

# SLO definition: "this service level objective should be tracked"
catalog-api-slo:
  type: slo_definition
  spec:
    service: catalog-api
    objective:
      name: availability
      target: 99.9
      window: 30d
    indicator:
      good: 'http_requests_total{status!~"5.."}'
      total: 'http_requests_total'
    alerting:
      burnRateWindow: 1h
      burnRateThreshold: 14.4

# Log pipeline: "logs should be routed to these destinations"
audit-log-routing:
  type: log_pipeline
  spec:
    source: "audit.*"
    destinations:
      - { type: long_term_storage, backend: s3, bucket: audit-logs, retention: 365d }
      - { type: real_time, backend: elasticsearch, index: audit-live, retention: 30d }

# Escalation policy: "incidents should escalate through this chain"
platform-escalation:
  type: escalation_policy
  spec:
    provider: pagerduty
    name: platform-team
    steps:
      - { delay: 5m, targets: [on-call-primary] }
      - { delay: 15m, targets: [on-call-secondary, team-lead] }
      - { delay: 30m, targets: [engineering-manager] }
```

**Provisioner:** Provider-specific backends (Prometheus/Alertmanager API, Grafana API,
PagerDuty API, ElasticSearch API). Drift detection queries each provider's API to
verify the object exists with the correct configuration.

**Cross-domain dependencies:** Alert rules reference services from Domain 3.
Dashboards visualise metrics from hosts (Domain 2) and applications (Domain 3). SLOs
span multiple infrastructure resources. These dependencies are expressed as
`dependsOn` edges in the desired-state graph — the same mechanism used by all other
domains.

### Domain 9: Environment Lifecycle — Techniques 1 + 3

**Partially implemented via YAML frontend.** The variable system, forEach, and lifecycle
phases already enable environment parameterisation. What's missing: environment-level
orchestration (promotion, parity enforcement, ephemeral lifecycle).

**How it works with existing primitives:**

```yaml
# Environment declaration — same topology, different variables per environment
desiredState:
  namespace: ecommerce
  name: "${var.environment}-storefront"

variables:
  environment: staging          # overridden per environment
  replicas: 2                   # staging: 2, production: 3
  instance_size: m5.large       # staging: m5.large, production: m5.2xlarge
  db_storage: 20Gi              # staging: 20Gi, production: 200Gi
  enable_monitoring: "true"     # staging: true, production: true, dev: false

nodes:
  catalog-api:
    type: k8s_deployment
    spec:
      name: catalog-api
      replicas: ${var.replicas}
      image: catalog-api:${var.image_version}
      resources:
        cpuRequest: "${var.cpu_request}"
        cpuLimit: "${var.cpu_limit}"
        memoryRequest: "${var.memory_request}"
        memoryLimit: "${var.memory_limit}"

  monitoring:
    type: k8s_deployment
    when: "${var.enable_monitoring}"
    spec:
      name: prometheus
      image: prom/prometheus:latest
```

**New capabilities needed:**

```yaml
# Environment promotion: "staging should match production minus scale"
# This is a GOAP problem — plan the sequence of: deploy to staging → run tests →
# switch traffic → verify → promote
promotion-gate:
  type: environment_promotion
  spec:
    source: staging
    target: production
    gates:
      - { type: test_suite, name: smoke-tests }
      - { type: test_suite, name: integration-tests }
      - { type: human_approval, role: release-manager }
    strategy: blue_green

# Ephemeral environment: "this environment should exist while the PR is open"
pr-environment:
  type: ephemeral_environment
  spec:
    trigger: pull_request
    prNumber: ${var.pr_number}
    topology: "${var.topology_template}"
    variables:
      replicas: 1
      instance_size: t3.small
    ttl: 48h                    # auto-destroy after 48 hours
    destroyOn: pr_merged        # or destroy when PR merges
```

**Decommissioning as orchestrated teardown:** Environment destruction is a
Technique 3 problem — orderly sequence with dependencies:

```
Goal: environment-destroyed
Actions:
  drain-traffic:        effects: { traffic-drained: true }
  archive-data:         preconditions: { traffic-drained: true }
                        effects: { data-archived: true }
  revoke-secrets:       preconditions: { traffic-drained: true }
                        effects: { secrets-revoked: true }
  remove-dns:           preconditions: { traffic-drained: true }
                        effects: { dns-cleaned: true }
  destroy-resources:    preconditions: { data-archived, secrets-revoked, dns-cleaned }
                        effects: { resources-destroyed: true }
  notify-dependents:    preconditions: { resources-destroyed: true }
                        effects: { dependents-notified: true }
```

### Domain 10: Data Management — Techniques 1 + 2

**Partially implemented.** Database provisioning (Domain 1) and schema migrations
(Domain 3) are sketched. The data-specific concerns are not yet covered.

**New node types needed:**

```yaml
# Replication topology: "these replicas should exist with this configuration"
catalog-db-replicas:
  type: database_replication
  dependsOn: [catalog-db]
  spec:
    primary: catalog-db
    replicas:
      - { name: catalog-db-read-1, region: eu-west-1a, mode: async }
      - { name: catalog-db-read-2, region: eu-west-1b, mode: async }
    maxLagSeconds: 30
    failoverPolicy: automatic

# Cache configuration: "this cache should exist with these settings"
catalog-cache:
  type: cache_configuration
  spec:
    provider: redis
    name: catalog-cache
    maxMemory: 6Gi
    evictionPolicy: allkeys-lru
    persistence:
      type: rdb
      intervalSeconds: 900
    replication:
      mode: sentinel
      replicas: 2

# Message queue: "this topic/queue should exist"
orders-placed-topic:
  type: message_topic
  spec:
    provider: kafka
    name: orders.placed
    partitions: 12
    replicationFactor: 3
    retentionHours: 168          # 7 days
    cleanupPolicy: delete
    config:
      max.message.bytes: 1048576
      compression.type: lz4

# Data masking: "non-prod databases should mask PII"
staging-data-masking:
  type: data_masking
  dependsOn: [staging-catalog-db]
  spec:
    target: staging-catalog-db
    rules:
      - { column: "customers.email", strategy: hash }
      - { column: "customers.phone", strategy: redact }
      - { column: "customers.address", strategy: fake }
      - { column: "orders.payment_token", strategy: nullify }
    applyOn: environment_refresh  # re-mask when staging is refreshed from prod

# Data sovereignty: "data must not leave this region"
eu-data-residency:
  type: data_sovereignty
  spec:
    scope: eu-west-1
    constraints:
      - { dataClass: pii, allowedRegions: [eu-west-1, eu-central-1] }
      - { dataClass: financial, allowedRegions: [eu-west-1] }
    enforcement: block_replication  # prevent replication to non-allowed regions
    auditFrequencyHours: 24
```

**Data sovereignty as compliance:** The `data_sovereignty` node type bridges Domain 10
(Data Management) and Domain 6 (Continuous Governance). It's a compliance control
with evidence collection — the actual-state adapter checks whether any replication
targets exist outside allowed regions. Staleness-based re-verification ensures
ongoing compliance.

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
| 7. Secret & Identity Mgmt | Declarative + staleness | **Not implemented** | Secret engine, IAM role, service account, cert chain node types |
| 8. Observability Infra | Declarative convergence | **Not implemented** | Alert rule, dashboard, SLO, log pipeline, escalation node types |
| 9. Environment Lifecycle | Declarative + GOAP | **Partially** (YAML variables + lifecycle phases) | Promotion gates, ephemeral envs, orderly teardown |
| 10. Data Management | Declarative + staleness | **Not implemented** | Replication, cache, queue, masking, sovereignty node types |

### What's Genuinely New

| New Work | Domain | Type | Complexity |
|----------|--------|------|-----------|
| Host configuration (packages, services, files, users, firewall, sysctl) | D2 | New domain module | High — SSH/agent provisioner |
| Secret engine, IAM role, service account, cert chain | D7 | New node types | High — multi-provider identity APIs |
| Alert rule, dashboard, SLO, log pipeline, escalation policy | D8 | New node types | Medium — monitoring API integrations |
| Database replication, cache config, message queue/topic | D10 | New node types | Medium — data platform APIs |
| Data masking, data sovereignty | D10 | New node types | Medium — privacy/compliance rules |
| Schema migration | D3 | New node type | Medium — Flyway/Liquibase integration |
| Credential rotation, certificate renewal, backup schedule | D4 | New node types | Medium — staleness + consumer coordination |
| Health check | D3 | New node type | Low — HTTP/TCP probe |
| Environment promotion gates, ephemeral environments | D9 | New node types | Medium — GOAP + lifecycle integration |
| GOAP action libraries (rolling update, migration, rotation, teardown) | D5 | Action definitions | Medium — domain-specific preconditions/effects |

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

### Priority Ordering

| Priority | Domain | Why | Builds On |
|----------|--------|-----|-----------|
| 1 | Resource Provisioning — topology exemplars (D1) | Already in spec — proves the YAML frontend | Existing infra module |
| 2 | Host Configuration (D2) | Largest gap — 70-80% of Day 1+ operations | D1 (hosts depend on compute instances) |
| 3 | Secret & Identity Management (D7) | Cross-cutting — every other domain consumes secrets and identity | D1 (Vault/IAM are resources), D2 (agent deployment) |
| 4 | Periodic Operations (D4) | Credential rotation, cert renewal, backups — extends proven compliance pattern | D7 (rotates secrets from D7), D3 (backups for D3 databases) |
| 5 | Data Management (D10) | Replication, caching, queues — data outlives applications | D1 (databases), D4 (backup schedules) |
| 6 | Observability Infrastructure (D8) | Monitoring, alerting, dashboards — the nervous system | D1-D5 (observes everything), D7 (secrets for API access) |
| 7 | Application Lifecycle (D3) — migrations, health | Schema migrations and health verification | D1 (databases), D2 (host config) |
| 8 | Orchestrated Sequences (D5) — GOAP actions | Rolling updates, topology migrations, complex rotations | D1-D4 (actions operate on all domains) |
| 9 | Environment Lifecycle (D9) | Promotion, parity, ephemeral, teardown | D1-D8 (environments compose all domains) |
| 10 | Continuous Governance extensions (D6) | Cross-domain governance, cost governance | D1-D9 (governs everything) |

### Dependency Graph

```
D1 Resource Provisioning (topology exemplars)
 │
 ├──► D2 Host Configuration
 │     │
 │     ├──► D7 Secret & Identity
 │     │     │
 │     │     ├──► D4 Periodic Operations (rotation, renewal, backup)
 │     │     │
 │     │     └──► D8 Observability Infrastructure
 │     │
 │     └──► D3 Application Lifecycle (migrations, health)
 │
 ├──► D10 Data Management
 │
 ├──► D5 Orchestrated Sequences (GOAP actions for D1-D4)
 │
 └──► D9 Environment Lifecycle (composes D1-D8)
      │
      └──► D6 Continuous Governance extensions (governs D1-D9)
```

### Separate Design Specs Needed

Each domain beyond D1 (already in spec) needs its own design spec:

| Spec | Domain | New Module? | Key Decisions |
|------|--------|------------|---------------|
| Host Configuration | D2 | `casehub-ops-hostconfig` | SSH vs agent connection model, host resolution from graph, package manager abstraction |
| Secret & Identity | D7 | `casehub-ops-identity` | Multi-provider (Vault, AWS IAM, K8s SA), cert chain lifecycle, zero-trust integration |
| Periodic Operations | D4 | Extends compliance pattern | Dual-credential rotation, ACME cert renewal, backup verification |
| Data Management | D10 | `casehub-ops-data` or extend infra | Replication topology, masking rules, sovereignty enforcement |
| Observability | D8 | `casehub-ops-observability` | Multi-provider (Prometheus, Grafana, PagerDuty), SLO tracking |
| Application Lifecycle | D3 | Extends app module | Flyway/Liquibase integration, health check escalation |
| GOAP Actions | D5 | Action libraries in engine | Rolling update, migration, rotation, teardown action sets |
| Environment Lifecycle | D9 | Extends YAML frontend | Promotion gates, ephemeral lifecycle, parity enforcement |
| Governance Extensions | D6 | Extends compliance | Cost governance, cross-domain policy, tagging enforcement |

---

## 9. Reusable Library Landscape

### Principle: Quarkus First, Then Liberal OSS, Then Custom

CaseHub runs on Quarkus. Before writing custom code or adopting a standalone library,
check whether a Quarkus extension already solves the problem — Quarkus extensions get
build-time optimisation, native compilation support, and CDI integration for free.

### Existing CaseHub Capabilities (Don't Rebuild)

| Capability | Location | What It Does |
|---|---|---|
| `SecretManager` SPI | `casehub-platform-api`, `casehub-engine-common` | Secret resolution — abstraction over secret backends |
| `CredentialResolver` SPI | `casehub-platform-api` | Credential resolution for workers and services |
| `WorkerCredential` | `casehub-platform-api` | Credential record for worker identity |
| fabric8 K8s client | `casehub-ops-app` (already a dependency) | K8s resource provisioning, watches, events |

D7 (Secret & Identity) should **extend** the existing `SecretManager` and
`CredentialResolver` SPIs rather than building a parallel secret management system.
The desired-state model manages the secret infrastructure (Vault paths, IAM roles);
the existing SPIs resolve secrets at runtime.

### Library Map by Domain

| Domain | Concern | Quarkus Extension | Standalone Library | License | What CaseHub Writes |
|--------|---------|-------------------|-------------------|---------|---------------------|
| **D2** | SSH connectivity | — | **SSHJ** | Apache 2.0 | Connection pool + command executor |
| **D2** | Template engine | — | **Jinjava** (HubSpot) | Apache 2.0 | Integration with `host_file` provisioner |
| **D2** | Package/service mgmt | — | **None exists** | — | SPI + per-OS adapters (~80 lines each) |
| **D4/D7** | TLS certificates | **Quarkus TLS Registry** (built-in ACME) | **acme4j** (if non-Quarkus) | Apache 2.0 | `certificate_renewal` provisioner |
| **D7** | HashiCorp Vault | **quarkus-vault** (v4.9.0) | jopenlibs vault-java-driver | Apache 2.0 / MIT | `secret_engine` provisioner delegates to quarkus-vault |
| **D1** | K8s resources | **quarkus-kubernetes-client** (fabric8) | — | Apache 2.0 | Already in use |
| **D8** | Prometheus metrics | **quarkus-micrometer-registry-prometheus** | — | Apache 2.0 | `alert_rule` provisioner uses Prometheus API |
| **D10** | Kafka topics | **quarkus-messaging-kafka** | Apache Kafka AdminClient | Apache 2.0 | `message_topic` provisioner |
| **D10** | RabbitMQ | **quarkus-messaging-rabbitmq** | RabbitMQ Java client | Apache 2.0 | Queue/exchange provisioner |
| **D1** | AWS resources | — | **AWS SDK v2** | Apache 2.0 | Cloud backend for infra module |
| **D1** | Azure resources | — | **Azure SDK** | MIT | Cloud backend |
| **D1** | GCP resources | — | **Google Cloud Java** | Apache 2.0 | Cloud backend |
| **D1** | DNS protocol | — | **dnsjava** | BSD | DNS record provisioner |
| **D8** | Grafana dashboards | — | Grafana REST API (HTTP client) | — | `dashboard` provisioner calls REST API |

### Key Findings

**Jinjava solves the #1 gap from the stress test.** HubSpot's Jinja2-compatible
template engine for Java (Apache 2.0) means ops people use the exact same template
syntax they already know. Filters, conditionals, loops — all supported. This is the
`host_file` provisioner's template engine.

**quarkus-vault replaces standalone Vault clients.** The Quarkiverse extension
(v4.9.0, July 2026) provides Vault as a config source, database credential fetching,
TOTP support, and automatic token renewal — all with Quarkus-native build-time
optimisation. CaseHub's existing `SecretManager` SPI can delegate to `quarkus-vault`
for Vault-backed secret resolution. No need for a separate Vault Java driver.

**Quarkus TLS Registry has built-in ACME.** Server-side certificate management with
Let's Encrypt, auto-renewal, and cert-manager integration is built into Quarkus.
For managing certificates on *other* hosts (the D2/D4 use case), `acme4j` provides
the ACME client protocol.

**Host-level configuration is genuinely novel.** No Java library exists for
cross-platform package/service/file management on remote hosts. This is the one area
where CaseHub writes original code — but it's thin adapters over SSH + CLI commands,
not a monolithic framework. The SPI architecture keeps each OS-family adapter small
(~80 lines).

### What CaseHub Writes From Scratch (Updated)

| New Code | Lines (est.) | Why |
|----------|-------------|-----|
| D2 `HostConfigProvisioner` + SSH integration | ~500 | Novel composition of SSHJ + desired-state model |
| D2 `PackageManager` SPI + 3 impls | ~300 | Too thin for a library — CLI wrappers over SSH |
| D2 `ServiceManager` SPI + 2 impls | ~200 | Same — `systemctl`/`openrc` wrappers |
| D2 Additional host SPIs (file, user, firewall, cron, mount) | ~600 | Per-concern adapters |
| Node type records (all domains) | ~500 | Domain-specific — these ARE the design |
| Provisioner adapters (wrap Quarkus extensions/libraries) | ~1,000 | Integration glue |
| GOAP action libraries (D5) | ~500 | Domain-specific planning actions |
| **Total genuinely new code** | **~3,600** | Everything else is Quarkus extensions or existing CaseHub |

---

## 10. Adversarial Stress Test Results

The three-tier model (first-class node types + generic operations + script escape
hatch) was adversarially stress-tested against the top 43 Ansible modules.

**Verdict:** The claim holds for ~85% of production use cases. 30 of 43 modules
covered by Tiers 1-2.

**5 genuine gaps identified and addressed:**

| Gap | Fix | Status |
|-----|-----|--------|
| Templating language | **Jinjava** (Jinja2-compatible, Apache 2.0) | Solved — library exists |
| 5 missing node types (cron, mount, package_repo, selinux, alternatives) | Add to D2 sealed variants | Scope item |
| Line-level file mutation (lineinfile/blockinfile) | Add `host_file_line` and `host_file_block` modes | Scope item |
| Cross-host delegation | Model as separate graph nodes with dependencies | Architecture pattern (already natural) |
| Progressive canary rollout with abort thresholds | Engine case with adaptive batch sizing | Design work needed (D5) |

**False alarms (look like gaps but aren't):**
- Roles → YAML modules (more powerful — graph-level composition)
- Inventory → DesiredStateGraph IS the inventory
- Vault → D7 + quarkus-vault + existing SecretManager SPI
- Tags → Different paradigm (continuous convergence, not selective execution)
- Handlers → `notifyService` on `host_file`
- Facts → Actual-state adapter (continuous, not per-run)

---

## 11. Sources

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

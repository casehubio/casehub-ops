# Deployment Topology Levels

YAML-driven integration tests demonstrating CaseHub app deployment
via the desiredstate reconciliation loop. Each level adds a platform
capability on top of a working core.

## Levels

| # | Folder | Methodology Step | What it demonstrates |
|---|--------|-----------------|---------------------|
| 01 | [01-hello-llm/](01-hello-llm/) | DEPLOY — First App | Deploy a complete LLM app — eidos + engine + LLM via claudony |
| 02 | [02-accountability/](02-accountability/) | DEPLOY — Audit Trail | Add ledger + work — every deployment audited, every task tracked |
| 03 | [03-case-management/](03-case-management/) | DEPLOY — Case Operations | Add qhorus — case-driven operations |
| 04 | [04-llm-fleet/](04-llm-fleet/) | DEPLOY — Fleet Scaling | Scale to a fleet of 3 differentiated LLM instances via forEach |
| 05 | [05-config-drift/](05-config-drift/) | OPERATE — Drift Reconciliation | Detect config drift and reconcile only changed nodes |
| 06 | [06-lifecycle/](06-lifecycle/) | OPERATE — Suspend and Resume | Suspend and resume fleet instances with status transitions |
| 07 | [07-topology-mutations/](07-topology-mutations/) | OPERATE — Declarative Mutations | Declarative add/remove — prune before grow |
| 08 | [08-fleet-observability/](08-fleet-observability/) | OPERATE — Health and Recovery | Health probes, status monitoring, degradation → recovery |

Each level folder contains the topology YAML and a README.md walkthrough.

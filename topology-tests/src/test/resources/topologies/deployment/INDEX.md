# Deployment Topology Levels

YAML-driven integration tests demonstrating CaseHub app deployment
via the desiredstate reconciliation loop. Each level adds a platform
capability on top of a working core.

**Use case companion track:** [use-cases/](../use-cases/) — real-world
application scenarios built on the same platform capabilities.

## Levels

| # | Folder | Methodology Step | Use Case Companion | What it demonstrates |
|---|--------|-----------------|-------------------|---------------------|
| 01 | [01-hello-llm/](01-hello-llm/) | DEPLOY — First App | [UC01 Customer FAQ](../use-cases/UC01-customer-faq-agent/) | Deploy a complete LLM app — eidos + engine + LLM via claudony |
| 02 | [02-accountability/](02-accountability/) | DEPLOY — Audit Trail | [UC02 Audited Support](../use-cases/UC02-audited-support/) | Add ledger + work — every deployment audited, every task tracked |
| 03 | [03-case-management/](03-case-management/) | DEPLOY — Case Operations | [UC03 Escalation Flow](../use-cases/UC03-escalation-flow/) | Add qhorus — case-driven operations |
| 04 | [04-llm-fleet/](04-llm-fleet/) | DEPLOY — Fleet Scaling | [UC04 Multi-Specialist](../use-cases/UC04-multi-specialist-desk/) | Scale to a fleet of 3 differentiated LLM instances via forEach |
| 05 | [05-config-drift/](05-config-drift/) | OPERATE — Drift Reconciliation | [UC05 Policy Rollout](../use-cases/UC05-policy-rollout/) | Detect config drift and reconcile only changed nodes |
| 06 | [06-lifecycle/](06-lifecycle/) | OPERATE — Suspend and Resume | [UC06 Maintenance Window](../use-cases/UC06-maintenance-window/) | Suspend and resume fleet instances with status transitions |
| 07 | [07-topology-mutations/](07-topology-mutations/) | OPERATE — Declarative Mutations | [UC07 Market Expansion](../use-cases/UC07-market-expansion/) | Declarative add/remove — prune before grow |
| 08 | [08-fleet-observability/](08-fleet-observability/) | OPERATE — Health and Recovery | [UC08 SLA Monitoring](../use-cases/UC08-sla-monitoring/) | Health probes, status monitoring, degradation → recovery |

Each level folder contains the topology YAML and a README.md walkthrough.

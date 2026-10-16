# Deployment Topology Tutorials

YAML-driven integration tests demonstrating CaseHub app deployment
via the desiredstate reconciliation loop. Each level adds a platform
capability on top of a working core.

## Levels

| # | File | What it teaches |
|---|------|-----------------|
| 01 | [01-hello-llm.yaml](01-hello-llm.yaml) | Deploy a complete LLM app — eidos + engine + LLM via claudony |
| 02 | [02-accountability.yaml](02-accountability.yaml) | Add ledger + work — every deployment audited, every task tracked |
| 03 | [03-case-management.yaml](03-case-management.yaml) | Add qhorus — case-driven operations |
| 04 | [04-llm-fleet.yaml](04-llm-fleet.yaml) | Scale to a fleet of 3 differentiated LLM instances via forEach |
| 05 | [05-config-drift.yaml](05-config-drift.yaml) | Detect config drift and reconcile only changed nodes |
| 06 | [06-lifecycle.yaml](06-lifecycle.yaml) | Suspend and resume fleet instances with status transitions |
| 07 | [07-topology-mutations.yaml](07-topology-mutations.yaml) | Declarative add/remove — prune before grow |
| 08 | [08-fleet-observability.yaml](08-fleet-observability.yaml) | Health probes, status monitoring, degradation → recovery |

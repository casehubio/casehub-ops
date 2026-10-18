# UC05: Policy Rollout

**Topology companion:** [L05 Config Drift](../../deployment/05-config-drift/)
**Prerequisites:** UC04

## Scenario

Roll out a policy change to 50 agents — update response guidelines, compliance
rules, or knowledge boundaries. The platform detects which agents are affected,
updates only those, and leaves the rest untouched. One YAML change, selective
reconciliation.

## What the topology level teaches

L05 shows **how** config drift detection and selective reconciliation work.
This use case shows **why** — fleet-wide policy updates must be surgical.

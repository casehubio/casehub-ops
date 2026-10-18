# UC08: SLA Monitoring

**Topology companion:** [L08 Observability](../../deployment/08-fleet-observability/)
**Prerequisites:** UC07

## Scenario

SLA health probes detect degradation before it impacts customers. An agent's
response latency increases — the platform detects DEGRADED status, triggers
recovery, and restores PRESENT before the SLA breach threshold. Continuous
monitoring, not periodic checks.

## What the topology level teaches

L08 shows **how** health probes, status monitoring, and degradation recovery
work. This use case shows **why** — SLA guarantees require proactive detection.

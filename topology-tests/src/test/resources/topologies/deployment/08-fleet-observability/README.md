# Level 08: Fleet Observability

**Methodology step:** OPERATE — Health and Recovery
**Prerequisites:** Level 04, Level 07

## When to use this pattern

You need continuous observation of your fleet — not just "is it up" but what kind of not-up it is, with automated classification and recovery.

## Walkthrough

1. Deploy the fleet and verify all instances report HEALTHY
2. Observe the signal chain at rest: health probes return Healthy, status polls return PRESENT, no action needed
3. Introduce degradation — a health probe returns Unhealthy with a diagnostic reason
4. Observe the DEGRADED → DRIFTED mapping: the instance is flagged for attention but not auto-recovered
5. Simulate instance failure — status transitions to DOWN / ABSENT
6. The reconciliation loop detects the missing node and reconciles to restore it
7. After recovery, exercise all LLM endpoints to verify the fleet is genuinely operational
8. Verify the full signal chain was recorded: health probe → status poll → HealthCheckResult → NodeStatus → reconciliation event

## Verify after deployment

1. Health probes return per-instance HealthCheckResult with diagnostic reason
2. DEGRADED maps to DRIFTED — flagged for attention, no auto-recover
3. SUSPENDED maps correctly — operator-initiated, no auto-recover
4. DOWN maps to ABSENT — triggers reconciliation to restore the instance
5. Recovery produces a fully operational fleet (LLM endpoints respond correctly)

## Common failure modes

- Health probe returns wrong status → check HealthCheckResult mapping in the deployment target
- No distinction between DEGRADED and DOWN → verify NodeFaultGanglion classification logic
- Recovery doesn't restore the instance → check reconciliation loop actual-state detection and re-deploy logic
- Suspended instance incorrectly triggers recovery → verify fault detection excludes SUSPENDED from the recovery path

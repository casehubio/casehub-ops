# Level 14: Fault Recovery

**Methodology step:** OPERATE — Fault Detection and Replacement
**Prerequisites:** Level 10

## When to use this pattern

You need automatic fault detection and recovery for a managed pool of LLM agents, with escalation to human review when automatic recovery fails.

## Walkthrough

1. Deploy the support application and its managed pool (same as Level 10)
2. Verify initial convergence — all nodes PRESENT
3. Simulate a node failure — remove the pool entry from the parent app
4. Inject provisioning failures to simulate structural issues (OOM, resource exhaustion)
5. Observe ReconciliationLoop detecting ABSENT and attempting re-provision
6. Observe PROVISION_FAILED events flowing through FaultPolicyEngine to ClaudonyFaultPolicy
7. If failures are transient (< 3): automatic recovery on next successful provision
8. If failures are persistent (>= 3): ClaudonyFaultPolicy adds a claudony_review node for human review
9. Verify audit trail records the full fault sequence via CloudEvents

## Verify after deployment

1. Transient failures: pool recovers automatically, no review node created
2. Persistent failures: review node added to desired graph after 3 PROVISION_FAILED events
3. Other fleet members remain PRESENT throughout fault recovery
4. Audit trail contains NODE_FAULTED events with PROVISION_FAILED detail
5. Audit trail contains RECONCILIATION_COMPLETED events marking convergence

## Common failure modes

- Reconciliation does not retry — check that FailableClaudonyProvisioner budget is configured correctly
- Review node not created — verify fault count reaches threshold 3 (need 3+ reconciliation cycles with failures)
- NODE_FAULTED events missing — check FaultPolicyEngine is wired with ClaudonyFaultPolicy
- Pool recovers before fault count reaches threshold — reduce failure budget or increase reconciliation cycles

# Level 10: Pool Deployment

**Methodology step:** DEPLOY — Managed Instance Pool
**Prerequisites:** Level 04, Level 06

## When to use this pattern

You need a managed pool of interchangeable LLM support agents that scales with load and self-heals when instances become unresponsive.

## Walkthrough

1. Deploy the support application and its managed pool (minActive=3, maxActive=8)
2. Verify the pool converges — reconciliation provisions the pool on the parent app
3. Simulate load increase — update desired state with larger pool bounds
4. Observe reconciliation detecting drift and re-provisioning with the new configuration
5. Simulate load decrease — update desired state with smaller pool bounds
6. Observe reconciliation converging back to the reduced configuration
7. Simulate instance failure — remove the pool entry from the parent app
8. Observe reconciliation detecting ABSENT and re-provisioning the pool automatically

## Verify after deployment

1. Pool node is PRESENT with correct minActive/maxActive on the parent app
2. Scaling up: updated desired state triggers drift detection and re-provisioning
3. Scaling down: reduced bounds are reconciled without interrupting the parent app
4. Unhealthy replacement: removed pool entry is detected as ABSENT and re-provisioned
5. Audit trail records all pool provisioning and reconciliation events

## Common failure modes

- Pool provisioning fails with "parent app not registered" — verify support-app is provisioned before support-pool (dependsOn ordering)
- Drift not detected after scaling — check that SpecHashStore records are updated on provision
- Pool removal not detected — verify ClaudonyActualStateAdapter checks pool entries by agentId
- Reconciliation does not re-provision — check that FaultPolicyEngine handles claudony_pool type

# Level 02: Accountability

**Methodology step:** DEPLOY — Audit Trail
**Prerequisites:** Level 01

## When to use this pattern

Every deployment action needs an audit record. You need tamper-evident tracking of what was deployed, when, and by whom. This level adds the accountability layer that makes operations auditable.

## Walkthrough

1. Deploy all Level 01 services (manifest, eidos, engine, my-llm)
2. Deploy ledger — the audit trail service that records every deployment event
3. Deploy work — the task management service that tracks deployment operations as work items
4. Observe that deployment events from steps 1-3 are themselves recorded in the ledger
5. Check the audit trail — every service registration has a corresponding ledger entry

## Verify after deployment

1. Ledger service is running and registered with claudony
2. Work service is running and registered with claudony
3. Deployment events have corresponding audit entries in the ledger
4. Work service tracks deployment tasks with status and timestamps

## Common failure modes

- Missing audit entries → check ledger dependency ordering; ledger must be deployed before the services it audits
- Work items not created → verify work service is registered after ledger (dependency edge required)
- Audit entries present but incomplete → check event propagation between deployment target and ledger

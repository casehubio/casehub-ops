# Level 03: Case Management

**Methodology step:** DEPLOY — Case Operations
**Prerequisites:** Level 02

## When to use this pattern

Operations should be case-driven — each deployment tracked as a case instance with lifecycle, ownership, and audit history. Cases are the fundamental unit of work in CaseHub.

## Walkthrough

1. Deploy all Level 02 services (manifest, eidos, engine, my-llm, ledger, work)
2. Deploy qhorus — the case management service
3. Observe that deployment operations now create case instances in qhorus
4. Each case links to its audit trail entries in the ledger
5. Cases track the full lifecycle of a deployment operation from request through completion

## Verify after deployment

1. Qhorus service is running and registered with claudony
2. Deployment operations create case instances in qhorus
3. Cases are linked to the corresponding audit trail entries
4. Case lifecycle reflects deployment status (pending → active → completed)

## Common failure modes

- Cases not created → check qhorus dependency on ledger and work services
- Case not linked to audit trail → verify event propagation from ledger to qhorus
- Case lifecycle stuck → check that deployment events are reaching qhorus through the event chain

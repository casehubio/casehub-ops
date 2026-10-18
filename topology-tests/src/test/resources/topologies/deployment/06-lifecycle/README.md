# Level 06: Lifecycle

**Methodology step:** OPERATE — Suspend and Resume
**Prerequisites:** Level 04
**Use case companion:** [UC06 Maintenance Window](../../use-cases/UC06-maintenance-window/)

## When to use this pattern

You need to take an instance offline temporarily without destroying its registration — maintenance, cost management, or testing how the fleet operates in a degraded state.

## Walkthrough

1. Deploy the fleet from Level 04's topology (3 LLM instances + infrastructure)
2. All 3 fleet instances report HEALTHY status
3. Suspend the analyst instance via the deployment target's lifecycle API
4. Verify the analyst's status transitions to SUSPENDED — registration is preserved, processing is paused
5. Verify coder and assistant remain HEALTHY and unaffected
6. Resume the analyst instance
7. Verify the analyst returns to HEALTHY status
8. Exercise the analyst's LLM endpoint to confirm it is fully operational after resume

## Verify after deployment

1. Suspended instance shows SUSPENDED status, not ABSENT (registration preserved)
2. Other fleet instances are unaffected by the suspend operation
3. Resume restores the instance to HEALTHY status
4. The platform distinguishes "suspended by operator" from "down due to failure" — no auto-recover triggered

## Common failure modes

- Suspend destroys registration instead of pausing → verify the deployment target supports stateful lifecycle via `supportsStatefulLifecycle()`
- Status shows ABSENT instead of SUSPENDED → check NodeStatus mapping in the actual-state adapter
- Auto-recover triggers on a suspended instance → verify fault detection logic excludes SUSPENDED from the recovery path

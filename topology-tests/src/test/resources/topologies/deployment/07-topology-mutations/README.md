# Level 07: Topology Mutations

**Methodology step:** OPERATE — Declarative Mutations
**Prerequisites:** Level 04
**Use case companion:** [UC07 Market Expansion](../../use-cases/UC07-market-expansion/)

## When to use this pattern

Your fleet needs to change shape — swap out one instance for another without redeploying the whole topology. Change the data, not the code.

## Walkthrough

1. Deploy the fleet from Level 04's topology (analyst, coder, assistant + infrastructure)
2. Apply Level 07 YAML — the CSV data block removes the assistant row and adds a researcher row
3. The platform diffs the new desired state against the running actual state
4. The TransitionPlanner identifies one removal (assistant) and one addition (researcher)
5. Prune executes first — assistant is deregistered from claudony
6. Grow executes second — researcher is registered with claudony
7. Verify analyst and coder are undisturbed throughout the mutation
8. Exercise the new researcher's LLM endpoint to confirm it is operational

## Verify after deployment

1. Assistant is fully deregistered from claudony (status ABSENT)
2. Researcher is registered with correct config (role, capability, archetype, domain)
3. Analyst and coder configs are unchanged
4. Prune executed before grow — dependency ordering enforced by TransitionPlanner

## Common failure modes

- Both old and new instance present simultaneously → check that graph diff correctly detects removals
- New instance fails to register → verify prune-before-grow ordering; the old node may hold a resource or dependency slot
- Wrong node removed → verify forEach expansion matches the updated CSV data block

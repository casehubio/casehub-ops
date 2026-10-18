# Level 05: Config Drift

**Methodology step:** OPERATE — Drift Reconciliation
**Prerequisites:** Level 04
**Use case companion:** [UC05 Policy Rollout](../../use-cases/UC05-policy-rollout/)

## When to use this pattern

Your fleet is running. Config changes are needed. You need to update only what changed — not redeploy everything.

## Walkthrough

1. Deploy the fleet from Level 04's topology (3 LLM instances + infrastructure)
2. Observe the initial state — analyst has capability "deep", domain "analysis"
3. Apply Level 05 YAML — analyst config changes to capability "analytical", domain "data-analysis"
4. Reconcile — the platform diffs desired state against actual state, identifies exactly one changed node
5. Only the analyst node receives an update call; coder and assistant are untouched
6. Verify the analyst's LLM behavior now reflects "analytical investigator in the data-analysis domain"
7. Check the audit trail — reconciliation event recorded with a config diff summary

## Verify after deployment

1. Only the analyst node received an update call
2. Coder and assistant configs are unchanged (capability "fast" and "balanced" respectively)
3. Analyst LLM behavior reflects the new config (analytical investigator in data-analysis domain)
4. Reconciliation event recorded with diff summary showing the specific config changes

## Common failure modes

- Full redeploy instead of selective reconciliation → check graph diff logic in TransitionPlanner
- Config change pushed but not reflected in LLM behavior → verify eidos descriptor resolution with the new config values
- Update rejected by deployment target → check 409 conflict handling in ClaudonyDeploymentTarget (409 triggers update-in-place)

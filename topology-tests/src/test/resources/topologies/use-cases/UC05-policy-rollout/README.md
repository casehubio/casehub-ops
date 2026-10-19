# UC05: Policy Rollout

**Topology companion:** [L05 Config Drift](../../deployment/05-config-drift/)
**Prerequisites:** UC04

## Scenario

The support desk from UC04 is running — three specialists handling billing,
technical, and returns queries. A policy change arrives: billing escalation
rules are too aggressive. Too many straightforward billing queries are being
resolved without supervisor review. The threshold needs to drop from "high"
to "medium", and the greeting should shift from billing-specific to a broader
account focus.

One YAML change. One CSV field updated. The platform diffs desired state
against actual state, identifies exactly one changed agent (billing), and
pushes the update. Technical and returns agents are untouched — their config
hasn't changed, so they don't get redeployed.

This is the difference between "redeploy everything" and "reconcile what
changed." In a fleet of 50 agents, the distinction is the difference between
a 30-minute maintenance window and a 30-second targeted update.

## What Changed

| Field | UC04 (before) | UC05 (after) |
|-------|--------------|--------------|
| billing greeting | "How can I help with your billing?" | "How can I help with your account?" |
| billing escalation | high | medium |
| technical | _(unchanged)_ | _(unchanged)_ |
| returns | _(unchanged)_ | _(unchanged)_ |

## How It Works

1. The fleet is already running from UC04 — 3 infrastructure nodes + 3
   support agents
2. Apply the UC05 YAML — billing agent's `escalation_threshold` and
   `greeting` fields differ from UC04
3. The platform **diffs desired state against actual state** and identifies
   one changed node: `support-agent.billing`
   ([see L05 for the mechanism](../../deployment/05-config-drift/))
4. Only the billing agent receives an update — technical and returns are
   untouched because their config rows haven't changed
5. Verify the billing agent now uses the new greeting and medium threshold

## What To Look For

1. **6 total nodes** — same fleet structure as UC04
2. **Billing config changed** — escalation "medium", greeting "How can I help with your account?"
3. **Technical unchanged** — escalation still "medium", same greeting as UC04
4. **Returns unchanged** — escalation still "low", same greeting as UC04
5. **Selective reconciliation** — only the billing agent was updated

## Why This Matters

Fleet-wide policy changes happen constantly — updated compliance rules, new
greeting standards, adjusted escalation thresholds. Without selective
reconciliation, every change means redeploying the entire fleet. With it,
changes are surgical: the platform knows which agents are affected and
updates only those.

The YAML change is trivial — one CSV field. The operational value is that
the platform handles the hard part: determining what changed, what didn't,
and acting on exactly the delta. Scale this to 50 agents across 3 regions
and the difference is existential.

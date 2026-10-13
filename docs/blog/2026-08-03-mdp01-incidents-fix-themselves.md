---
layout: post
title: "When Incidents Fix Themselves"
date: 2026-08-03
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-ops]
tags: [incident-response, desiredstate, child-case, reconciliation, service-lifecycle]
series: issue-34-incident-response-child-case
---

The ops console had incident response wired from day one — the binding fired, the child case spawned, and the worker did nothing. A stub that proved the plumbing worked without doing the plumbing's job. Replacing that stub forced a question I hadn't properly thought through: what does "fix an incident" actually mean when your entire infrastructure model is desired-state reconciliation?

The instinct is to reach for K8s primitives. Service down? Delete the pod, let the Deployment controller recreate it. Crash-looping? Roll back the Deployment spec to a previous revision. These work, but they create a second mutation path alongside the reconciliation loop — the system now has two ways to change infrastructure, and they don't know about each other.

The answer turned out to be simpler than the question. In a desired-state model, every remediation is the same operation: update what you want the world to look like, and let the reconciliation loop make it so. Restart a service? Increment a generation counter on the desired state — the loop sees a mismatch and re-provisions the pods. Roll back? Swap the image reference back to the previous known-good version — the loop provisions the old image. Scale up? Add a replica to the desired count — the loop creates the pod. Three different user-facing actions, one mechanism underneath.

This collapses the implementation to something surprisingly compact. `IncidentResponseCaseDescriptor` has four workers chained by context changes:

**Assess** receives the incident signal and classifies it. `SERVICE_DOWN` or `DEGRADED` → restart. `CRASH_LOOP` → rollback (the service keeps crashing; restarting won't help; revert to the last image that worked). `RESOURCE_PRESSURE` → scale. Anything else → escalate to a human immediately. The classification is a lookup table, not a machine learning model — the value is in having a table at all, because it means the system has an opinion before a human gets paged.

**Remediate** takes the assessment's decision and calls `ApplicationLifecycleService` — the same service that handles deploys and scaling. `restartService()` bumps a `restartGeneration` counter on the `ServiceDefinition`, which propagates through the goal compiler into the desired-state metadata. When the reconciliation loop next compares desired vs actual, the counter has changed, so the node looks drifted, so it gets re-provisioned. This is the K8s equivalent of `kubectl rollout restart` — but expressed as a desired-state change rather than an imperative command. `rollbackService()` finds the most recent successful deployment that used a different image for the affected service and swaps the image back. The loop handles the rest.

**Verify** registers the affected node IDs with `NodeConvergenceTracker`. When the reconciliation loop provisions the nodes and they come back healthy, the tracker signals `.incidentStatus = "resolved"` on the case. The child case closes. If the parent is a service lifecycle case, the health dimension status transitions from DOWN → REMEDIATING → HEALTHY.

**Escalate** fires in two situations: the assessment didn't recognise the incident type (no automated action available), or the remediation attempt threw an exception (service not found, no previous deployment to roll back to). Either way, the case writes `.incidentStatus = "escalated"` and closes. A future version will create a WorkItem for human review; for now, the case reaches a terminal state with a summary of what was attempted and why it failed.

The design review surfaced a subtle problem I'd missed. What happens when the remediate worker throws an exception? The natural instinct — `WorkerResult.failed()` — is a terminal worker state in the engine. It doesn't write to the case context. It doesn't trigger any binding. The completion expression never evaluates to true. The case hangs forever, invisible and unreachable. The fix: the remediate worker catches exceptions and writes `.escalationRequired` instead, routing to the escalation path. Every incident either resolves or escalates — there's no third outcome where it silently disappears.

What makes this work isn't the incident logic itself — it's the architectural decision that all remediation flows through desired-state reconciliation. The ops console now has a single mechanism for every infrastructure mutation, whether it's a human deploying through the wizard, a CVE scanner triggering an image update, drift detection correcting configuration, or incident response restarting a crashed service. One mechanism, one audit trail, one set of fault policies, one approval workflow. The child case adds the "why" — the reconciliation loop provides the "how."

The next child case in Batch 2 is compliance-remediation (#37). It follows the same assess → remediate → verify → escalate pattern, with the compliance domain's vocabulary substituted in. The pattern is proving reusable — which is the point of having a pattern.

---
layout: post
title: "The case that doesn't call anyone"
date: 2026-08-12
entry_type: note
subtype: diary
projects: [casehub-ops]
tags: [compliance, child-case, cdi, architecture, desired-state]
---

The compliance module knows everything about controls — evidence freshness, posture scores, framework mappings. The ops-console app knows everything about infrastructure — K8s deployments, config maps, cluster topology. When a compliance violation fires and spawns a remediation child case, the natural instinct is to reach across: import the compliance module, call ComplianceEvidenceService, evaluate the posture, decide the fix.

That doesn't work here. The CDI single-domain constraint means each desired-state domain module provides its own `@ApplicationScoped` SPI implementations. Put two on the same classpath and CDI throws ambiguous dependency errors at startup. The compliance module and the app module are designed to run in different applications.

I spent the brainstorming session working through the alternatives — a bridge service, a shared API, a qualified CDI workaround — and the answer turned out to be: none of the above. The case doesn't need the compliance module at all.

The violation signal that triggers the case already carries everything: controlId, controlType, outcome, frameworks, tenancyId. The assess worker maps `controlType` to a config update via a static `Map` — LOG_RETENTION gets `{LOG_RETENTION_DAYS: "365"}`, ENCRYPTION_AT_REST gets `{ENCRYPTION_ENABLED: "true", ENCRYPTION_CIPHER: "AES-256"}`. That's the entire domain bridge: a 5-line lookup table inside the descriptor.

We built the four-phase worker chain (assess → remediate → verify → escalate) to match the incident-response pattern from #34. The only new piece is `ApplicationLifecycleService.updateServiceConfig()`, which merges key-value pairs into a service's env map, recompiles the desired state per cluster, and returns affected node IDs for convergence tracking. Same persistence and recompile pattern as `updateServiceReplicas()` and `restartService()` — the method is 45 lines, most of which are the same for-loop-over-clusters boilerplate.

The honest limitation: updating a ConfigMap with `ENCRYPTION_ENABLED=true` doesn't enable encryption at the storage layer. That requires cloud provider APIs — AWS KMS, GCP CMEK — which live outside the app module's reach. The auto-fix is demonstrative. It shows the remediation pattern for the reference architecture, not a production-grade encryption enablement. Four of six control types (ACCESS_REVIEW, INCIDENT_RESPONSE, DATA_PROCESSING, AI_RISK_ASSESSMENT) always escalate because their fixes genuinely require human judgment.

What's interesting is the architectural constraint that drove the design. The CDI single-domain rule exists for a good reason — it prevents SPI implementation conflicts — but it also means cross-domain orchestration has to work through signals, not method calls. The case blackboard IS the integration layer. The assess worker IS the domain knowledge bridge. No new abstractions needed.

# Use Case Levels

Real-world application scenarios built on CaseHub platform capabilities.
Each use case level maps to a topology level — the topology teaches the
mechanism, the use case applies it to a concrete business problem.

YAML-only — no Java test companions. Use case YAMLs are validated by the
same compilation and reconciliation tests that cover the topology track.

## Levels

| # | Folder | Topology Companion | What it demonstrates |
|---|--------|--------------------|---------------------|
| UC01 | [UC01-customer-faq-agent/](UC01-customer-faq-agent/) | [L01 Hello LLM](../deployment/01-hello-llm/) | Single FAQ agent — the simplest useful deployment |
| UC02 | [UC02-audited-support/](UC02-audited-support/) | [L02 Accountability](../deployment/02-accountability/) | Support agent with full audit trail |
| UC03 | [UC03-escalation-flow/](UC03-escalation-flow/) | [L03 Case Management](../deployment/03-case-management/) | Chat becomes case — handoff from agent to human |
| UC04 | [UC04-multi-specialist-desk/](UC04-multi-specialist-desk/) | [L04 Fleet Scaling](../deployment/04-llm-fleet/) | Multiple specialist agents, each with distinct expertise |
| UC05 | [UC05-policy-rollout/](UC05-policy-rollout/) | [L05 Config Drift](../deployment/05-config-drift/) | Roll out policy changes to 50 agents — selective reconciliation |
| UC06 | [UC06-maintenance-window/](UC06-maintenance-window/) | [L06 Lifecycle](../deployment/06-lifecycle/) | Scheduled maintenance — suspend, update, resume |
| UC07 | [UC07-market-expansion/](UC07-market-expansion/) | [L07 Mutations](../deployment/07-topology-mutations/) | Add language support — grow the fleet declaratively |
| UC08 | [UC08-sla-monitoring/](UC08-sla-monitoring/) | [L08 Observability](../deployment/08-fleet-observability/) | SLA health probes — detect degradation before impact |

## Alignment with topology track

```
Topology (mechanism)              Use Case (application)
─────────────────────────────────────────────────────────
L01 Hello LLM               ←→   UC01 Customer FAQ agent
L02 Accountability           ←→   UC02 Audited support
L03 Case Management          ←→   UC03 Escalation flow
L04 Fleet Scaling            ←→   UC04 Multi-specialist desk
L05 Config Drift             ←→   UC05 Policy rollout
L06 Lifecycle                ←→   UC06 Maintenance window
L07 Mutations                ←→   UC07 Market expansion
L08 Observability            ←→   UC08 SLA monitoring
```

The topology track teaches **how** the platform works. The use case track
shows **why** you'd use it. Same YAML schema, different narrative.

## Path conventions

```
topology-tests/src/test/resources/topologies/
  deployment/                    # Topology track — mechanism
    01-hello-llm/
      01-hello-llm.yaml          # Topology YAML
      README.md                  # Walkthrough
    ...
  use-cases/                     # Use case track — application
    UC01-customer-faq-agent/
      UC01-customer-faq-agent.yaml   # Use case YAML
      README.md                      # Scenario narrative
    ...

topology-tests/src/test/java/.../topology/
  compilation/                   # Topology Java test companions
  deployment/                    # Deployment integration tests
  reconciliation/                # Reconciliation tests
  integration/                   # Live integration tests
```

Java tests live in the topology track only. Use case YAMLs reuse the
same compilation infrastructure — no separate Java test classes needed.

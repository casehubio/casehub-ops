# UC04: Multi-Specialist Desk

**Topology companion:** [L04 Fleet Scaling](../../deployment/04-llm-fleet/)
**Prerequisites:** UC03

## Scenario

A growing customer support operation needs specialist agents — not a single
generalist that handles everything poorly, but dedicated specialists who each
excel at their domain. Billing queries go to an agent that understands payment
systems, invoicing rules, and refund policies. Technical issues go to an agent
trained on the product's architecture, troubleshooting guides, and known issues.
Returns go to an agent that knows the returns workflow, shipping logistics, and
exception policies.

Each specialist has its own greeting, escalation threshold, and knowledge base.
The billing specialist resolves most queries directly (high threshold) because
billing answers are usually definitive. The technical specialist escalates more
frequently (medium threshold) because some issues need engineering. The returns
specialist escalates most often (low threshold) because refund approvals often
require supervisor sign-off.

The fleet scales by adding specialties — not by cloning a generic agent. When
the business adds a "warranty claims" specialty next quarter, the topology
change is one CSV row.

## The Fleet

| Specialist | Greeting | Escalation | Knowledge Base |
|-----------|----------|------------|----------------|
| Billing | "How can I help with your billing?" | High | billing-kb |
| Technical | "What technical issue are you experiencing?" | Medium | tech-kb |
| Returns | "I can help process your return." | Low | returns-kb |

## How It Works

1. Infrastructure services deploy first — manifest, eidos, engine — in
   dependency order (same as every topology level)
2. The `support-agent` node uses **forEach expansion** over the `support-team`
   data block to create one instance per specialist
   ([see L04 for the mechanism](../../deployment/04-llm-fleet/))
3. Each instance gets a unique `applicationId` (`support-billing`,
   `support-technical`, `support-returns`) and domain-specific config
   resolved from its data row via `${each.agent.*}` variables
4. All three agents depend on `engine` — they deploy in parallel after
   infrastructure is ready
5. The result: 6 nodes total (3 infrastructure + 3 support agents), each
   agent configured for its specialty

## What To Look For

1. **6 total nodes** — 3 infrastructure (manifest, eidos, engine) + 3 support agents
2. **Distinct applicationIds** — `support-billing`, `support-technical`, `support-returns`
3. **Per-agent config** — billing has escalation "high", technical has "medium", returns has "low"
4. **Greeting differentiation** — each agent greets customers with domain-appropriate language
5. **Knowledge base routing** — each agent points to its own KB (billing-kb, tech-kb, returns-kb)

## Why This Matters

Real support operations don't run one agent that does everything. They run
specialists — and those specialists need different configurations, different
knowledge bases, and different escalation rules. The alternative is maintaining
three separate topology definitions that drift apart over time.

forEach expansion turns this into a data problem. The topology defines the
shape once; the data block defines the variants. Adding a fourth specialty
is a CSV row, not a copy-paste of infrastructure config. This is why fleet
scaling matters — not for running N identical copies, but for running N
differentiated instances from a single declaration.

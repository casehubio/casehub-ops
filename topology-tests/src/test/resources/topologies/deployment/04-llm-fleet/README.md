# Level 04: LLM Fleet

**Methodology step:** DEPLOY — Fleet Scaling
**Prerequisites:** Level 01
**Use case companion:** [UC04 Multi-Specialist Desk](../../use-cases/UC04-multi-specialist-desk/)

## When to use this pattern

You need multiple LLM instances with differentiated roles — an analyst, a coder, and an assistant. Each has its own identity, archetype, capability, and domain vocabulary, all from a single topology definition.

## Walkthrough

1. Deploy infrastructure services (manifest, eidos, engine) — same as Level 01
2. The `my-llm` node uses `forEach` expansion over a CSV data block defining three fleet members: analyst, coder, assistant
3. Each instance gets unique config via `${each.llm.*}` variable resolution — role, capability, archetype, adjective, domain
4. Observe 6 total nodes deployed: 3 infrastructure + 3 fleet instances
5. Each fleet instance has a distinct applicationId: my-llm-analyst, my-llm-coder, my-llm-assistant
6. Exercise each LLM endpoint — each responds with role-appropriate behavior reflecting its eidos-generated identity

## Verify after deployment

1. All 6 nodes deployed (3 infrastructure + 3 fleet instances)
2. Each fleet instance has a distinct applicationId (my-llm-analyst, my-llm-coder, my-llm-assistant)
3. Each instance's config reflects its CSV row (analyst has capability "deep", coder has "fast", assistant has "balanced")
4. Each LLM responds with behavior appropriate to its role and domain

## Common failure modes

- forEach not expanding → check the `data` block format; CSV header must match field references
- All instances share the same config → verify `${each.llm.*}` variable resolution in the spec block
- Wrong instance count → check CSV row count in the data block (header row is not counted)
- LLM identity not differentiated → verify eidos descriptors.yaml uses `${config.*}` placeholders

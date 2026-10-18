# UC07: Market Expansion

**Topology companion:** [L07 Mutations](../../deployment/07-topology-mutations/)
**Prerequisites:** UC06

## Scenario

Expand into a new market — add French and German language agents to the fleet.
The topology grows declaratively: add nodes to the YAML, the platform provisions
them in dependency order while the existing English agents keep running. Prune
before grow ensures clean transitions.

## What the topology level teaches

L07 shows **how** declarative mutations (add/remove nodes) work with dependency
ordering. This use case shows **why** — market expansion is additive, not
disruptive.

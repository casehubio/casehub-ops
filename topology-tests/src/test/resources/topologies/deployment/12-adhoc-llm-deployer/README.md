# Level 12: Ad-hoc LLM Deployer

An LLM node monitors RAS situations and autonomously deploys specialist
LLM instances via topology mutation.

## Prerequisites

- Level 08: Fleet Observability (RAS monitoring)
- Level 10: Pool Deployment (managed instance groups)

## What this level demonstrates

- `SituationRecompiler` SPI for runtime graph mutation
- Data-driven specialist catalog (inline CSV data block)
- Deploy on TRIGGERED / remove on RESOLVED lifecycle
- Circuit breaker for specialist health failures

## Topology

| Node | Type | Role |
|------|------|------|
| manifest | claudony_app | Manifest service |
| eidos | claudony_app | Identity service |
| engine | claudony_app | Engine service |
| deployer | claudony_app | Autonomous deployer LLM |

Specialist nodes (`claudony_app`) are added/removed dynamically at runtime
by the `AdhocLlmDeployerRecompiler` based on RAS situations.

## Tests

**Compilation** (offline):
```bash
mvn test -pl topology-tests -Dtest="DeploymentCompilationTest#adhocLlmDeployer*"
```

**Unit** (offline):
```bash
mvn test -pl topology-tests -Dtest="AdhocLlmDeployerRecompilerTest"
```

**Integration** (requires Docker):
```bash
mvn test -pl topology-tests -Pintegration-live -Dtest="AdhocLlmDeployerIntegrationTest"
```

# casehub-ops — Consumer Guide

> Domain implementations of casehub-desiredstate SPIs for CaseHub deployment concerns.

**GitHub:** [casehubio/casehub-ops](https://github.com/casehubio/casehub-ops)
**Tier:** Integration (Research project + reference architecture)

---

## Purpose

Domain implementations of `casehub-desiredstate` SPIs for CaseHub-specific deployment concerns. Bridges the generic desired-state runtime (`casehub-desiredstate`) to concrete CaseHub operational goals — agent deployment, stream topology, channel configuration, IoT desired state, compliance posture, and infrastructure provisioning.

`casehub-desiredstate` stays domain-agnostic; `casehub-ops` is the CaseHub domain layer above it.

---

## Module Structure

| Module | Purpose |
|--------|---------|
| `api` | SPIs for CaseHub-specific desired state: `GoalCompiler`, `NodeProvisioner`, `ActualStateAdapter`, `FaultPolicy`, `EventSource` implementations keyed to CaseHub domain concepts |
| `infra` | Terraform/Ansible augmentation PoC — `InfraNodeSpec` sealed hierarchy, `InfraBackend` SPI, `StandaloneBackend`, `InfraGoalCompiler`, `InMemoryResourceProvisioner`. Three operating modes: standalone, Terraform augmentation, Ansible augmentation |
| `deployment` | `DeploymentGoalCompiler` — processes `casehub-deployment.yaml` goal declaration into a `DesiredStateGraph`; sub-compilers for agents, streams, channels, detection, trust |
| `compliance` | SOC2/GDPR/EU-AI-Act/DORA posture compliance desired-state — includes `EvidenceCollector` SPI and 4 implementations (FileExistence, CertificateExpiry, ConfigHash, LogDirectory) |
| `iot` | IoT desired state: `IoTGoalCompiler`, `IoTActualStateAdapter`, `IoTNodeProvisioner`, `CapabilityNormalizer`, `CapabilityCommandMapper`, `IoTGoalLoader`, `IoTEventSource`, `IoTApprovalEvaluator`, `IoTFaultPolicy` (coordinates with `casehub-iot`) |
| `app` | Ops console — service lifecycle management for K8s microservices; embeds casehub-engine and casehub-desiredstate |
| `testing` | Shared test fixtures — dependency aggregator POM for `casehub-ops-api`, `casehub-desiredstate-testing`, `casehub-platform-testing`. No Java sources. |

---

## Compliance Module

**EvidenceCollector** SPI — strategy-based evidence collection for compliance posture verification. Four implementations:
- `FileExistenceEvidenceCollector` — verifies files exist at expected paths (e.g., `.env.example` present, `.env` absent)
- `CertificateExpiryEvidenceCollector` — checks TLS/SSL certificate expiry dates
- `ConfigHashEvidenceCollector` — verifies configuration file hashes match approved baselines
- `LogDirectoryEvidenceCollector` — validates log directory permissions and retention policies

---

## Dependencies

| Artifact | Module | Nature |
|---|---|---|
| `casehub-desiredstate-api` | `api`, `infra` | `GoalCompiler`, `NodeProvisioner`, `ActualStateAdapter`, `FaultPolicy`, `EventSource` SPIs |
| `casehub-desiredstate` (runtime) | `infra` | `DefaultDesiredStateGraphFactory` (test scope) |
| `casehub-platform-api` | `api` | `Path`, `Preferences`, `CurrentPrincipal` |
| `casehub-work-api` | `infra` | `WorkItem` generation for human nodes |

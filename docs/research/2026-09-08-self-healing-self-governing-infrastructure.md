# Self-Healing, Self-Governing Infrastructure: CaseHub as an Autonomic Operations Platform

## 1. Executive Summary

CaseHub is uniquely positioned to deliver self-healing, self-governing infrastructure because it already integrates the components that industry leaders build as separate, bolt-on products: event detection (RAS), case-based reasoning for learning from outcomes (CBR), case management for incident workflows (Engine), trust-weighted execution for graduated autonomy, human-in-the-loop gating, tamper-evident audit (Ledger), and a continuous desired-state reconciliation loop. No existing platform combines these capabilities in a single, closed-loop architecture. This research maps CaseHub's existing stack to the academic and industry frameworks for autonomic computing, identifies capability gaps, and proposes a phased delivery plan to realise the full self-healing, self-governing vision.

## 2. State of the Art

### 2.1 Autonomic Computing and MAPE-K

IBM's MAPE-K (Monitor, Analyze, Plan, Execute, Knowledge) reference model, introduced by Kephart and Chess (2003), remains the foundational architecture for self-adaptive systems [1]. The model defines four self-* properties: self-configuration, self-healing, self-optimization, and self-protection. Contemporary extensions (2024–2026) apply MAPE-K to cloud microservices, enterprise AI agents, robotics, and human-machine teaming scenarios [2]. Hierarchical and decentralised variants address scalability, while the FOCALE architecture adds dynamically updateable knowledge bases with machine learning-driven reasoning [3].

A critical insight from MAPE-K research is that real systems require multiple layered control loops — autonomic meta-controllers managing other autonomic controllers — not a single monolithic loop [2].

### 2.2 AIOps and Event Intelligence

Gartner retired the "AIOps Platforms" market category in 2025, replacing it with "Event Intelligence Solutions" (EIS), citing vendor overuse of the term and resulting market confusion [4]. The AIOps market is estimated at $25 billion (2026), growing at 30% annually, with projections of $36.6 billion by 2030 (IDC) [5].

Seventy-three percent of enterprises plan to adopt AIOps self-healing infrastructure by end of 2026 (Gartner survey of 500+ IT leaders) [5]. Teams using these capabilities report 40–70% MTTR reductions [6]. However, Forrester's AIOps Wave Q1 2026 found that 28% of AIOps projects collapse due to data silos, and the MIT Technology Review (February 2026) identified data quality as the primary bottleneck for self-healing systems [7].

The industry is shifting from AIOps 1.0 (pattern recognition, alert grouping, noise reduction) to "Agentic SRE" — intelligent agents that analyse system state, execute remediations, and verify results [8]. Key capabilities include anomaly detection (85% accuracy with ML models, rising to 90%+ with causal inference per Stanford's NeurIPS 2025 proceedings), RAG-based reasoning over runbooks and incident history, and progressive automation tiers from alert correlation through autonomous remediation with human approval [6][9].

The alert fatigue problem is severe: DevOps teams receive over 2,000 alerts per week, with only 3% requiring action; SOC teams field 4,484 alerts daily with 67% ignored [10].

### 2.3 LLM-Assisted Incident Resolution

LLM-driven root cause analysis is an active academic and industry frontier. Key recent work includes:

- **FoundRoot** (ICSE 2026): Foundation models for RCA via structured deep thinking [11]
- **RCAFlow** (AAAI 2026): Workflow-informed hierarchical planning multi-agent systems for RCA [12]
- **OpsAgent** (ASE Industry 2026): Evolving multi-agent systems for microservices incident management [13]
- **Microsoft Research**: Evaluation of 40,000+ incidents across 1,000+ services found fine-tuned GPT-3.5 models significantly outperformed other approaches, with 70%+ of on-call engineers rating recommendations useful [14]
- **ACM Survey on LLM4AIOps**: Analysis of 183 research articles (2020–2024) on LLM applications in AIOps [15]

The AI Investigation Capability Ladder defines six tiers from L0 (manual) through L5 (closed-loop investigate + remediate with human approval). Most teams remain at L0–L1; 78.2% of survey respondents do not use AI in CI/CD workflows (JetBrains AI Pulse, April 2026) [6].

RAG-based reasoning is the critical architectural pattern — LLMs grounded in specific infrastructure data (logs, runbooks, topology graphs) rather than general knowledge. This maps directly to CaseHub's Garden/Protocols knowledge base.

### 2.4 Case-Based Reasoning in Operations

CBR formalises the operational instinct of "have we seen something like this before?" — storing past resolutions as structured cases and reusing them for new incidents [16]. Research on CBR for fault resolution in communication networks demonstrates advantages over rule-based reasoning (RBR): ability to learn from experience, handle novel problems, and update in rapidly changing domains [17]. The DisCaRia distributed CBR system combines P2P technology with conventional CBR for distributed fault resolution [17].

Automated case creation methodologies reduce human effort by building cases from operational databases using NLP techniques, enabling high-quality case libraries from historical maintenance data [18]. The CBR learning loop — resolve, store, retrieve, adapt — provides continuous improvement without explicit reprogramming.

CBR's transparency is a key advantage for operations: when recommending a resolution, it points to the specific past case that informed the recommendation, giving operators a concrete reference point for evaluation [16].

### 2.5 Policy-as-Code and Continuous Compliance

The policy-as-code landscape in 2026 centres on OPA/Gatekeeper (Rego-based, CNCF-graduated, multi-domain) and Kyverno (YAML-native, Kubernetes-focused, CNCF-graduated) [19][20]. The cost of non-compliance averages $14.82 million versus $5.47 million for compliance [19].

Integration with GitOps (ArgoCD, Flux) enables fully declarative policy enforcement where cluster state is a function of Git state, and audit questions are answered with `git log` rather than screenshots [20]. Kyverno's PolicyReport CRDs generate audit-grade compliance evidence automatically [20].

Current limitations: policy evaluation is admission-time only (prevent bad state) with limited continuous reconciliation (detect and remediate drift). Policy engines enforce rules but do not learn from violations or adapt policies based on outcomes.

### 2.6 CVE and Vulnerability Management

48,244 CVE records were published in 2025 (up from 40,077 in 2024), making manual management impossible [21]. Vulnerability exploitation now accounts for 20% of all breaches, a 34% increase year-over-year (Verizon 2025 DBIR) [22]. 60% of breach victims were breached via unpatched known vulnerabilities (Ponemon/ServiceNow) [22].

The industry is shifting from "scan and patch" to continuous exposure management with risk-based prioritisation using CVSS v4 + EPSS + KEV signals [21]. The primary gap is the handoff between security (finding) and IT (fixing) — the remediation bottleneck [23]. Current tools (Snyk, Trivy, Grype, Dependabot) excel at detection but lack orchestration of the full lifecycle: assess → prioritise → patch → canary → verify → rollback if broken.

### 2.7 Graduated Autonomy and Trust-Based Execution

The Cloud Security Alliance published an Autonomy Levels Framework (2026) defining six levels from fully supervised to fully autonomous operation, with a Capability-Control Matrix mapping agent capabilities to required controls [24]. Key principle: autonomy becomes measurable when agents must graduate across tiers based on demonstrated safety. Every operational action must have an undo path — rollbacks, kill switches, and safe defaults are mandatory [24].

The TRUST 2026 workshop (co-located with ASE 2026) emphasises engineering autonomous systems as first-class software systems with explicit consideration of ethical, societal, and quality concerns [25].

As of 2025, only approximately one-quarter of organisations had comprehensive AI governance programs, with oversight and expertise gaps as significant barriers [24]. The pattern emerging in 2026: governed autonomy — systems that self-regulate, learn boundaries, and preserve flexibility while staying safe [26].

## 3. CaseHub's Existing Capabilities Mapped to MAPE-K

CaseHub's architecture maps naturally to an extended MAPE-K model with additional learning and governance dimensions:

| MAPE-K Phase | CaseHub Component | Capability |
|---|---|---|
| **Monitor** | RAS (Ganglia, EventSource) | Continuous event stream from reconciliation loop; situation detection with chain modes (and, or, threshold, sequence, count, streak, rate) |
| **Monitor** | ActualStateAdapter | Read current state of managed resources; drift detection via compare-state |
| **Analyze** | RAS (SituationDefinition) | Event correlation, pattern recognition, anomaly classification |
| **Analyze** | Blocks/Summarisation | Aggregate raw L1 events into L2/L3 phases (CloudEvent bridge, YAML pipeline surface) |
| **Plan** | CBR (CbrFaultPolicy) | Retrieve past cases matching current situation; select resolution strategy based on historical outcomes |
| **Plan** | TransitionPlanner | Compute minimum set of transitions to reach desired state from actual state |
| **Execute** | NodeProvisioner | Create/update/delete resources; dispatch via handledTypes() |
| **Execute** | HumanNodeHandler | Route to human when trust threshold not met; WorkItem creation |
| **Knowledge** | Garden/Protocols | Cross-project technical knowledge base; project-specific standing rules |
| **Knowledge** | Ledger | Tamper-evident audit trail of all decisions and actions |
| **Knowledge** | CBR (CbrProposalTracker) | Outcome recording; case library growth |
| **Governance** | Trust policies | Trust-weighted execution; threshold-based gating; bootstrap escalation |
| **Governance** | TypedFaultPolicy | Type-aware fault handling (e.g., adding review nodes of specific types) |
| **Governance** | ApprovalEvaluator | Per-domain approval workflows |

CaseHub extends MAPE-K with two dimensions absent from the classical model: **Learning** (CBR outcome tracking creates a continuously improving case library) and **Governance** (trust-weighted execution provides graduated autonomy without binary on/off automation).

## 4. Gap Analysis

### Gap 1: Observability Ingestion Layer

**Current state:** RAS processes events from the desired-state reconciliation loop (internal events). No integration with external observability stacks (Prometheus metrics, OpenTelemetry traces, Kubernetes events, cloud provider health APIs).

**What would fill it:** An ObservabilityAdapter SPI that ingests metrics, logs, and traces from standard observability pipelines (OpenTelemetry, Prometheus, Fluentd) and feeds them as events into RAS ganglia.

**Component affected:** RAS, extends EventSource SPI.

### Gap 2: Pre-emptive Detection (Predictive Analytics)

**Current state:** RAS detects situations reactively — events must occur before detection triggers. No trending, forecasting, or anomaly prediction on time-series data.

**What would fill it:** A PredictiveGanglion that applies statistical models (z-score, isolation forests) and ML forecasting to metric streams, generating pre-emptive situation events before thresholds are breached (e.g., "memory trending to OOM within 2 hours").

**Component affected:** RAS, new ganglion type.

### Gap 3: LLM-Assisted Diagnosis for Novel Incidents

**Current state:** CBR retrieves past cases for known situations. Novel incidents (no matching case) fall through to human escalation via HumanNodeHandler. No automated diagnosis capability for unprecedented failures.

**What would fill it:** A DiagnosticAgent that uses LLM with RAG over Garden entries, Protocols, runbooks, and incident history to diagnose novel situations. Proposes resolution strategies that, if successful, become new CBR cases automatically.

**Component affected:** Neocortex (LLM integration), Garden (knowledge retrieval), CBR (case creation). The Garden and Protocols infrastructure already provides the RAG corpus; the gap is the reasoning agent that consumes it.

### Gap 4: Vulnerability and Update Lifecycle Orchestration

**Current state:** No built-in CVE scanning, dependency update tracking, or patch lifecycle management.

**What would fill it:** A VulnerabilityAdapter that integrates with scanning tools (Trivy, Grype, Snyk) and feeds findings as desired-state deviations — a known CVE is drift from the "no known vulnerabilities" desired state. The reconciliation loop then drives assessment → prioritisation (CVSS + EPSS + KEV) → patch → canary → verify → rollback, using existing FaultPolicy for rollback and HumanNodeHandler for approval gates.

**Component affected:** Desired-state runtime (new NodeSpec types for vulnerability posture), ops module (VulnerabilityNodeProvisioner).

### Gap 5: Continuous Compliance Reconciliation

**Current state:** The compliance ops module exists but is scaffolded. No integration with policy engines (OPA, Kyverno) or continuous compliance evidence generation.

**What would fill it:** ComplianceActualStateAdapter that reads compliance posture from policy engine reports (Kyverno PolicyReports, OPA audit logs). ComplianceNodeProvisioner that can apply remediation (mutation policies, configuration corrections). Continuous reconciliation ensures compliance drift is detected and remediated, not just blocked at admission time.

**Component affected:** Compliance ops module. The desired-state reconciliation loop is the key differentiator over current policy-as-code tools which only prevent bad state at admission time.

### Gap 6: Knowledge Feedback Loop Automation

**Current state:** Garden entries are created manually during development sessions (forage CAPTURE/SWEEP). CBR cases are recorded via CbrProposalTracker. These two knowledge stores are not connected — a CBR resolution that works repeatedly does not automatically become a Garden entry or Protocol.

**What would fill it:** A KnowledgePromoter that monitors CBR outcome data: when a resolution strategy succeeds consistently (e.g., 5+ times with >80% success rate), it proposes promotion to a Garden entry (cross-project) or Protocol (project-specific). Human approval required before promotion, but the system surfaces the candidates automatically.

**Component affected:** CBR, Garden, Protocols. Bridges the learning loop to the knowledge base.

### Gap 7: Multi-Resource Blast Radius Analysis

**Current state:** FaultPolicy operates on individual nodes. No cross-node impact analysis — when a fault occurs, the system doesn't reason about which other nodes are affected by the same root cause.

**What would fill it:** A BlastRadiusAnalyzer that uses the DesiredStateGraph's dependency edges and the ActualState to compute the set of nodes affected by a root-cause event. Feeds into TransitionPlanner for coordinated remediation rather than per-node independent responses.

**Component affected:** Desired-state runtime (TransitionPlanner), FaultPolicy.

## 5. Proposed Architecture

The full self-healing architecture extends CaseHub's existing desired-state reconciliation loop into a five-stage closed loop:

```
┌─────────────────────────────────────────────────────────────────┐
│                    DETECT                                       │
│  ObservabilityAdapter ──→ RAS Ganglia ──→ Situation Detection  │
│  (metrics, logs, traces)  (reactive + predictive)              │
│  ActualStateAdapter ──→ Drift Detection                        │
│  VulnerabilityAdapter ──→ CVE/Posture Detection                │
├─────────────────────────────────────────────────────────────────┤
│                    DIAGNOSE                                     │
│  Blocks/Summarisation ──→ L2/L3 Phase Classification           │
│  CBR Retrieval ──→ Known Resolution Match?                     │
│  ├─ YES ──→ Ranked resolutions with outcome history            │
│  └─ NO  ──→ DiagnosticAgent (LLM + RAG over Garden/Protocols) │
│             ──→ Proposed resolution                            │
├─────────────────────────────────────────────────────────────────┤
│                    RESOLVE                                      │
│  Trust Evaluation ──→ Trust threshold met?                     │
│  ├─ YES ──→ NodeProvisioner (auto-execute)                     │
│  └─ NO  ──→ HumanNodeHandler (WorkItem for approval)          │
│  BlastRadiusAnalyzer ──→ Coordinated remediation plan          │
│  TransitionPlanner ──→ Ordered execution with lifecycle phases │
│  FaultPolicy ──→ Rollback if remediation fails                 │
├─────────────────────────────────────────────────────────────────┤
│                    LEARN                                        │
│  CbrProposalTracker ──→ Record outcome (success/failure)       │
│  CBR Case Library ──→ Future retrievals improve                │
│  KnowledgePromoter ──→ Consistent successes → Garden/Protocol  │
├─────────────────────────────────────────────────────────────────┤
│                    HARDEN                                       │
│  Garden ──→ Cross-project knowledge base grows                 │
│  Protocols ──→ Project-specific rules codified                 │
│  Ledger ──→ Tamper-evident audit trail                         │
│  Trust Scores ──→ Proven remediations earn higher trust        │
└─────────────────────────────────────────────────────────────────┘
```

Each stage maps to existing CaseHub components, with the gaps identified in Section 4 as the required extensions. The architecture is inherently hierarchical — multiple MAPE-K loops can operate at different granularities (per-node, per-service, per-region, per-cluster) with trust thresholds determining which loops may act autonomously.

## 6. Phased Delivery

### Phase 1: Detection + Basic Auto-Remediation (Foundation)

**Goal:** Prove the detect→resolve loop works for common infrastructure faults.

**Delivers:**
- ObservabilityAdapter SPI + Kubernetes metrics/events ingestion
- RAS situation definitions for common K8s faults (CrashLoopBackOff, OOMKilled, node NotReady, pod eviction)
- YamlFaultPolicy tiers for standard responses (restart, reschedule, scale-down)
- Integration with existing desired-state reconciliation loop
- Ledger audit trail for all automated actions

**CaseHub components exercised:** RAS, FaultPolicy, NodeProvisioner, Ledger, desired-state runtime.

**Measure:** Automated resolution of tier-1 K8s faults without human intervention; MTTR for covered faults.

### Phase 2: CBR-Driven Resolution + Learning

**Goal:** The system learns from outcomes and improves resolution accuracy over time.

**Delivers:**
- CbrFaultPolicy wired to infrastructure domain (not just deployment domain)
- Outcome tracking for every automated resolution
- Resolution ranking based on historical success rates
- CBR case library growth from operational data
- Dashboard showing CBR learning curve (resolution accuracy over time)

**CaseHub components exercised:** CBR (ConfigurationRetriever, PreferenceProvider, CbrProposalTracker), RAS, Trust policies.

**Measure:** Resolution accuracy improvement over baseline; reduction in human escalations for known fault types.

### Phase 3: LLM-Assisted Diagnosis for Novel Incidents

**Goal:** Novel incidents (no CBR match) get automated diagnosis before human escalation.

**Delivers:**
- DiagnosticAgent using LLM + RAG over Garden entries, Protocols, and incident history
- Automatic case creation from successfully resolved novel incidents
- KnowledgePromoter for consistent resolutions → Garden/Protocol promotion
- Integration with MCP tools for live system inspection during diagnosis

**CaseHub components exercised:** Neocortex, Garden, Protocols, CBR, HumanNodeHandler.

**Measure:** Percentage of novel incidents where DiagnosticAgent proposes a successful resolution; time from novel incident to first CBR-resolvable recurrence.

### Phase 4: Self-Governing (CVE, Compliance, Updates)

**Goal:** Continuous compliance posture and automated vulnerability lifecycle.

**Delivers:**
- VulnerabilityAdapter integrating Trivy/Grype scan results as desired-state deviations
- Risk-based prioritisation (CVSS + EPSS + KEV) driving remediation SLAs
- Patch → canary → verify → rollback lifecycle via existing FaultPolicy
- ComplianceActualStateAdapter reading Kyverno PolicyReports / OPA audit logs
- Compliance drift as a continuous reconciliation target, not just admission-time enforcement
- Automated compliance evidence generation for SOC2, GDPR, DORA, NIS2

**CaseHub components exercised:** Compliance ops module, desired-state runtime, FaultPolicy, HumanNodeHandler, Ledger.

**Measure:** Time from CVE disclosure to remediation; continuous compliance score; audit evidence generation time.

### Phase 5: Full Autonomic Loop with Trust-Gated Execution

**Goal:** Production-grade graduated autonomy — the system earns increasing independence.

**Delivers:**
- PredictiveGanglion for pre-emptive detection (trending, forecasting)
- BlastRadiusAnalyzer for coordinated multi-node remediation
- Graduated trust tiers per resolution type (proven auto-executes, novel requires approval)
- Trust score decay (unused resolutions lose trust over time, requiring re-verification)
- Multi-loop hierarchical architecture (per-node, per-service, per-region)
- Full closed-loop: detect → diagnose → resolve → learn → harden, operating continuously

**CaseHub components exercised:** All components at full integration.

**Measure:** Percentage of incidents resolved without human intervention; trust score distribution across resolution types; time from first detection to autonomous resolution capability for new fault classes.

## 7. CaseHub Capability Integration Matrix

| Self-Healing Function | CaseHub Component | Current Status | Gap Phase |
|---|---|---|---|
| Event ingestion | RAS EventSource | Internal events only | Phase 1 (ObservabilityAdapter) |
| Reactive detection | RAS Ganglia + ChainModes | Production-ready | — |
| Pre-emptive detection | RAS (PredictiveGanglion) | Not implemented | Phase 5 |
| Event summarisation | Blocks/Summarisation | Production-ready | — |
| Known-issue diagnosis | CBR (CbrFaultPolicy) | Production-ready (deployment domain) | Phase 2 (infra domain) |
| Novel-issue diagnosis | Neocortex + Garden RAG | Components exist, not wired | Phase 3 |
| Resolution execution | NodeProvisioner | Production-ready | — |
| Human escalation | HumanNodeHandler + WorkItems | Production-ready | — |
| Trust-gated execution | Trust policies | Production-ready | Phase 5 (graduated tiers) |
| Approval workflows | ApprovalEvaluator | Production-ready | — |
| Outcome recording | CbrProposalTracker | Production-ready | — |
| Knowledge promotion | Garden/Protocols | Manual only | Phase 3 (KnowledgePromoter) |
| Audit trail | Ledger | Production-ready | — |
| CVE lifecycle | Not implemented | — | Phase 4 |
| Continuous compliance | Compliance module (scaffolded) | Partial | Phase 4 |
| Blast radius analysis | Not implemented | — | Phase 5 |
| Desired-state reconciliation | Reconciliation loop | Production-ready | — |
| YAML topology declaration | YAML frontend | Production-ready | — |

**Key finding:** 10 of 17 self-healing functions have production-ready CaseHub components. The remaining 7 are extensions of existing components, not new architectural concepts. The framework is architecturally complete — the gaps are domain-specific integrations (observability, CVE, compliance) and intelligence extensions (predictive detection, LLM diagnosis, knowledge promotion).

## 8. References

[1] Kephart, J.O. and Chess, D.M. "The Vision of Autonomic Computing." *IEEE Computer*, 36(1), 2003.

[2] "MAPE-K Loop Architecture." Emergent Mind, 2025. https://www.emergentmind.com/topics/mape-k-loop

[3] "Autonomic Computing." Wikipedia. https://en.wikipedia.org/wiki/Autonomic_computing

[4] Gartner. "Event Intelligence Solutions market category redefinition." 2025. https://www.augmentcode.com/guides/what-is-aiops

[5] "AIOps Self-Healing Infrastructure 2026." NeuralWired, March 2026. https://neuralwired.com/2026/03/31/aiops-self-healing-infrastructure-2026/

[6] "AI SRE explained: what it is, how it works." incident.io, 2026. https://incident.io/blog/what-is-ai-sre-complete-guide-2026

[7] "From AIOps Hype to Reality: Building Self-Healing Infrastructure in 2026." Techstrong IT, 2026. https://techstrong.it/features/from-aiops-hype-to-reality-building-self-healing-infrastructure-in-2026/

[8] "Agentic SRE: How Self-Healing Infrastructure Is Redefining Enterprise AIOps in 2026." Unite.AI, 2026. https://www.unite.ai/agentic-sre-how-self-healing-infrastructure-is-redefining-enterprise-aiops-in-2026/

[9] "A Survey of AIOps in the Era of Large Language Models." *ACM Computing Surveys*, 2025. https://dl.acm.org/doi/full/10.1145/3746635

[10] Grafana Labs. "Alert Fatigue Research." 2026. Referenced in https://byteiota.com/aiops-self-healing-60-enterprises-adopt-in-2026/

[11] "FoundRoot: Towards Foundation Model for Root Cause Analysis via Structured Deep Thinking." *ICSE 2026*.

[12] "RCAFlow: A Workflow-Informed Hierarchical Planning Multi-Agent System for Root Cause Analysis." *AAAI 2026*.

[13] "OpsAgent: An Evolving Multi-agent System for Incident Management in Microservices." *ASE Industry 2026*.

[14] "Automatic Root Cause Analysis via Large Language Models for Cloud Incidents." Microsoft Research. https://arxiv.org/pdf/2305.15778

[15] "awesome-LLM-AIOps: LLM and AIOps research collection." GitHub. https://github.com/Jun-jie-Huang/awesome-LLM-AIOps

[16] "What Is Case-Based Reasoning?" Teachfloor, 2026. https://www.teachfloor.com/blog/case-based-reasoning

[17] Tran et al. "Fault Resolution in Case-Based Reasoning." *Springer*, 2008. https://link.springer.com/chapter/10.1007/978-3-540-89197-0_39

[18] "Automated case creation and management for diagnostic CBR systems." *Applied Intelligence*, Springer. https://link.springer.com/content/pdf/10.1007/s10489-007-0039-1.pdf

[19] Anderson, P. "Policy as Code in GitOps: Implementing Compliance Automation with OPA and Kyverno." *ResearchGate*, April 2026. https://www.researchgate.net/publication/404400476

[20] "GitOps policy-as-code: Securing Kubernetes with Argo CD and Kyverno." CNCF Blog, April 2026. https://www.cncf.io/blog/2026/04/02/gitops-policy-as-code-securing-kubernetes-with-argo-cd-and-kyverno/

[21] "CVE & Vulnerability Management in 2026." isMalicious Blog. https://ismalicious.com/posts/cve-vulnerability-lifecycle-patch-management-2026

[22] Verizon. "2025 Data Breach Investigations Report." Referenced in https://defectdojo.com/blog/vulnerability-management-in-2026-moving-from-scan-patch-to-continuous-orchestration

[23] "Vulnerability Management in 2026: Moving from Scan & Patch to Continuous Orchestration." DefectDojo, 2026. https://defectdojo.com/blog/vulnerability-management-in-2026-moving-from-scan-patch-to-continuous-orchestration

[24] Cloud Security Alliance. "Agentic AI Autonomy Levels and Control Framework." 2026. https://labs.cloudsecurityalliance.org/research/agentic-ai-autonomy-levels-control-framework-v2-csa-styled/

[25] "TRUST 2026 Workshop." ASE 2026. https://conf.researchr.org/home/ase-2026/trust-2026

[26] "Governed Autonomy in 2026: How AI Systems Self-Regulate Without Losing Creativity." Logiciel, 2026. https://logiciel.io/blog/governed-autonomy-ai-self-regulation

[27] "Exploring LLM-based Agents for Root Cause Analysis." arXiv, 2024. https://arxiv.org/html/2403.04123v1

[28] "Root Cause Analysis Method Based on Large Language Models with Residual Connection Structures." arXiv, 2026. https://arxiv.org/html/2602.08804v1

[29] "Distilled Lifelong Self-Adaptation for Configurable Systems." arXiv, 2025. https://arxiv.org/pdf/2501.00840

[30] "Building adaptive knowledge bases for evolving continual learning models." *npj Artificial Intelligence*, 2025. https://www.nature.com/articles/s44387-025-00028-4

[31] "Autonomics: In search of a foundation for next-generation autonomous systems." *PNAS*, 2020. https://www.pnas.org/doi/10.1073/pnas.2003162117

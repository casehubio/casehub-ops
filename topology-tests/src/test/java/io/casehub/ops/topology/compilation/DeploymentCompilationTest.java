package io.casehub.ops.topology.compilation;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeploymentApplicationNodeSpec;
import io.casehub.ops.api.deployment.DeploymentNodeSpec;
import io.casehub.ops.api.infra.InfraNodeSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentCompilationTest extends TopologyTestBase {

    private static final Map<String, String> DEPLOYMENT_REGISTRY =
            buildTypeRegistryFrom(InfraNodeSpec.class, DeploymentNodeSpec.class);

    @Test
    void helloLlm_hasFourNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(4);
    }

    @Test
    void helloLlm_allNodesAreCasehubApplication() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node ->
                assertThat(node.type()).isEqualTo(NodeType.of("casehub_application")));
    }

    @Test
    void helloLlm_eidosDependsOnManifest() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("eidos")))
                .contains(NodeId.of("manifest"));
    }

    @Test
    void helloLlm_engineDependsOnEidos() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("engine")))
                .contains(NodeId.of("eidos"));
    }

    @Test
    void helloLlm_llmDependsOnEngine() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("my-llm")))
                .contains(NodeId.of("engine"));
    }

    @Test
    void helloLlm_specContainsApplicationId() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        var eidosNode = graph.nodes().get(NodeId.of("eidos"));
        assertThat(eidosNode).isNotNull();
        assertThat(eidosNode.spec()).isNotNull();
    }

    // ── Level 02: Accountability ──────────────────────────────────────────

    @Test
    void accountability_hasSixNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/02-accountability/02-accountability.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(6);
    }

    @Test
    void accountability_allNodesAreCasehubApplication() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/02-accountability/02-accountability.yaml", DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node ->
                assertThat(node.type()).isEqualTo(NodeType.of("casehub_application")));
    }

    @Test
    void accountability_eidosDependsOnManifest() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/02-accountability/02-accountability.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("eidos")))
                .contains(NodeId.of("manifest"));
    }

    @Test
    void accountability_ledgerDependsOnEidos() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/02-accountability/02-accountability.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("ledger")))
                .contains(NodeId.of("eidos"));
    }

    @Test
    void accountability_workDependsOnEngineAndLedger() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/02-accountability/02-accountability.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("work")))
                .containsExactlyInAnyOrder(NodeId.of("engine"), NodeId.of("ledger"));
    }

    @Test
    void accountability_llmDependsOnWork() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/02-accountability/02-accountability.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("my-llm")))
                .contains(NodeId.of("work"));
    }
// ── Level 03: Case Management ────────────────────────────────────────

    @Test
    void caseManagement_hasSevenNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/03-case-management/03-case-management.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(7);
    }

    @Test
    void caseManagement_allNodesAreCasehubApplication() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/03-case-management/03-case-management.yaml", DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node ->
                                               assertThat(node.type()).isEqualTo(NodeType.of("casehub_application")));
    }

    @Test
    void caseManagement_qhorusDependsOnWorkAndLedger() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/03-case-management/03-case-management.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("qhorus")))
                .containsExactlyInAnyOrder(NodeId.of("work"), NodeId.of("ledger"));
    }

    @Test
    void caseManagement_llmDependsOnQhorus() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/03-case-management/03-case-management.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("my-llm")))
                .contains(NodeId.of("qhorus"));
    }


// ── Level 04: Fleet Scaling ──────────────────────────────────────────

    @Test
    void llmFleet_hasSixNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(6);
    }

    @Test
    void llmFleet_allNodesAreCasehubApplication() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node ->
                                               assertThat(node.type()).isEqualTo(NodeType.of("casehub_application")));
    }

    @Test
    void llmFleet_threeExpandedLlmNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes().keySet().stream()
                        .map(NodeId::value)
                        .filter(id -> id.startsWith("my-llm."))
                        .toList())
                .containsExactlyInAnyOrder("my-llm.analyst", "my-llm.coder", "my-llm.assistant");
    }

    @Test
    void llmFleet_allLlmNodesDependOnEngine() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);
        for (String llmId : List.of("my-llm.analyst", "my-llm.coder", "my-llm.assistant")) {
            assertThat(graph.dependenciesOf(NodeId.of(llmId)))
                    .as("dependencies of %s", llmId)
                    .contains(NodeId.of("engine"));
        }
    }

    @Test
    void llmFleet_configValuesFlowPerInstance() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);

        var analyst = extractAppSpec(graph, "my-llm.analyst");
        assertThat(analyst.config()).containsEntry("llm.capability", "deep");
        assertThat(analyst.config()).containsEntry("llm.role", "analyst");

        var coder = extractAppSpec(graph, "my-llm.coder");
        assertThat(coder.config()).containsEntry("llm.capability", "fast");
        assertThat(coder.config()).containsEntry("llm.role", "developer");

        var assistant = extractAppSpec(graph, "my-llm.assistant");
        assertThat(assistant.config()).containsEntry("llm.capability", "balanced");
        assertThat(assistant.config()).containsEntry("llm.role", "assistant");
    }


// ── UC04: Multi-Specialist Desk ─────────────────────────────────────

    @Test
    void uc04MultiSpecialistDesk_hasSixNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/use-cases/UC04-multi-specialist-desk/UC04-multi-specialist-desk.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(6);
    }

    @Test
    void uc04MultiSpecialistDesk_threeExpandedSupportAgents() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/use-cases/UC04-multi-specialist-desk/UC04-multi-specialist-desk.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes().keySet().stream()
                        .map(NodeId::value)
                        .filter(id -> id.startsWith("support-agent."))
                        .toList())
                .containsExactlyInAnyOrder("support-agent.billing", "support-agent.technical", "support-agent.returns");
    }

    @Test
    void uc04MultiSpecialistDesk_allSupportAgentsDependOnEngine() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/use-cases/UC04-multi-specialist-desk/UC04-multi-specialist-desk.yaml", DEPLOYMENT_REGISTRY);
        for (String agentId : List.of("support-agent.billing", "support-agent.technical", "support-agent.returns")) {
            assertThat(graph.dependenciesOf(NodeId.of(agentId)))
                    .as("dependencies of %s", agentId)
                    .contains(NodeId.of("engine"));
        }
    }

    @Test
    void uc04MultiSpecialistDesk_configValuesFlowPerAgent() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/use-cases/UC04-multi-specialist-desk/UC04-multi-specialist-desk.yaml", DEPLOYMENT_REGISTRY);

        var billing = extractAppSpec(graph, "support-agent.billing");
        assertThat(billing.config()).containsEntry("agent.specialty", "Billing & Payments");
        assertThat(billing.config()).containsEntry("agent.escalation-threshold", "high");

        var technical = extractAppSpec(graph, "support-agent.technical");
        assertThat(technical.config()).containsEntry("agent.specialty", "Technical Support");
        assertThat(technical.config()).containsEntry("agent.escalation-threshold", "medium");

        var returns = extractAppSpec(graph, "support-agent.returns");
        assertThat(returns.config()).containsEntry("agent.specialty", "Returns & Refunds");
        assertThat(returns.config()).containsEntry("agent.escalation-threshold", "low");
    }


// ── UC05: Policy Rollout ────────────────────────────────────────────

    @Test
    void uc05PolicyRollout_hasSixNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/use-cases/UC05-policy-rollout/UC05-policy-rollout.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(6);
    }

    @Test
    void uc05PolicyRollout_billingEscalationChangedToMedium() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/use-cases/UC05-policy-rollout/UC05-policy-rollout.yaml", DEPLOYMENT_REGISTRY);

        var billing = extractAppSpec(graph, "support-agent.billing");
        assertThat(billing.config()).containsEntry("agent.escalation-threshold", "medium");
        assertThat(billing.config()).containsEntry("agent.greeting", "How can I help with your account?");
    }

    @Test
    void uc05PolicyRollout_technicalAndReturnsUnchanged() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/use-cases/UC05-policy-rollout/UC05-policy-rollout.yaml", DEPLOYMENT_REGISTRY);

        var technical = extractAppSpec(graph, "support-agent.technical");
        assertThat(technical.config()).containsEntry("agent.escalation-threshold", "medium");
        assertThat(technical.config()).containsEntry("agent.greeting", "What technical issue are you experiencing?");

        var returns = extractAppSpec(graph, "support-agent.returns");
        assertThat(returns.config()).containsEntry("agent.escalation-threshold", "low");
        assertThat(returns.config()).containsEntry("agent.greeting", "I can help process your return.");
    }

    private CaseHubApplicationSpec extractAppSpec(DesiredStateGraph graph, String nodeId) {
        var node = graph.nodes().get(NodeId.of(nodeId));
        assertThat(node).as("node %s", nodeId).isNotNull();
        if (node.spec() instanceof DeploymentApplicationNodeSpec dns) {
            return dns.spec();
        }
        return (CaseHubApplicationSpec) node.spec();
    }
}

package io.casehub.ops.topology.compilation;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyPoolNodeSpec;
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
    private static final Map<String, String> CLAUDONY_REGISTRY   =
            buildTypeRegistryFrom(ClaudonyNodeSpec.class);


    @Test
    void helloLlm_hasOneNode() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(1);
    }

    @Test
    void helloLlm_nodeIsCasehubApplicationWithUniversalImage() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/01-hello-llm/01-hello-llm.yaml", DEPLOYMENT_REGISTRY);
        var llmNode = graph.nodes().get(NodeId.of("my-llm"));
        assertThat(llmNode).isNotNull();
        assertThat(llmNode.type()).isEqualTo(NodeType.of("casehub_application"));
        var spec = extractAppSpec(graph, "my-llm");
        assertThat(spec.image()).isEqualTo("casehubio/casehub-runtime:latest");
        assertThat(spec.config()).containsEntry("llm.capability", "fast");
    }

    // ── Level 02: Accountability ──────────────────────────────────────────

    @Test
    void accountability_hasTwoNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/02-accountability/02-accountability.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(2);
    }

    @Test
    void accountability_allNodesAreCasehubApplication() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/02-accountability/02-accountability.yaml", DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node ->
                assertThat(node.type()).isEqualTo(NodeType.of("casehub_application")));
    }

    @Test
    void accountability_ledgerDependsOnPlatform() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/02-accountability/02-accountability.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("ledger")))
                .contains(NodeId.of("platform"));
    }

    @Test
    void accountability_bothNodesUseUniversalImage() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/02-accountability/02-accountability.yaml", DEPLOYMENT_REGISTRY);
        var platform = extractAppSpec(graph, "platform");
        var ledger = extractAppSpec(graph, "ledger");
        assertThat(platform.image()).isEqualTo("casehubio/casehub-runtime:latest");
        assertThat(ledger.image()).isEqualTo("casehubio/casehub-runtime:latest");
        assertThat(platform.config()).containsEntry("role", "platform");
        assertThat(ledger.config()).containsEntry("role", "ledger");
    }
// ── Level 03: Case Management ────────────────────────────────────────

    @Test
    void caseManagement_hasThreeNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/03-case-management/03-case-management.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(3);
    }

    @Test
    void caseManagement_allNodesAreCasehubApplication() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/03-case-management/03-case-management.yaml", DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node ->
                                               assertThat(node.type()).isEqualTo(NodeType.of("casehub_application")));
    }

    @Test
    void caseManagement_ledgerDependsOnPlatform() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/03-case-management/03-case-management.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("ledger")))
                .contains(NodeId.of("platform"));
    }


    @Test
    void caseManagement_qhorusDependsOnPlatformAndLedger() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/03-case-management/03-case-management.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("qhorus")))
                .containsExactlyInAnyOrder(NodeId.of("platform"), NodeId.of("ledger"));
    }

    @Test
    void caseManagement_allNodesUseUniversalImage() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/03-case-management/03-case-management.yaml", DEPLOYMENT_REGISTRY);

        var platform = extractAppSpec(graph, "platform");
        assertThat(platform.image()).isEqualTo("casehubio/casehub-runtime:latest");
        assertThat(platform.config()).containsEntry("role", "platform");
        assertThat(platform.config()).containsEntry("llm.capability", "fast");
        assertThat(platform.config()).containsEntry("llm.identity-source", "eidos");

        var ledger = extractAppSpec(graph, "ledger");
        assertThat(ledger.image()).isEqualTo("casehubio/casehub-runtime:latest");
        assertThat(ledger.config()).containsEntry("role", "ledger");

        var qhorus = extractAppSpec(graph, "qhorus");
        assertThat(qhorus.image()).isEqualTo("casehubio/casehub-runtime:latest");
        assertThat(qhorus.config()).containsEntry("role", "qhorus");
    }


// ── Level 04: Fleet Scaling ──────────────────────────────────────────

    @Test
    void llmFleet_hasFourNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(4);
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
    void llmFleet_allLlmNodesDependOnPlatform() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/04-llm-fleet/04-llm-fleet.yaml", DEPLOYMENT_REGISTRY);
        for (String llmId : List.of("my-llm.analyst", "my-llm.coder", "my-llm.assistant")) {
            assertThat(graph.dependenciesOf(NodeId.of(llmId)))
                    .as("dependencies of %s", llmId)
                    .contains(NodeId.of("platform"));
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


// ── Level 05: Config Drift ───────────────────────────────────────────

    @Test
    void configDrift_hasFourNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/05-config-drift/05-config-drift.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(4);
    }

    @Test
    void configDrift_allNodesAreCasehubApplication() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/05-config-drift/05-config-drift.yaml", DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node ->
                assertThat(node.type()).isEqualTo(NodeType.of("casehub_application")));
    }

    @Test
    void configDrift_allNodesUseUniversalImage() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/05-config-drift/05-config-drift.yaml", DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node -> {
            var spec = extractAppSpec(node);
            assertThat(spec.image()).isEqualTo("casehubio/casehub-runtime:latest");
        });
    }

    @Test
    void configDrift_allLlmNodesDependOnPlatform() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/05-config-drift/05-config-drift.yaml", DEPLOYMENT_REGISTRY);
        for (String llmId : List.of("my-llm.analyst", "my-llm.coder", "my-llm.assistant")) {
            assertThat(graph.dependenciesOf(NodeId.of(llmId)))
                    .as("dependencies of %s", llmId)
                    .contains(NodeId.of("platform"));
        }
    }

    @Test
    void configDrift_analystHasDriftedConfig() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/05-config-drift/05-config-drift.yaml", DEPLOYMENT_REGISTRY);

        var analyst = extractAppSpec(graph, "my-llm.analyst");
        assertThat(analyst.config()).containsEntry("llm.capability", "analytical");
        assertThat(analyst.config()).containsEntry("llm.domain", "data-analysis");
        assertThat(analyst.config()).containsEntry("llm.role", "analyst");
    }

    @Test
    void configDrift_coderAndAssistantUnchangedFromFleet() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/05-config-drift/05-config-drift.yaml", DEPLOYMENT_REGISTRY);

        var coder = extractAppSpec(graph, "my-llm.coder");
        assertThat(coder.config()).containsEntry("llm.capability", "fast");
        assertThat(coder.config()).containsEntry("llm.role", "developer");

        var assistant = extractAppSpec(graph, "my-llm.assistant");
        assertThat(assistant.config()).containsEntry("llm.capability", "balanced");
        assertThat(assistant.config()).containsEntry("llm.role", "assistant");
    }


// ── Level 06: Lifecycle ──────────────────────────────────────────────

    @Test
    void lifecycle_hasFourNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/06-lifecycle/06-lifecycle.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(4);
    }

    @Test
    void lifecycle_allNodesAreCasehubApplication() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/06-lifecycle/06-lifecycle.yaml", DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node ->
                assertThat(node.type()).isEqualTo(NodeType.of("casehub_application")));
    }

    @Test
    void lifecycle_allNodesUseUniversalImage() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/06-lifecycle/06-lifecycle.yaml", DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node -> {
            var spec = extractAppSpec(node);
            assertThat(spec.image()).isEqualTo("casehubio/casehub-runtime:latest");
        });
    }

    @Test
    void lifecycle_allLlmNodesDependOnPlatform() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/06-lifecycle/06-lifecycle.yaml", DEPLOYMENT_REGISTRY);
        for (String llmId : List.of("my-llm.analyst", "my-llm.coder", "my-llm.assistant")) {
            assertThat(graph.dependenciesOf(NodeId.of(llmId)))
                    .as("dependencies of %s", llmId)
                    .contains(NodeId.of("platform"));
        }
    }

    @Test
    void lifecycle_configMatchesFleet() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/06-lifecycle/06-lifecycle.yaml", DEPLOYMENT_REGISTRY);

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


// ── Level 07: Topology Mutations ─────────────────────────────────────

    @Test
    void topologyMutations_hasFourNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/07-topology-mutations/07-topology-mutations.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(4);
    }

    @Test
    void topologyMutations_allNodesUseUniversalImage() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/07-topology-mutations/07-topology-mutations.yaml", DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node -> {
            var spec = extractAppSpec(node);
            assertThat(spec.image()).isEqualTo("casehubio/casehub-runtime:latest");
        });
    }

    @Test
    void topologyMutations_researcherReplacesAssistant() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/07-topology-mutations/07-topology-mutations.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes().keySet().stream()
                        .map(NodeId::value)
                        .filter(id -> id.startsWith("my-llm."))
                        .toList())
                .containsExactlyInAnyOrder("my-llm.analyst", "my-llm.coder", "my-llm.researcher");
    }

    @Test
    void topologyMutations_researcherConfig() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/07-topology-mutations/07-topology-mutations.yaml", DEPLOYMENT_REGISTRY);

        var researcher = extractAppSpec(graph, "my-llm.researcher");
        assertThat(researcher.config()).containsEntry("llm.capability", "thorough");
        assertThat(researcher.config()).containsEntry("llm.role", "researcher");
        assertThat(researcher.config()).containsEntry("llm.domain", "knowledge-discovery");
    }

    @Test
    void topologyMutations_allLlmNodesDependOnPlatform() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/07-topology-mutations/07-topology-mutations.yaml", DEPLOYMENT_REGISTRY);
        for (String llmId : List.of("my-llm.analyst", "my-llm.coder", "my-llm.researcher")) {
            assertThat(graph.dependenciesOf(NodeId.of(llmId)))
                    .as("dependencies of %s", llmId)
                    .contains(NodeId.of("platform"));
        }
    }


// ── Level 08: Fleet Observability ────────────────────────────────────

    @Test
    void fleetObservability_hasFourNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/08-fleet-observability/08-fleet-observability.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(4);
    }

    @Test
    void fleetObservability_allNodesUseUniversalImage() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/08-fleet-observability/08-fleet-observability.yaml", DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node -> {
            var spec = extractAppSpec(node);
            assertThat(spec.image()).isEqualTo("casehubio/casehub-runtime:latest");
        });
    }

    @Test
    void fleetObservability_allNodesAreCasehubApplication() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/08-fleet-observability/08-fleet-observability.yaml", DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node ->
                assertThat(node.type()).isEqualTo(NodeType.of("casehub_application")));
    }

    @Test
    void fleetObservability_configMatchesFleet() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/08-fleet-observability/08-fleet-observability.yaml", DEPLOYMENT_REGISTRY);

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


// ── Level 09: Chat UI ────────────────────────────────────────────────

    @Test
    void chatUi_hasFourNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/09-chat-ui/09-chat-ui.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(4);
    }

    @Test
    void chatUi_allNodesUseUniversalImage() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/09-chat-ui/09-chat-ui.yaml", DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node -> {
            var spec = extractAppSpec(node);
            assertThat(spec.image()).isEqualTo("casehubio/casehub-runtime:latest");
        });
    }

    @Test
    void chatUi_chatViewerDependsOnQhorus() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/09-chat-ui/09-chat-ui.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("chat-viewer")))
                .contains(NodeId.of("qhorus"));
    }

    @Test
    void chatUi_qhorusDependsOnPlatformAndLedger() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/09-chat-ui/09-chat-ui.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("qhorus")))
                .containsExactlyInAnyOrder(NodeId.of("platform"), NodeId.of("ledger"));
    }

    @Test
    void chatUi_chatViewerHasChatConfig() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/09-chat-ui/09-chat-ui.yaml", DEPLOYMENT_REGISTRY);
        var chatViewer = extractAppSpec(graph, "chat-viewer");
        assertThat(chatViewer.config()).containsEntry("role", "chat-viewer");
        assertThat(chatViewer.config()).containsEntry("chat.mode", "conversation-viewer");
        assertThat(chatViewer.config()).containsEntry("chat.session-source", "qhorus");
        assertThat(chatViewer.config()).containsEntry("chat.channel-type", "direct");
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
// ── Level 10: Pool Deployment ──────────────────────────────────────

    @Test
    void poolDeployment_hasThreeNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/10-pool-deployment/10-pool-deployment.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(3);
    }

    @Test
    void poolDeployment_containsPoolNode() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/10-pool-deployment/10-pool-deployment.yaml", DEPLOYMENT_REGISTRY);
        var poolNode = graph.nodes().values().stream()
                            .filter(n -> n.spec() instanceof io.casehub.ops.api.deployment.PoolNodeSpec)
                            .findFirst();
        assertThat(poolNode).as("graph must contain a pool node").isPresent();
        var poolSpec = (io.casehub.ops.api.deployment.PoolNodeSpec) poolNode.get().spec();
        assertThat(poolSpec.applicationId()).isEqualTo("support-app");
        assertThat(poolSpec.agentId()).isEqualTo("support-agent");
        assertThat(poolSpec.backend()).isEqualTo("claudony");
        assertThat(poolSpec.minActive()).isEqualTo(3);
        assertThat(poolSpec.maxActive()).isEqualTo(8);
    }

    @Test
    void poolDeployment_poolDependsOnSupportApp() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/10-pool-deployment/10-pool-deployment.yaml", DEPLOYMENT_REGISTRY);
        var poolNodeId = graph.nodes().keySet().stream()
                              .filter(id -> id.value().contains("support-pool"))
                              .findFirst();
        assertThat(poolNodeId).as("pool node ID must exist").isPresent();
        assertThat(graph.dependenciesOf(poolNodeId.get()))
                .as("pool must depend on support-app")
                .contains(NodeId.of("support-app"));
    }

    @Test
    void poolDeployment_supportAppDependsOnPlatform() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/10-pool-deployment/10-pool-deployment.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("support-app")))
                .contains(NodeId.of("platform"));
    }

    @Test
    void poolDeployment_allAppsUseUniversalImage() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/10-pool-deployment/10-pool-deployment.yaml", DEPLOYMENT_REGISTRY);
        var platform = extractAppSpec(graph, "platform");
        var supportApp = extractAppSpec(graph, "support-app");
        assertThat(platform.image()).isEqualTo("casehubio/casehub-runtime:latest");
        assertThat(supportApp.image()).isEqualTo("casehubio/casehub-runtime:latest");
    }


// ── Level 14: Fault Recovery ──────────────────────────────────────

    @Test
    void faultRecovery_hasThreeNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/14-fault-recovery/14-fault-recovery.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(3);
    }

    @Test
    void faultRecovery_containsPoolNode() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/14-fault-recovery/14-fault-recovery.yaml", DEPLOYMENT_REGISTRY);
        var poolNode = graph.nodes().values().stream()
                            .filter(n -> n.spec() instanceof io.casehub.ops.api.deployment.PoolNodeSpec)
                            .findFirst();
        assertThat(poolNode).as("graph must contain a pool node").isPresent();
        var poolSpec = (io.casehub.ops.api.deployment.PoolNodeSpec) poolNode.get().spec();
        assertThat(poolSpec.applicationId()).isEqualTo("support-app");
        assertThat(poolSpec.agentId()).isEqualTo("support-agent");
        assertThat(poolSpec.minActive()).isEqualTo(3);
        assertThat(poolSpec.maxActive()).isEqualTo(8);
    }

    @Test
    void faultRecovery_poolDependsOnSupportApp() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/14-fault-recovery/14-fault-recovery.yaml", DEPLOYMENT_REGISTRY);
        var poolNodeId = graph.nodes().keySet().stream()
                              .filter(id -> id.value().contains("support-pool"))
                              .findFirst();
        assertThat(poolNodeId).as("pool node ID must exist").isPresent();
        assertThat(graph.dependenciesOf(poolNodeId.get()))
                .as("pool must depend on support-app")
                .contains(NodeId.of("support-app"));
    }

    @Test
    void faultRecovery_supportAppDependsOnPlatform() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/14-fault-recovery/14-fault-recovery.yaml", DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("support-app")))
                .contains(NodeId.of("platform"));
    }

    @Test
    void faultRecovery_allAppsUseUniversalImage() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/14-fault-recovery/14-fault-recovery.yaml", DEPLOYMENT_REGISTRY);
        var platform = extractAppSpec(graph, "platform");
        var supportApp = extractAppSpec(graph, "support-app");
        assertThat(platform.image()).isEqualTo("casehubio/casehub-runtime:latest");
        assertThat(supportApp.image()).isEqualTo("casehubio/casehub-runtime:latest");
    }


// ── Level 12: Ad-hoc LLM Deployer ──────────────────────────────────

    @Test
    void adhocLlmDeployer_hasTwoNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/12-adhoc-llm-deployer/12-adhoc-llm-deployer.yaml",
                DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(2);
    }

    @Test
    void adhocLlmDeployer_allNodesAreCasehubApplication() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/12-adhoc-llm-deployer/12-adhoc-llm-deployer.yaml",
                DEPLOYMENT_REGISTRY);
        graph.nodes().values().forEach(node ->
                assertThat(node.type()).isEqualTo(NodeType.of("casehub_application")));
    }

    @Test
    void adhocLlmDeployer_deployerDependsOnPlatform() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/12-adhoc-llm-deployer/12-adhoc-llm-deployer.yaml",
                DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("deployer")))
                .contains(NodeId.of("platform"));
    }

    @Test
    void adhocLlmDeployer_deployerHasLlmConfig() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/12-adhoc-llm-deployer/12-adhoc-llm-deployer.yaml",
                DEPLOYMENT_REGISTRY);
        var deployerNode = graph.nodes().get(NodeId.of("deployer"));
        assertThat(deployerNode).isNotNull();
        var spec = extractAppSpec(graph, "deployer");
        assertThat(spec.config()).containsEntry("llm.role", "deployer");
        assertThat(spec.config()).containsEntry("deployer.specialist-catalog", "specialist-catalog");
    }

    @Test
    void adhocLlmDeployer_allAppsUseUniversalImage() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/12-adhoc-llm-deployer/12-adhoc-llm-deployer.yaml",
                DEPLOYMENT_REGISTRY);
        var platform = extractAppSpec(graph, "platform");
        var deployer = extractAppSpec(graph, "deployer");
        assertThat(platform.image()).isEqualTo("casehubio/casehub-runtime:latest");
        assertThat(deployer.image()).isEqualTo("casehubio/casehub-runtime:latest");
    }
// ── Level 15: Governed Interaction ────────────────────────────────────

    @Test
    void governedInteraction_hasFourNodes() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/15-governed-interaction/15-governed-interaction.yaml",
                DEPLOYMENT_REGISTRY);
        assertThat(graph.nodes()).hasSize(4);
    }

    @Test
    void governedInteraction_channelHasProtocols() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/15-governed-interaction/15-governed-interaction.yaml",
                DEPLOYMENT_REGISTRY);
        var channelNode = graph.nodes().get(NodeId.of("design-review"));
        assertThat(channelNode.spec()).isInstanceOf(io.casehub.ops.api.deployment.ChannelNodeSpec.class);
        var channelSpec = (io.casehub.ops.api.deployment.ChannelNodeSpec) channelNode.spec();
        assertThat(channelSpec.protocols()).containsExactly("NEGOTIATION");
        assertThat(channelSpec.protocolParticipants()).containsExactly("coordinator", "reviewer-1", "reviewer-2");
        assertThat(channelSpec.policyOverrides()).containsEntry("negotiation.acceptance", "majority");
        assertThat(channelSpec.policyOverrides()).containsEntry("negotiation.max-rounds", "3");
    }

    @Test
    void governedInteraction_channelDependsOnAgents() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/15-governed-interaction/15-governed-interaction.yaml",
                DEPLOYMENT_REGISTRY);
        assertThat(graph.dependenciesOf(NodeId.of("design-review")))
                .containsExactlyInAnyOrder(NodeId.of("coordinator"), NodeId.of("reviewer-1"), NodeId.of("reviewer-2"));
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

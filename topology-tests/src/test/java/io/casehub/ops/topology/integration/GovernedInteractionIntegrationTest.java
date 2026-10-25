package io.casehub.ops.topology.integration;

import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.StepOutcome;
import io.casehub.desiredstate.api.TransitionPlan;
import io.casehub.desiredstate.api.TransitionResult;
import io.casehub.ops.api.deployment.ChannelNodeSpec;
import io.casehub.ops.api.deployment.DeploymentNodeSpec;
import io.casehub.ops.api.infra.InfraNodeSpec;
import io.casehub.ops.claudony.ClaudonyDeploymentTarget;
import io.casehub.ops.topology.compilation.TopologyTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Level 15: Governed Interaction — live integration test.
 *
 * Proves the four-layer composition end-to-end:
 * Platform (CDI + manifest + Jlama) → Qhorus (enforcement) → Agency (NegotiationProtocol) → Ops (YAML)
 *
 * Prerequisites:
 * - Docker (for claudony + postgres via Testcontainers)
 * - langchain4j-jlama on classpath with a small quantized model
 * - agency NegotiationProtocol available on classpath
 *
 * Run: mvn test -pl topology-tests -Pintegration-live -Dtest="GovernedInteractionIntegrationTest"
 */
@Tag("integration-live")
@ServiceCluster(services = {
        @Service(name = "claudony", repo = "claudony/app")
})
class GovernedInteractionIntegrationTest extends TopologyTestBase {

    private static final Map<String, String> DEPLOYMENT_REGISTRY =
            buildTypeRegistryFrom(InfraNodeSpec.class, DeploymentNodeSpec.class);

    @ServiceEndpoint("claudony")
    static String claudonyUrl;

    @Test
    void deploysGovernedInteractionTopology() throws IOException {
        DesiredStateGraph graph = compileSingleGraph(
                "topologies/deployment/15-governed-interaction/15-governed-interaction.yaml",
                DEPLOYMENT_REGISTRY);

        assertThat(graph.nodes()).hasSize(4);

        ClaudonyDeploymentTarget target = createClaudonyTarget(claudonyUrl);
        deployAllNodes(graph, target);

        var channelSpec = (ChannelNodeSpec) graph.nodes()
                .get(io.casehub.desiredstate.api.NodeId.of("design-review")).spec();
        assertThat(channelSpec.protocols()).containsExactly("NEGOTIATION");
        assertThat(channelSpec.policyOverrides())
                .containsEntry("negotiation.acceptance", "majority");
    }

    // TODO(#188): operational assertions require agency NegotiationProtocol + qhorus enforcement pipeline
    // 1. Coordinator sends PROPOSE → commitment opens
    // 2. Reviewer-1 sends RESPONSE (endorse) → negotiation state tracks 1/2
    // 3. Coordinator attempts DONE → BLOCKED (majority not yet reached)
    // 4. Reviewer-2 sends RESPONSE (endorse) → negotiation state tracks 2/2
    // 5. Coordinator sends DONE → ALLOWED (majority reached)
    // 6. Ledger records full negotiation chain with protocol advisories
}

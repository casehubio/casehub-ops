package io.casehub.ops.topology.reconciliation;

import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.HumanGating;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("reconciliation")
class FailableNodeProvisionerTest {

    private static final DesiredStateGraph EMPTY_GRAPH = new DefaultDesiredStateGraphFactory().empty();

    private FailableNodeProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new FailableNodeProvisioner();
        provisioner.setHandledTypes(Set.of(NodeType.of("k8s_deployment")));
    }

    @Test
    void provision_succeeds_by_default() {
        DesiredNode     node   = new DesiredNode(NodeId.of("app"), new TestSpec("k8s_deployment"), HumanGating.NONE);
        ProvisionResult result = provisioner.provision(node, new ProvisionContext("tenant", EMPTY_GRAPH));
        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(provisioner.provisioned).containsExactly(node);
    }

    @Test
    void provision_fails_when_node_configured_to_fail() {
        provisioner.failNode("app", 2);
        DesiredNode      node = new DesiredNode(NodeId.of("app"), new TestSpec("k8s_deployment"), HumanGating.NONE);
        ProvisionContext ctx  = new ProvisionContext("tenant", EMPTY_GRAPH);

        ProvisionResult r1 = provisioner.provision(node, ctx);
        assertThat(r1).isInstanceOf(ProvisionResult.Failed.class);

        ProvisionResult r2 = provisioner.provision(node, ctx);
        assertThat(r2).isInstanceOf(ProvisionResult.Failed.class);

        ProvisionResult r3 = provisioner.provision(node, ctx);
        assertThat(r3).isInstanceOf(ProvisionResult.Success.class);
    }

    @Test
    void deprovision_succeeds_by_default() {
        DesiredNode       node   = new DesiredNode(NodeId.of("app"), new TestSpec("k8s_deployment"), HumanGating.NONE);
        DeprovisionResult result = provisioner.deprovision(node, new DeprovisionContext("tenant", EMPTY_GRAPH));
        assertThat(result).isInstanceOf(DeprovisionResult.Success.class);
        assertThat(provisioner.deprovisioned).containsExactly(node);
    }

    @Test
    void records_provision_order() {
        DesiredNode      a   = new DesiredNode(NodeId.of("a"), new TestSpec("k8s_deployment"), HumanGating.NONE);
        DesiredNode      b   = new DesiredNode(NodeId.of("b"), new TestSpec("k8s_deployment"), HumanGating.NONE);
        ProvisionContext ctx = new ProvisionContext("tenant", EMPTY_GRAPH);
        provisioner.provision(a, ctx);
        provisioner.provision(b, ctx);
        assertThat(provisioner.provisioned).containsExactly(a, b);
    }

    @Test
    void clear_resets_state() {
        provisioner.failNode("x", 1);
        DesiredNode node = new DesiredNode(NodeId.of("x"), new TestSpec("k8s_deployment"), HumanGating.NONE);
        provisioner.provision(node, new ProvisionContext("tenant", EMPTY_GRAPH));
        provisioner.clear();
        assertThat(provisioner.provisioned).isEmpty();
        ProvisionResult result = provisioner.provision(node, new ProvisionContext("tenant", EMPTY_GRAPH));
        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
    }

    private record TestSpec(String type) implements NodeSpec {
        @Override
        public NodeType nodeType() {
            return NodeType.of(type);
        }
    }
}

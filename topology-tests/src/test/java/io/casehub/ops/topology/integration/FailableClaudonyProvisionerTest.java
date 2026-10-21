package io.casehub.ops.topology.integration;

import io.casehub.desiredstate.runtime.DefaultDesiredStateGraphFactory;
import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.HumanGating;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeProvisioner;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.desiredstate.api.ResumeContext;
import io.casehub.desiredstate.api.ResumeResult;
import io.casehub.desiredstate.api.SuspendContext;
import io.casehub.desiredstate.api.SuspendResult;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyNodeTypes;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FailableClaudonyProvisionerTest {

    private static final DesiredStateGraph EMPTY_GRAPH =
        new DefaultDesiredStateGraphFactory().empty();

    @Test
    void delegatesToRealProvisionerWhenNoFailureInjected() {
        var delegate = new CountingProvisioner();
        var failable = new FailableClaudonyProvisioner(delegate);

        var result = failable.provision(testNode("app-1"),
            new ProvisionContext("ns", EMPTY_GRAPH));

        assertThat(result).isInstanceOf(ProvisionResult.Success.class);
        assertThat(delegate.provisionCount.get()).isEqualTo(1);
    }

    @Test
    void injectsFailureForConfiguredCountdownThenDelegates() {
        var delegate = new CountingProvisioner();
        var failable = new FailableClaudonyProvisioner(delegate);

        failable.failNode("claudony_app:app-1", 2);
        var node = testNode("app-1");
        var ctx = new ProvisionContext("ns", EMPTY_GRAPH);

        assertThat(failable.provision(node, ctx))
            .isInstanceOf(ProvisionResult.Failed.class);
        assertThat(failable.provision(node, ctx))
            .isInstanceOf(ProvisionResult.Failed.class);
        assertThat(failable.provision(node, ctx))
            .isInstanceOf(ProvisionResult.Success.class);
    }

    @Test
    void clearFailuresRemovesAllBudgets() {
        var delegate = new CountingProvisioner();
        var failable = new FailableClaudonyProvisioner(delegate);

        failable.failNode("claudony_app:app-1", 5);
        failable.clearFailures();

        assertThat(failable.provision(testNode("app-1"),
            new ProvisionContext("ns", EMPTY_GRAPH)))
            .isInstanceOf(ProvisionResult.Success.class);
    }

    private static DesiredNode testNode(String name) {
        var spec = new ClaudonyAppNodeSpec(name, name, "test", "image:latest", Map.of());
        return new DesiredNode(NodeId.of(spec.nodeId()), spec, HumanGating.NONE);
    }

    static class CountingProvisioner implements NodeProvisioner {
        final AtomicInteger provisionCount = new AtomicInteger();

        @Override
        public ProvisionResult provision(DesiredNode node, ProvisionContext context) {
            provisionCount.incrementAndGet();
            return new ProvisionResult.Success();
        }

        @Override
        public DeprovisionResult deprovision(DesiredNode node, DeprovisionContext context) {
            return new DeprovisionResult.Success();
        }

        @Override
        public SuspendResult suspend(DesiredNode node, SuspendContext context) {
            return new SuspendResult.Success();
        }

        @Override
        public ResumeResult resume(DesiredNode node, ResumeContext context) {
            return new ResumeResult.Success();
        }

        @Override public Set<NodeType> handledTypes() { return Set.of(ClaudonyNodeTypes.APP); }
        @Override public boolean supportsStatefulLifecycle() { return true; }
        @Override public Duration resyncInterval() { return Duration.ofMinutes(5); }
    }
}

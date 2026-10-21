package io.casehub.ops.topology.integration;

import io.casehub.desiredstate.api.DeprovisionContext;
import io.casehub.desiredstate.api.DeprovisionResult;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.NodeProvisioner;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.desiredstate.api.ProvisionContext;
import io.casehub.desiredstate.api.ProvisionResult;
import io.casehub.desiredstate.api.ResumeContext;
import io.casehub.desiredstate.api.ResumeResult;
import io.casehub.desiredstate.api.SuspendContext;
import io.casehub.desiredstate.api.SuspendResult;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class FailableClaudonyProvisioner implements NodeProvisioner {

    private final NodeProvisioner delegate;
    private final ConcurrentHashMap<String, AtomicInteger> failureBudgets = new ConcurrentHashMap<>();

    FailableClaudonyProvisioner(NodeProvisioner delegate) {
        this.delegate = delegate;
    }

    void failNode(String nodeId, int times) {
        failureBudgets.put(nodeId, new AtomicInteger(times));
    }

    void clearFailures() {
        failureBudgets.clear();
    }

    @Override
    public ProvisionResult provision(DesiredNode node, ProvisionContext context) {
        if (shouldFail(node)) {
            return new ProvisionResult.Failed("injected failure for " + node.id().value());
        }
        return delegate.provision(node, context);
    }

    @Override
    public DeprovisionResult deprovision(DesiredNode node, DeprovisionContext context) {
        return delegate.deprovision(node, context);
    }

    @Override
    public SuspendResult suspend(DesiredNode node, SuspendContext context) {
        if (shouldFail(node)) {
            return new SuspendResult.Failed("injected suspend failure for " + node.id().value());
        }
        return delegate.suspend(node, context);
    }

    @Override
    public ResumeResult resume(DesiredNode node, ResumeContext context) {
        if (shouldFail(node)) {
            return new ResumeResult.Failed("injected resume failure for " + node.id().value());
        }
        return delegate.resume(node, context);
    }

    @Override public Set<NodeType> handledTypes() { return delegate.handledTypes(); }
    @Override public boolean supportsStatefulLifecycle() { return delegate.supportsStatefulLifecycle(); }
    @Override public Duration resyncInterval() { return delegate.resyncInterval(); }

    private boolean shouldFail(DesiredNode node) {
        AtomicInteger budget = failureBudgets.get(node.id().value());
        return budget != null && budget.getAndDecrement() > 0;
    }
}

package io.casehub.ops.topology.reconciliation;

import io.casehub.desiredstate.api.*;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class FailableNodeProvisioner implements NodeProvisioner {

    public final CopyOnWriteArrayList<DesiredNode> provisioned = new CopyOnWriteArrayList<>();
    public final CopyOnWriteArrayList<DesiredNode> deprovisioned = new CopyOnWriteArrayList<>();

    private final Map<String, AtomicInteger> failureBudgets = new ConcurrentHashMap<>();
    private volatile Set<NodeType> handledTypes = Set.of();

    @Override
    public Set<NodeType> handledTypes() {
        return handledTypes;
    }

    @Override
    public Duration resyncInterval() {
        return Duration.ofMinutes(5);
    }

    @Override
    public ProvisionResult provision(DesiredNode node, ProvisionContext context) {
        provisioned.add(node);
        AtomicInteger budget = failureBudgets.get(node.id().value());
        if (budget != null && budget.getAndDecrement() > 0) {
            return new ProvisionResult.Failed("injected failure for " + node.id().value());
        }
        return new ProvisionResult.Success();
    }

    @Override
    public DeprovisionResult deprovision(DesiredNode node, DeprovisionContext context) {
        deprovisioned.add(node);
        return new DeprovisionResult.Success();
    }

    public void failNode(String nodeId, int times) {
        failureBudgets.put(nodeId, new AtomicInteger(times));
    }

    public void setHandledTypes(Set<NodeType> types) {
        this.handledTypes = Set.copyOf(types);
    }

    public void clear() {
        provisioned.clear();
        deprovisioned.clear();
        failureBudgets.clear();
    }
}

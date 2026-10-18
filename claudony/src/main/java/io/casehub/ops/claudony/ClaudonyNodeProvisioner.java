package io.casehub.ops.claudony;

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
import io.casehub.ops.api.SpecHashStore;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyNodeTypes;
import io.casehub.ops.api.claudony.ClaudonyPoolNodeSpec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Set;

@ApplicationScoped
public class ClaudonyNodeProvisioner implements NodeProvisioner {

    private static final Set<NodeType> HANDLED = Set.of(ClaudonyNodeTypes.APP, ClaudonyNodeTypes.POOL);

    private final ClaudonyFleetClient fleetClient;
    private final SpecHashStore specHashStore;

    @Inject
    public ClaudonyNodeProvisioner(ClaudonyFleetClient fleetClient,
                                    SpecHashStore specHashStore) {
        this.fleetClient = fleetClient;
        this.specHashStore = specHashStore;
    }

    @Override public Set<NodeType> handledTypes() { return HANDLED; }
    @Override public boolean supportsStatefulLifecycle() { return true; }

    @Override
    public ProvisionResult provision(DesiredNode node, ProvisionContext context) {
        try {
            return switch (node.spec()) {
                case ClaudonyAppNodeSpec spec -> provisionApp(node, spec, context);
                case ClaudonyPoolNodeSpec spec -> provisionPool(node, spec);
                default -> new ProvisionResult.Failed("Unknown spec type: " + node.spec().getClass());
            };
        } catch (Exception e) {
            return new ProvisionResult.Failed(e.getMessage());
        }
    }

    @Override
    public DeprovisionResult deprovision(DesiredNode node, DeprovisionContext context) {
        try {
            return switch (node.spec()) {
                case ClaudonyAppNodeSpec spec -> deprovisionApp(node, spec);
                case ClaudonyPoolNodeSpec spec -> deprovisionPool(node, spec);
                default -> new DeprovisionResult.Failed("Unknown spec type: " + node.spec().getClass());
            };
        } catch (Exception e) {
            return new DeprovisionResult.Failed(e.getMessage());
        }
    }

    @Override
    public SuspendResult suspend(DesiredNode node, SuspendContext context) {
        if (node.spec() instanceof ClaudonyAppNodeSpec spec) {
            try {
                fleetClient.suspendApp(spec.name());
                return new SuspendResult.Success();
            } catch (ClaudonyApiException e) {
                return new SuspendResult.Failed("Suspend failed: " + e.getMessage());
            }
        }
        return new SuspendResult.Failed("suspend not supported for " + node.type().value());
    }

    @Override
    public ResumeResult resume(DesiredNode node, ResumeContext context) {
        if (node.spec() instanceof ClaudonyAppNodeSpec spec) {
            try {
                fleetClient.resumeApp(spec.name());
                return new ResumeResult.Success();
            } catch (ClaudonyApiException e) {
                return new ResumeResult.Failed("Resume failed: " + e.getMessage());
            }
        }
        return new ResumeResult.Failed("resume not supported for " + node.type().value());
    }

    private ProvisionResult provisionApp(DesiredNode node, ClaudonyAppNodeSpec spec,
                                          ProvisionContext context) {
        var appSpec = new ClaudonyAppSpec(
            spec.name(), context.tenancyId(), spec.displayName(),
            spec.description(), spec.image(), spec.config(), null);
        try {
            fleetClient.registerApp(appSpec);
        } catch (ClaudonyApiException e) {
            if (e.statusCode() == 409) {
                fleetClient.updateApp(spec.name(), appSpec);
            } else {
                return new ProvisionResult.Failed(
                    "Register failed (" + e.statusCode() + "): " + e.responseBody());
            }
        }
        specHashStore.record(node.id(), spec);
        return new ProvisionResult.Success();
    }

    private ProvisionResult provisionPool(DesiredNode node, ClaudonyPoolNodeSpec spec) {
        var appInfo = fleetClient.getApp(spec.appName());
        if (appInfo.isEmpty()) {
            return new ProvisionResult.Failed("parent app not registered: " + spec.appName());
        }
        var info = appInfo.get();
        var pools = new ArrayList<>(info.pools().stream()
            .filter(p -> !spec.agentId().equals(p.agentId()))
            .map(p -> new ClaudonyAppSpec.PoolSpec(p.agentId(), p.minActive(), p.maxActive()))
            .toList());
        pools.add(new ClaudonyAppSpec.PoolSpec(spec.agentId(), spec.minActive(), spec.maxActive()));

        var updated = new ClaudonyAppSpec(
            info.name(), info.namespace(), info.displayName(),
            info.description(), info.image(), info.config(), pools);
        fleetClient.updateApp(spec.appName(), updated);
        specHashStore.record(node.id(), spec);
        return new ProvisionResult.Success();
    }

    private DeprovisionResult deprovisionApp(DesiredNode node, ClaudonyAppNodeSpec spec) {
        try {
            fleetClient.deregisterApp(spec.name());
        } catch (ClaudonyApiException e) {
            if (e.statusCode() != 404) {
                return new DeprovisionResult.Failed("Deregister failed: " + e.getMessage());
            }
        }
        specHashStore.remove(node.id());
        return new DeprovisionResult.Success();
    }

    private DeprovisionResult deprovisionPool(DesiredNode node, ClaudonyPoolNodeSpec spec) {
        var appInfo = fleetClient.getApp(spec.appName());
        if (appInfo.isEmpty()) {
            specHashStore.remove(node.id());
            return new DeprovisionResult.Success();
        }
        var info = appInfo.get();
        var pools = info.pools().stream()
            .filter(p -> !spec.agentId().equals(p.agentId()))
            .map(p -> new ClaudonyAppSpec.PoolSpec(p.agentId(), p.minActive(), p.maxActive()))
            .toList();

        var updated = new ClaudonyAppSpec(
            info.name(), info.namespace(), info.displayName(),
            info.description(), info.image(), info.config(), pools);
        fleetClient.updateApp(spec.appName(), updated);
        specHashStore.remove(node.id());
        return new DeprovisionResult.Success();
    }
}

package io.casehub.ops.claudony;

import io.casehub.desiredstate.api.ActualState;
import io.casehub.desiredstate.api.ActualStateAdapter;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraph;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.NodeType;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyNodeTypes;
import io.casehub.ops.api.claudony.ClaudonyPoolNodeSpec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ClaudonyActualStateAdapter implements ActualStateAdapter {

    private static final Set<NodeType> HANDLED = Set.of(ClaudonyNodeTypes.APP, ClaudonyNodeTypes.POOL);

    private final ClaudonyFleetClient fleetClient;
    private final ClaudonySpecHashStore specHashStore;

    @Inject
    public ClaudonyActualStateAdapter(ClaudonyFleetClient fleetClient,
                                       ClaudonySpecHashStore specHashStore) {
        this.fleetClient = fleetClient;
        this.specHashStore = specHashStore;
    }

    @Override
    public Set<NodeType> handledTypes() { return HANDLED; }

    @Override
    public ActualState readActual(DesiredStateGraph desired, String tenancyId) {
        var appsByName = new HashMap<String, ClaudonyAppInfo>();
        for (var info : fleetClient.listApps(tenancyId)) {
            appsByName.put(info.name(), info);
        }

        var statuses = new HashMap<NodeId, NodeStatus>();
        for (var entry : desired.nodes().entrySet()) {
            var node = entry.getValue();
            if (!HANDLED.contains(node.type())) continue;
            statuses.put(entry.getKey(), checkNode(node, appsByName));
        }
        return new ActualState(statuses);
    }

    private NodeStatus checkNode(DesiredNode node, Map<String, ClaudonyAppInfo> appsByName) {
        return switch (node.spec()) {
            case ClaudonyAppNodeSpec spec -> checkApp(node, spec, appsByName);
            case ClaudonyPoolNodeSpec spec -> checkPool(node, spec, appsByName);
            default -> NodeStatus.UNKNOWN;
        };
    }

    private NodeStatus checkApp(DesiredNode node, ClaudonyAppNodeSpec spec,
                                 Map<String, ClaudonyAppInfo> appsByName) {
        var info = appsByName.get(spec.name());
        if (info == null) return NodeStatus.ABSENT;

        var external = ClaudonyHealthMapper.toNodeStatus(info.health());
        if (external == NodeStatus.ABSENT) return NodeStatus.ABSENT;
        if (external == NodeStatus.DRIFTED) return NodeStatus.DRIFTED;
        if (external == NodeStatus.PRESENT && specHashStore.hasDrifted(node.id(), spec)) {
            return NodeStatus.DRIFTED;
        }
        return external;
    }

    private NodeStatus checkPool(DesiredNode node, ClaudonyPoolNodeSpec spec,
                                  Map<String, ClaudonyAppInfo> appsByName) {
        var appInfo = appsByName.get(spec.appName());
        if (appInfo == null) return NodeStatus.ABSENT;

        var actualPool = appInfo.pools().stream()
            .filter(p -> spec.agentId().equals(p.agentId()))
            .findFirst();
        if (actualPool.isEmpty()) return NodeStatus.ABSENT;

        var pool = actualPool.get();
        if (pool.minActive() != spec.minActive() || pool.maxActive() != spec.maxActive()) {
            return NodeStatus.DRIFTED;
        }

        if (specHashStore.hasDrifted(node.id(), spec)) {
            return NodeStatus.DRIFTED;
        }
        return NodeStatus.PRESENT;
    }
}

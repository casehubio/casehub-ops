package io.casehub.ops.claudony;

import io.casehub.desiredstate.annotations.DesiredStateQualifier;
import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.Dependency;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraphFactory;
import io.casehub.desiredstate.api.GoalCompiler;
import io.casehub.desiredstate.api.HumanGating;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.ops.api.claudony.ClaudonyGoals;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@DesiredStateQualifier(namespace = "claudony")
public class ClaudonyGoalCompiler implements GoalCompiler<ClaudonyGoals> {

    @Override
    public CompilationResult compile(ClaudonyGoals goals, DesiredStateGraphFactory factory) {
        List<DesiredNode> nodes = new ArrayList<>();
        List<Dependency> dependencies = new ArrayList<>();

        for (var entry : goals.apps()) {
            var spec = entry.spec();
            nodes.add(new DesiredNode(NodeId.of(spec.nodeId()), spec, HumanGating.NONE));
            for (String dep : entry.dependsOn()) {
                dependencies.add(new Dependency(NodeId.of(spec.nodeId()), NodeId.of(dep)));
            }
        }

        for (var entry : goals.pools()) {
            var spec = entry.spec();
            var poolId = NodeId.of(spec.nodeId());
            var appId = NodeId.of("claudony_app:" + spec.appName());
            nodes.add(new DesiredNode(poolId, spec, HumanGating.NONE));
            dependencies.add(new Dependency(poolId, appId));
            for (String dep : entry.dependsOn()) {
                dependencies.add(new Dependency(poolId, NodeId.of(dep)));
            }
        }

        return CompilationResult.single(factory.of(nodes, dependencies));
    }
}

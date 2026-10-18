package io.casehub.ops.compliance;

import io.casehub.desiredstate.annotations.DesiredStateQualifier;
import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.Dependency;
import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.DesiredStateGraphFactory;
import io.casehub.desiredstate.api.GoalCompiler;
import io.casehub.desiredstate.api.HumanGating;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.ops.api.compliance.ComplianceControlSpec;
import io.casehub.ops.api.compliance.ComplianceGoals;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@DesiredStateQualifier(namespace = "compliance")
public class ComplianceGoalCompiler implements GoalCompiler<ComplianceGoals> {

    @Override
    public CompilationResult compile(ComplianceGoals goals, DesiredStateGraphFactory factory) {
        List<DesiredNode> nodes = new ArrayList<>();
        List<Dependency>  deps  = new ArrayList<>();

        for (var entry : goals.controls()) {
            ComplianceControlSpec spec = entry.spec();
            nodes.add(new DesiredNode(
                    NodeId.of(spec.controlId()),
                    spec, spec.requiresHumanReview() ? HumanGating.ALL : HumanGating.NONE));

            for (String depId : entry.dependsOn()) {
                deps.add(new Dependency(NodeId.of(spec.controlId()), NodeId.of(depId)));
            }
        }
        return CompilationResult.single(factory.of(nodes, deps));
    }
}

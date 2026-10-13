package io.casehub.ops.deployment.drift;

import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.RuntimeConfig;
import io.casehub.ops.testing.StubDeploymentTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CaseHubAppDriftCheckerTest {

    private StubDeploymentTarget deployTarget;
    private CaseHubAppDriftChecker checker;

    @BeforeEach
    void setUp() {
        deployTarget = new StubDeploymentTarget();
        checker = new CaseHubAppDriftChecker(deployTarget);
    }

    @Test
    void nodeTypeIsCasehubApplication() {
        assertThat(checker.nodeType()).isEqualTo("casehub_application");
    }

    @Test
    void absentWhenNotDeployed() {
        var spec = new CaseHubApplicationSpec(
            "app-1", null, "img:1.0",
            new RuntimeConfig("0.2", List.of()),
            Map.of(), List.of(), 1);
        assertThat(checker.check(spec, "tenant-1")).isEqualTo(NodeStatus.ABSENT);
    }

    @Test
    void presentWhenDeployed() {
        var spec = new CaseHubApplicationSpec(
            "app-1", null, "img:1.0",
            new RuntimeConfig("0.2", List.of()),
            Map.of(), List.of(), 1);
        var config = new DeployConfig("app-1", 1, Map.of(), Map.of());
        deployTarget.deploy("img:1.0", config);
        assertThat(checker.check(spec, "tenant-1")).isEqualTo(NodeStatus.PRESENT);
    }

    @Test
    void unknownForNonApplicationSpec() {
        NodeSpec nonAppSpec = new NodeSpec() {
            @Override
            public io.casehub.desiredstate.api.NodeType nodeType() {
                return io.casehub.desiredstate.api.NodeType.of("other");
            }
        };
        assertThat(checker.check(nonAppSpec, "tenant-1")).isEqualTo(NodeStatus.UNKNOWN);
    }
}

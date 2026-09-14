package io.casehub.ops.app.lifecycle.ras;

import io.casehub.ops.api.lifecycle.DeploymentSummarisationEventTypes;
import io.casehub.ras.api.GanglionDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentTopologySituationDefinitionProviderTest {

    private DeploymentTopologySituationDefinitionProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DeploymentTopologySituationDefinitionProvider();
    }

    @Test
    void declaresThreeGanglia() {
        var ganglia = provider.ganglionDescriptors();
        assertThat(ganglia).hasSize(3);
        assertThat(ganglia).extracting("ganglionId")
                .containsExactlyInAnyOrder(
                        "deployment-degraded",
                        "deployment-recovering",
                        "deployment-healthy");
    }

    @Test
    void allGangliaConsumePhaseEvents() {
        for (var g : provider.ganglionDescriptors()) {
            assertThat(g).isInstanceOfSatisfying(
                    GanglionDescriptor.ExpressionRules.class,
                    er -> assertThat(er.handledEventTypes())
                            .contains(DeploymentSummarisationEventTypes.PHASE));
        }
    }

    @Test
    void declaresSituationRegistration() {
        var registrations = provider.registrations();
        assertThat(registrations).hasSize(1);
        assertThat(registrations.get(0).definition().situationId())
                .isEqualTo("deployment.sustained-degradation");
    }
}

package io.casehub.ops.service.case_;

import io.casehub.ops.api.lifecycle.OpsCloudEventTypes;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaseContextEventObserverTest {

    @Test
    void mapsProblemsIncidentToIncidentDetected() {
        assertThat(CaseContextEventObserver.contextKeyFor(OpsCloudEventTypes.PROBLEMS_INCIDENT))
                .isEqualTo(".incidentDetected");
    }

    @Test
    void mapsProblemsDriftToDriftDetected() {
        assertThat(CaseContextEventObserver.contextKeyFor(OpsCloudEventTypes.PROBLEMS_DRIFT))
                .isEqualTo(".driftDetected");
    }

    @Test
    void mapsSecurityCveToCveDetected() {
        assertThat(CaseContextEventObserver.contextKeyFor(OpsCloudEventTypes.SECURITY_CVE))
                .isEqualTo(".cveDetected");
    }

    @Test
    void mapsComplianceControlToComplianceViolation() {
        assertThat(CaseContextEventObserver.contextKeyFor(OpsCloudEventTypes.COMPLIANCE_CONTROL))
                .isEqualTo(".complianceViolation");
    }

    @Test
    void mapsChangeVersionToUpgradeRequested() {
        assertThat(CaseContextEventObserver.contextKeyFor(OpsCloudEventTypes.CHANGE_VERSION))
                .isEqualTo(".upgradeRequested");
    }

    @Test
    void mapsProblemsSCalingToScalingRequired() {
        assertThat(CaseContextEventObserver.contextKeyFor(OpsCloudEventTypes.PROBLEMS_SCALING))
                .isEqualTo(".scalingRequired");
    }

    @Test
    void unmappedEventTypeReturnsNull() {
        assertThat(CaseContextEventObserver.contextKeyFor(OpsCloudEventTypes.HEALTH_PROBE))
                .isNull();
    }

    @Test
    void parseApplicationIdExtractsUuid() {
        UUID appId = UUID.randomUUID();
        String data = "{\"applicationId\":\"" + appId + "\",\"severity\":\"HIGH\"}";
        assertThat(CaseContextEventObserver.parseApplicationId(data)).isEqualTo(appId);
    }

    @Test
    void parseApplicationIdReturnsNullForMissingField() {
        assertThat(CaseContextEventObserver.parseApplicationId("{\"foo\":\"bar\"}")).isNull();
    }

    @Test
    void parseApplicationIdReturnsNullForInvalidJson() {
        assertThat(CaseContextEventObserver.parseApplicationId("not json")).isNull();
    }
}

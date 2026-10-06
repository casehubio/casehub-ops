package io.casehub.ops.service.rest;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.StepAction;
import io.casehub.ops.api.approval.ApprovalPlan;
import io.casehub.ops.api.approval.PlanStore;
import io.casehub.ops.api.approval.RiskClassification;
import io.casehub.ops.api.infra.InfraDesiredNodeSpec;
import io.casehub.ops.api.infra.K8sNamespaceSpec;
import io.casehub.ops.api.infra.types.Labels;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.spi.WorkItemCreator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ApprovalResourceTest {

    private static final String TENANCY = "278776f9-e1b0-46fb-9032-8bddebdcf9ce";

    @Inject
    WorkItemCreator workItemCreator;

    @Inject
    PlanStore planStore;

    @Test
    void listApprovals_returnsEnrichedView() {
        var spec = new InfraDesiredNodeSpec(
                new K8sNamespaceSpec("prod-billing", Labels.empty()),
                "kubernetes:ops-prod");
        var plan = new ApprovalPlan(
                NodeId.of("ns-1"), StepAction.DEPROVISION, RiskClassification.CRITICAL,
                "Deprovision namespace 'prod-billing' on kubernetes:ops-prod",
                TENANCY, spec, null);
        String planRef = planStore.store(plan);

        workItemCreator.create(WorkItemCreateRequest.builder()
                .title("Approve deprovision: ns-1")
                .callerRef("desiredstate-approval:" + TENANCY + ":ns-1:DEPROVISION")
                .payload(planRef)
                .tenancyId(TENANCY)
                .types(java.util.List.of("desiredstate-approval"))
                .build());

        given()
                .when().get("/api/ops/approvals")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("find { it.nodeId == 'ns-1' }.action", equalTo("DEPROVISION"))
                .body("find { it.nodeId == 'ns-1' }.risk", equalTo("CRITICAL"))
                .body("find { it.nodeId == 'ns-1' }.cluster", equalTo("kubernetes:ops-prod"))
                .body("find { it.nodeId == 'ns-1' }.namespace", equalTo("prod-billing"));
    }

    @Test
    void approve_returns204() {
        var spec = new InfraDesiredNodeSpec(
                new K8sNamespaceSpec("prod", Labels.empty()),
                "kubernetes:ops-prod");
        var plan = new ApprovalPlan(
                NodeId.of("ns-approve"), StepAction.DEPROVISION, RiskClassification.LOW,
                "Deprovision namespace 'prod'",
                TENANCY, spec, null);
        String planRef = planStore.store(plan);

        var workItem = workItemCreator.create(WorkItemCreateRequest.builder()
                .title("Approve deprovision: ns-approve")
                .callerRef("desiredstate-approval:" + TENANCY + ":ns-approve:DEPROVISION")
                .payload(planRef)
                .tenancyId(TENANCY)
                .types(java.util.List.of("desiredstate-approval"))
                .build());

        given()
                .queryParam("actorId", "admin")
                .when().post("/api/ops/approvals/" + workItem.id() + "/approve")
                .then().statusCode(204);
    }

    @Test
    void approve_notFound_returns500() {
        given()
                .queryParam("actorId", "admin")
                .when().post("/api/ops/approvals/00000000-0000-0000-0000-000000000099/approve")
                .then().statusCode(500);
    }

    @Test
    void reject_returns204() {
        var workItem = workItemCreator.create(WorkItemCreateRequest.builder()
                .title("Test rejection")
                .callerRef("desiredstate-approval:" + TENANCY + ":node-rej:PROVISION")
                .tenancyId(TENANCY)
                .types(java.util.List.of("desiredstate-approval"))
                .build());

        given()
                .queryParam("actorId", "admin")
                .queryParam("reason", "too risky")
                .when().post("/api/ops/approvals/" + workItem.id() + "/reject")
                .then().statusCode(204);
    }

    @Test
    void listApprovals_degradedView_whenPlanMissing() {
        workItemCreator.create(WorkItemCreateRequest.builder()
                .title("Approve deprovision: ns-degraded")
                .callerRef("desiredstate-approval:" + TENANCY + ":ns-degraded:DEPROVISION")
                .payload("nonexistent-plan-ref")
                .tenancyId(TENANCY)
                .types(java.util.List.of("desiredstate-approval"))
                .build());

        given()
                .when().get("/api/ops/approvals")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("find { it.summary == 'Approve deprovision: ns-degraded' }.nodeId", nullValue())
                .body("find { it.summary == 'Approve deprovision: ns-degraded' }.summary", equalTo("Approve deprovision: ns-degraded"));
    }
}

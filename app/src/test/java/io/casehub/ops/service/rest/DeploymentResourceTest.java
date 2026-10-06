package io.casehub.ops.service.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class DeploymentResourceTest {

    @Test
    void listDeploymentsForApplication() {
        String appId = given()
                .contentType("application/json")
                .body("""
                    {"name": "deploy-app", "description": "test", "servicesJson": "[]"}
                    """)
                .when().post("/api/ops/applications")
                .then().statusCode(200)
                .extract().path("id");

        given()
                .when().get("/api/ops/deployments/" + appId)
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(0));
    }
}

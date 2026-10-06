package io.casehub.ops.service.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ApplicationResourceTest {

    @Test
    void createAndListApplication() {
        given()
                .contentType("application/json")
                .header("X-Tenancy-ID", "test-tenant")
                .body("""
                    {"name": "test-app", "description": "test", "servicesJson": "[]"}
                    """)
                .when().post("/api/ops/applications")
                .then().statusCode(200)
                .body("name", equalTo("test-app"))
                .body("id", notNullValue());

        given()
                .header("X-Tenancy-ID", "test-tenant")
                .when().get("/api/ops/applications")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void returns404ForMissingApplication() {
        given()
                .when().get("/api/ops/applications/00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
    }

    @Test
    void deleteApplication() {
        String id = given()
                .contentType("application/json")
                .header("X-Tenancy-ID", "test-tenant-delete")
                .body("""
                    {"name": "delete-app", "description": "test", "servicesJson": "[]"}
                    """)
                .when().post("/api/ops/applications")
                .then().statusCode(200)
                .extract().path("id");

        given()
                .header("X-Tenancy-ID", "test-tenant-delete")
                .when().post("/api/ops/applications/" + id + "/delete")
                .then().statusCode(204);
    }

    @Test
    void getApplicationById() {
        String id = given()
                .contentType("application/json")
                .header("X-Tenancy-ID", "test-tenant-get")
                .body("""
                    {"name": "get-app", "description": "test", "servicesJson": "[]"}
                    """)
                .when().post("/api/ops/applications")
                .then().statusCode(200)
                .extract().path("id");

        given()
                .when().get("/api/ops/applications/" + id)
                .then().statusCode(200)
                .body("name", equalTo("get-app"));
    }
}

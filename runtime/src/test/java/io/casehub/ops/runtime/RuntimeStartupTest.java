package io.casehub.ops.runtime;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class RuntimeStartupTest {

    @Test
    void createApplication_persistsAndReturns() {
        given()
            .contentType("application/json")
            .header("X-Tenancy-ID", "runtime-test")
            .body("""
                {"name": "runtime-smoke", "description": "verify runtime serves real requests", "servicesJson": "[]"}
                """)
            .when().post("/api/ops/applications")
            .then().statusCode(200)
            .body("name", equalTo("runtime-smoke"))
            .body("id", notNullValue());
    }

    @Test
    void listApplications_returnsFromDatabase() {
        given()
            .contentType("application/json")
            .header("X-Tenancy-ID", "runtime-list-test")
            .body("""
                {"name": "list-check", "description": "seed data", "servicesJson": "[]"}
                """)
            .when().post("/api/ops/applications")
            .then().statusCode(200);

        given()
            .header("X-Tenancy-ID", "runtime-list-test")
            .when().get("/api/ops/applications")
            .then().statusCode(200)
            .body("size()", greaterThanOrEqualTo(1));
    }
}

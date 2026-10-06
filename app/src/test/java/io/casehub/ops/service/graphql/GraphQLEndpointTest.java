package io.casehub.ops.service.graphql;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class GraphQLEndpointTest {

    @Test
    void listApplicationsViaGraphQL() {
        given()
                .contentType("application/json")
                .header("X-Tenancy-ID", "graphql-test")
                .body("""
                    {"name": "gql-app", "description": "graphql test", "servicesJson": "[]"}
                    """)
                .when().post("/api/ops/applications")
                .then().statusCode(200);

        given()
                .contentType("application/json")
                .body("""
                    {"query": "{ listApplications { id name description } }"}
                    """)
                .when().post("/graphql")
                .then().statusCode(200)
                .body("data.listApplications", is(notNullValue()))
                .body("data.listApplications.size()", greaterThanOrEqualTo(1))
                .body("data.listApplications[0].name", is(notNullValue()))
                .body("errors", nullValue());
    }

    @Test
    void graphqlSchemaAvailable() {
        given()
                .when().get("/graphql/schema.graphql")
                .then().statusCode(200)
                .body(containsString("listApplications"));
    }
}

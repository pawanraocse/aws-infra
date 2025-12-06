package com.learning.systemtests.auth;

import com.learning.systemtests.BaseSystemTest;
import com.learning.systemtests.util.AuthHelper;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class TenantIsolationIT extends BaseSystemTest {

        @Test
        void shouldCreateAndRetrieveEntryInSameTenant() {
                // 1. Signup and Login (Default Tenant)
                AuthHelper.UserCredentials creds = AuthHelper.signup();
                String token = AuthHelper.login(creds.email(), creds.password());

                // 2. Create Entry
                String entryJson = """
                                {
                                    "title": "Isolation Test Entry",
                                    "content": "Secret Content"
                                }
                                """;

                String entryId = given()
                                .contentType(ContentType.JSON)
                                .header("Authorization", "Bearer " + token)
                                .body(entryJson)
                                .when()
                                .post("/api/v1/entries")
                                .then()
                                .statusCode(201)
                                .extract()
                                .path("id");

                // 3. Retrieve Entry (Same Tenant) -> Should Succeed
                given()
                                .header("Authorization", "Bearer " + token)
                                .when()
                                .get("/api/v1/entries/" + entryId)
                                .then()
                                .statusCode(200)
                                .body("id", equalTo(entryId));
        }

        @Test
        void shouldFailToAccessEntryWithDifferentTenantHeader() {
                // 1. Signup and Login (Default Tenant)
                AuthHelper.UserCredentials creds = AuthHelper.signup();
                String token = AuthHelper.login(creds.email(), creds.password());

                // 2. Create Entry
                String entryJson = """
                                {
                                    "title": "Tenant A Entry",
                                    "content": "Secret Content"
                                }
                                """;

                String entryId = given()
                                .contentType(ContentType.JSON)
                                .header("Authorization", "Bearer " + token)
                                .body(entryJson)
                                .when()
                                .post("/api/v1/entries")
                                .then()
                                .statusCode(201)
                                .extract()
                                .path("id");

                // 3. Try to access with DIFFERENT Tenant Header
                // This forces the backend to look in a different (likely non-existent) database
                // or at least a different tenant context where this ID doesn't exist.

                String fakeTenantId = "t_non_existent_" + UUID.randomUUID();

                given()
                                .header("Authorization", "Bearer " + token)
                                .header("X-Tenant-Id", fakeTenantId) // Override tenant context
                                .when()
                                .get("/api/v1/entries/" + entryId)
                                .then()
                                // Should be 500 (DB connection fail) or 404 (Not found in that DB)
                                // We accept either as proof it didn't find the record from the original tenant
                                .statusCode(org.hamcrest.Matchers.isOneOf(404, 500, 403));
        }

        @Test
        void shouldFailAccessWithoutAuthentication() {
                // Try to access a protected resource without token
                given()
                                .when()
                                .get("/api/v1/entries/some-id")
                                .then()
                                .statusCode(401);
        }
}

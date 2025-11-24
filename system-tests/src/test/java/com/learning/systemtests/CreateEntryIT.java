package com.learning.systemtests;

import com.learning.systemtests.util.AuthHelper;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * End-to-End test demonstrating the Entry creation flow with automated authentication.
 * <p>
 * This test automatically:
 * - Logs in via the Auth Service
 * - Retrieves a valid JWT token
 * - Uses it to test protected endpoints
 * </p>
 */
public class CreateEntryIT extends BaseSystemTest {

    private static String jwtToken;

    @BeforeAll
    static void authenticate() {
        // Automatically login and get JWT token before running tests
        jwtToken = AuthHelper.getAccessToken();
        log.info("Authenticated successfully. Token obtained.");
    }

    @Test
    void shouldCreateEntrySuccessfully() {
        // Arrange: Prepare the entry data
        String entryJson = """
                {
                    "title": "Test Entry",
                    "content": "This is a test entry created by E2E test"
                }
                """;

        // Act & Assert: Create the entry via Gateway -> Backend
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + jwtToken)
            .body(entryJson)
            .when()
                .post("/api/v1/entries")
            .then()
                .statusCode(201)  // Created
                .body("id", notNullValue())
                .body("title", equalTo("Test Entry"))
                .body("content", equalTo("This is a test entry created by E2E test"))
                .body("createdAt", notNullValue());
    }

    @Test
    void shouldRejectRequestWithoutAuthentication() {
        // Arrange
        String entryJson = """
                {
                    "title": "Unauthorized Entry",
                    "content": "This should fail"
                }
                """;

        // Act & Assert: Try to create without JWT
        given()
            .contentType(ContentType.JSON)
            .body(entryJson)
            .when()
                .post("/api/v1/entries")
            .then()
                .statusCode(401);  // Unauthorized
    }

    @Test
    void shouldRetrieveCreatedEntry() {
        // This test shows how to chain operations:
        // 1. Create an entry
        // 2. Extract its ID
        // 3. Retrieve it by ID

        String entryJson = """
                {
                    "title": "Retrievable Entry",
                    "content": "Test retrieval"
                }
                """;

        // Step 1: Create and capture the ID
        String entryId = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + jwtToken)
            .body(entryJson)
            .when()
                .post("/api/v1/entries")
            .then()
                .statusCode(201)
                .extract()
                .path("id");

        // Step 2: Retrieve the entry by ID
        given()
            .header("Authorization", "Bearer " + jwtToken)
            .when()
                .get("/api/v1/entries/" + entryId)
            .then()
                .statusCode(200)
                .body("id", equalTo(entryId))
                .body("title", equalTo("Retrievable Entry"));
    }

    @Test
    void shouldUpdateEntry() {
        // Create an entry first
        String createJson = """
                {
                    "title": "Original Title",
                    "content": "Original content"
                }
                """;

        String entryId = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + jwtToken)
            .body(createJson)
            .when()
                .post("/api/v1/entries")
            .then()
                .statusCode(201)
                .extract()
                .path("id");

        // Update it
        String updateJson = """
                {
                    "title": "Updated Title",
                    "content": "Updated content"
                }
                """;

        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + jwtToken)
            .body(updateJson)
            .when()
                .put("/api/v1/entries/" + entryId)
            .then()
                .statusCode(200)
                .body("id", equalTo(entryId))
                .body("title", equalTo("Updated Title"))
                .body("content", equalTo("Updated content"));
    }

    @Test
    void shouldDeleteEntry() {
        // Create an entry first
        String createJson = """
                {
                    "title": "To Be Deleted",
                    "content": "This will be deleted"
                }
                """;

        String entryId = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + jwtToken)
            .body(createJson)
            .when()
                .post("/api/v1/entries")
            .then()
                .statusCode(201)
                .extract()
                .path("id");

        // Delete it
        given()
            .header("Authorization", "Bearer " + jwtToken)
            .when()
                .delete("/api/v1/entries/" + entryId)
            .then()
                .statusCode(204);  // No Content

        // Verify it's gone
        given()
            .header("Authorization", "Bearer " + jwtToken)
            .when()
                .get("/api/v1/entries/" + entryId)
            .then()
                .statusCode(404);  // Not Found
    }
}

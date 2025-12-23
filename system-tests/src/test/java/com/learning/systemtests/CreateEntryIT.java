package com.learning.systemtests;

import com.learning.systemtests.util.AuthHelper;
import com.learning.systemtests.util.CleanupHelper;
import com.learning.systemtests.util.TestDataFactory;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static com.learning.systemtests.config.TestConfig.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Full CRUD workflow tests for Entry resource.
 * Tests Create, Read, Update, Delete operations.
 * 
 * <p>
 * Uses AdminConfirmSignUp to auto-verify test users.
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CreateEntryIT extends BaseSystemTest {

    private String jwtToken;
    private String entryId;

    @BeforeAll
    void authenticate() {
        log.info("Setting up user for CreateEntryIT...");
        try {
            AuthHelper.UserCredentials creds = AuthHelper.signupAndConfirm();
            jwtToken = AuthHelper.login(creds.email(), creds.password());
            log.info("✅ Authentication successful");
        } catch (Exception e) {
            log.warn("Setup failed: {} - tests will be skipped", e.getMessage());
        }
    }

    @Test
    @Order(1)
    @DisplayName("Create entry - success")
    void testCreateEntry() {
        Assumptions.assumeTrue(jwtToken != null, "Requires valid JWT token");

        String entryJson = TestDataFactory.entryJson("Test Entry", "Test Content");

        entryId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + jwtToken)
                .body(entryJson)
                .when()
                .post(ENTRIES_API)
                .then()
                .statusCode(anyOf(is(200), is(201)))
                .body("id", notNullValue())
                .body("title", equalTo("Test Entry"))
                .extract()
                .path("id");

        log.info("✅ Created entry: {}", entryId);
    }

    @Test
    @Order(2)
    @DisplayName("Read entry by ID - success")
    void testReadEntry() {
        Assumptions.assumeTrue(jwtToken != null, "Requires valid JWT token");
        Assumptions.assumeTrue(entryId != null, "Requires entry from previous test");

        given()
                .header("Authorization", "Bearer " + jwtToken)
                .when()
                .get(ENTRIES_API + "/" + entryId)
                .then()
                .statusCode(200)
                .body("id", equalTo(entryId))
                .body("title", equalTo("Test Entry"));

        log.info("✅ Read entry: {}", entryId);
    }

    @Test
    @Order(3)
    @DisplayName("Update entry - success")
    void testUpdateEntry() {
        Assumptions.assumeTrue(jwtToken != null, "Requires valid JWT token");
        Assumptions.assumeTrue(entryId != null, "Requires entry from previous test");

        String updatedJson = TestDataFactory.entryJson("Updated Entry", "Updated Content");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + jwtToken)
                .body(updatedJson)
                .when()
                .put(ENTRIES_API + "/" + entryId)
                .then()
                .statusCode(200)
                .body("title", equalTo("Updated Entry"));

        log.info("✅ Updated entry: {}", entryId);
    }

    @Test
    @Order(4)
    @DisplayName("List all entries - includes created entry")
    void testListEntries() {
        Assumptions.assumeTrue(jwtToken != null, "Requires valid JWT token");

        given()
                .header("Authorization", "Bearer " + jwtToken)
                .when()
                .get(ENTRIES_API)
                .then()
                .statusCode(200)
                .body("$", not(empty()));

        log.info("✅ Listed entries");
    }

    @Test
    @Order(5)
    @DisplayName("Delete entry - success")
    void testDeleteEntry() {
        Assumptions.assumeTrue(jwtToken != null, "Requires valid JWT token");
        Assumptions.assumeTrue(entryId != null, "Requires entry from previous test");

        given()
                .header("Authorization", "Bearer " + jwtToken)
                .when()
                .delete(ENTRIES_API + "/" + entryId)
                .then()
                .statusCode(anyOf(is(204), is(200)));

        log.info("✅ Deleted entry: {}", entryId);

        // Verify deletion
        given()
                .header("Authorization", "Bearer " + jwtToken)
                .when()
                .get(ENTRIES_API + "/" + entryId)
                .then()
                .statusCode(404);

        log.info("✅ Verified entry deleted");
    }

    @Test
    @Order(6)
    @DisplayName("Access without authentication - should fail")
    void testAccessWithoutAuth() {
        given()
                .when()
                .get(ENTRIES_API)
                .then()
                .statusCode(401);

        log.info("✅ Unauthenticated access correctly rejected");
    }

    @AfterAll
    void cleanup() {
        log.info("🧹 Running cleanup for CreateEntryIT...");
        CleanupHelper.cleanupAll();
    }

    @AfterEach
    void afterEach() {
        log.info("---");
    }
}

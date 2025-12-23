package com.learning.systemtests;

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
 * <b>REQUIRES:</b> Verified Cognito user. Currently disabled because
 * Cognito creates UNCONFIRMED users that cannot log in.
 * </p>
 * 
 * <p>
 * To enable: Either implement Cognito AdminConfirmSignUp or use
 * pre-verified test credentials via TEST_USER_EMAIL/TEST_USER_PASSWORD.
 * </p>
 */
@Disabled("Requires verified Cognito user - see class javadoc")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CreateEntryIT extends BaseSystemTest {

    private String jwtToken;
    private String entryId;

    @BeforeAll
    void authenticate() {
        // TODO: Use pre-verified test user or implement AdminConfirmSignUp
        // AuthHelper.UserCredentials creds = AuthHelper.signup();
        // jwtToken = AuthHelper.login(creds.email(), creds.password());
        jwtToken = System.getenv().getOrDefault("TEST_JWT_TOKEN", "");
        log.info("Using pre-configured JWT token for entry tests");
    }

    @Test
    @Order(1)
    @DisplayName("Create entry - success")
    void testCreateEntry() {
        Assumptions.assumeTrue(!jwtToken.isEmpty(), "Requires valid JWT token");

        String entryJson = TestDataFactory.entryJson("Test Entry", "Test Content");

        entryId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + jwtToken)
                .body(entryJson)
                .when()
                .post(ENTRIES_API)
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("title", equalTo("Test Entry"))
                .body("content", equalTo("Test Content"))
                .extract()
                .path("id");

        log.info("✅ Created entry: {}", entryId);
    }

    @Test
    @Order(2)
    @DisplayName("Read entry by ID - success")
    void testReadEntry() {
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
                .body("title", equalTo("Updated Entry"))
                .body("content", equalTo("Updated Content"));

        log.info("✅ Updated entry: {}", entryId);
    }

    @Test
    @Order(4)
    @DisplayName("List all entries - includes created entry")
    void testListEntries() {
        Assumptions.assumeTrue(entryId != null, "Requires entry from previous test");

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

    @AfterEach
    void afterEach() {
        log.info("---");
    }
}

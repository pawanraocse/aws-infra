package com.learning.systemtests.auth;

import com.learning.systemtests.BaseSystemTest;
import org.junit.jupiter.api.*;

/**
 * Integration tests for the Delete Account flow.
 * 
 * <p>
 * <b>REQUIRES:</b> Verified Cognito user. Currently disabled.
 * </p>
 */
@Disabled("Requires verified Cognito user")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeleteAccountFlowIT extends BaseSystemTest {

    @Test
    @Order(1)
    @DisplayName("Delete account without confirmation - returns 400")
    void testDeleteAccountWithoutConfirmation() {
        log.info("Test requires verified Cognito user - skipped");
    }

    @Test
    @Order(2)
    @DisplayName("Delete account with wrong confirmation - returns 400")
    void testDeleteAccountWrongConfirmation() {
        log.info("Test requires verified Cognito user - skipped");
    }

    @Test
    @Order(3)
    @DisplayName("Delete account with proper confirmation - success")
    void testDeleteAccountSuccess() {
        log.info("Test requires verified Cognito user - skipped");
    }

    @AfterEach
    void afterEach() {
        log.info("---");
    }
}

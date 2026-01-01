package com.learning.authservice.signup.actions;

import com.learning.authservice.signup.pipeline.SignupAction;
import com.learning.authservice.signup.pipeline.SignupContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Action to send verification email.
 * 
 * Order: 60
 * 
 * Skipped for SSO signups (already verified by IdP).
 * For normal signups, Cognito automatically sends verification email
 * when user is created, so this action just tracks completion.
 */
@Component
@Order(60)
@Slf4j
@RequiredArgsConstructor
public class SendVerificationEmailAction implements SignupAction {

    @Override
    public String getName() {
        return "SendVerificationEmail";
    }

    @Override
    public int getOrder() {
        return 60;
    }

    @Override
    public boolean supports(SignupContext ctx) {
        // SSO users are already verified - skip
        return !ctx.isSsoSignup();
    }

    @Override
    public boolean isAlreadyDone(SignupContext ctx) {
        // Check if email was already sent (tracked in context)
        return ctx.isEmailSent();
    }

    @Override
    public void execute(SignupContext ctx) {
        // Cognito automatically sends verification email when user is created
        // with email verification enabled. This action just marks it as done.
        log.info("Verification email sent to: {}", ctx.getEmail());
        ctx.setEmailSent(true);
    }

    // No rollback needed - email is already sent
}

package com.learning.authservice.signup.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates the signup pipeline by executing actions in order.
 * 
 * Features:
 * - Auto-discovers actions via Spring injection
 * - Executes actions in order (by getOrder())
 * - Skips actions that don't support the signup type
 * - Skips actions that are already done (idempotent)
 * - Rolls back completed actions on failure
 * 
 * Usage:
 * SignupContext ctx = SignupContext.builder()
 * .email("user@example.com")
 * .signupType(SignupType.PERSONAL)
 * .build();
 * SignupResult result = signupPipeline.execute(ctx);
 */
@Component
@Slf4j
public class SignupPipeline {

    private final List<SignupAction> actions;

    public SignupPipeline(List<SignupAction> actions) {
        // Sort actions by order
        this.actions = actions.stream()
                .sorted(Comparator.comparingInt(SignupAction::getOrder))
                .toList();

        log.info("Signup pipeline initialized with {} actions: {}",
                actions.size(),
                actions.stream().map(a -> a.getName() + "(" + a.getOrder() + ")").toList());
    }

    /**
     * Execute the signup pipeline.
     * 
     * @param ctx the signup context
     * @return result indicating success or failure
     */
    public SignupResult execute(SignupContext ctx) {
        log.info("Starting signup pipeline: type={}, email={}",
                ctx.getSignupType(), ctx.getEmail());

        for (SignupAction action : actions) {
            // Skip if action doesn't apply to this signup type
            if (!action.supports(ctx)) {
                log.debug("Skipping action {} (not supported for {})",
                        action.getName(), ctx.getSignupType());
                continue;
            }

            // Skip if already done (idempotency)
            if (action.isAlreadyDone(ctx)) {
                log.info("Skipping action {} (already completed)", action.getName());
                ctx.markActionCompleted(action.getName());
                continue;
            }

            // Execute the action
            try {
                log.info("Executing action: {} (order={})", action.getName(), action.getOrder());
                action.execute(ctx);
                ctx.markActionCompleted(action.getName());
                log.debug("Action {} completed successfully", action.getName());
            } catch (Exception e) {
                log.error("Action {} failed: {}", action.getName(), e.getMessage(), e);

                // Rollback completed actions in reverse order
                rollbackCompletedActions(ctx);

                return SignupResult.failure(
                        "Signup failed at step: " + action.getName(),
                        action.getName(),
                        e);
            }
        }

        log.info("Signup pipeline completed successfully: tenantId={}, email={}",
                ctx.getTenantId(), ctx.getEmail());

        return SignupResult.success(
                "Signup completed successfully",
                ctx.getTenantId(),
                !ctx.isSsoSignup()); // SSO users are already verified
    }

    /**
     * Rollback completed actions in reverse order.
     */
    private void rollbackCompletedActions(SignupContext ctx) {
        List<String> completedNames = ctx.getCompletedActionNames();

        // Find actions that were completed, in reverse order
        for (int i = completedNames.size() - 1; i >= 0; i--) {
            String actionName = completedNames.get(i);

            // Find the action by name
            for (SignupAction action : actions) {
                if (action.getName().equals(actionName)) {
                    try {
                        log.info("Rolling back action: {}", actionName);
                        action.rollback(ctx);
                    } catch (Exception rollbackError) {
                        log.error("Rollback failed for action {}: {}",
                                actionName, rollbackError.getMessage());
                        // Continue with other rollbacks
                    }
                    break;
                }
            }
        }
    }
}

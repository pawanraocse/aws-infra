package com.learning.authservice.signup.pipeline;

/**
 * Interface for signup pipeline actions.
 * 
 * Each action represents a discrete step in the signup process.
 * Actions are:
 * - Ordered: executed in sequence by order value
 * - Conditional: can be skipped based on signup type
 * - Idempotent: check if already done before executing
 * - Rollback-capable: can undo changes on failure
 * 
 * SOLID Principles:
 * - Single Responsibility: each action does one thing
 * - Open/Closed: new actions can be added without modifying pipeline
 * - Interface Segregation: clean, focused interface
 */
public interface SignupAction {

    /**
     * Unique name for this action (for logging and tracking).
     */
    String getName();

    /**
     * Order in which this action should execute.
     * Lower values execute first.
     * 
     * Recommended ranges:
     * - 10-19: Tenant ID generation
     * - 20-29: Tenant provisioning
     * - 30-39: Cognito user creation
     * - 40-49: Membership creation
     * - 50-59: Role assignment
     * - 60-69: Email/notifications
     * - 70+: Post-signup setup
     */
    int getOrder();

    /**
     * Check if this action should run for the given context.
     * 
     * @param ctx the signup context
     * @return true if action should execute, false to skip
     */
    boolean supports(SignupContext ctx);

    /**
     * Check if this action has already been completed (idempotency).
     * 
     * Called before execute() to support retry scenarios.
     * If returns true, the action is skipped (not executed again).
     * 
     * @param ctx the signup context
     * @return true if action was already done, false to execute
     */
    boolean isAlreadyDone(SignupContext ctx);

    /**
     * Execute the action.
     * 
     * @param ctx the signup context (may be modified)
     * @throws SignupActionException if action fails
     */
    void execute(SignupContext ctx) throws SignupActionException;

    /**
     * Rollback changes made by this action.
     * 
     * Called if a later action fails. Should be idempotent
     * (safe to call multiple times or if execute() was not called).
     * 
     * @param ctx the signup context
     */
    default void rollback(SignupContext ctx) {
        // Default: no-op. Override for reversible actions.
    }
}

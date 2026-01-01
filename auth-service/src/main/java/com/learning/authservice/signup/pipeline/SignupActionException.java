package com.learning.authservice.signup.pipeline;

/**
 * Exception thrown when a signup action fails.
 */
public class SignupActionException extends RuntimeException {

    private final String actionName;
    private final boolean retriable;

    public SignupActionException(String actionName, String message) {
        super(message);
        this.actionName = actionName;
        this.retriable = true;
    }

    public SignupActionException(String actionName, String message, Throwable cause) {
        super(message, cause);
        this.actionName = actionName;
        this.retriable = true;
    }

    public SignupActionException(String actionName, String message, boolean retriable) {
        super(message);
        this.actionName = actionName;
        this.retriable = retriable;
    }

    public String getActionName() {
        return actionName;
    }

    public boolean isRetriable() {
        return retriable;
    }
}

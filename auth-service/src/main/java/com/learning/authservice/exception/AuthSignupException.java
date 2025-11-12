package com.learning.authservice.exception;

public class AuthSignupException extends RuntimeException {
    private final String code;

    public AuthSignupException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AuthSignupException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}



package com.learning.authservice.exception;

public class AuthLoginException extends RuntimeException {
    private final String code;

    public AuthLoginException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AuthLoginException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

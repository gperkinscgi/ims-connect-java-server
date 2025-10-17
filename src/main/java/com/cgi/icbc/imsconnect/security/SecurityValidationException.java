package com.cgi.icbc.imsconnect.security;

/**
 * Exception thrown when security validation fails.
 */
public class SecurityValidationException extends RuntimeException {

    public SecurityValidationException(String message) {
        super(message);
    }

    public SecurityValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public SecurityValidationException(Throwable cause) {
        super(cause);
    }
}
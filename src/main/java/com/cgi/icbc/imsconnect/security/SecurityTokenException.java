package com.cgi.icbc.imsconnect.security;

/**
 * Exception thrown when security token parsing or validation fails.
 */
public class SecurityTokenException extends RuntimeException {

    public SecurityTokenException(String message) {
        super(message);
    }

    public SecurityTokenException(String message, Throwable cause) {
        super(message, cause);
    }

    public SecurityTokenException(Throwable cause) {
        super(cause);
    }
}
package com.cgi.icbc.imsconnect.protocol;

/**
 * Exception thrown when IMS Connect protocol violations are detected.
 */
public class ProtocolException extends Exception {

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
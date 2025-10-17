package com.cgi.icbc.imsconnect.transaction;

/**
 * Exception thrown when IMS transaction operations fail.
 */
public class IMSTransactionException extends RuntimeException {

    public IMSTransactionException(String message) {
        super(message);
    }

    public IMSTransactionException(String message, Throwable cause) {
        super(message, cause);
    }

    public IMSTransactionException(Throwable cause) {
        super(cause);
    }
}
package com.cgi.icbc.imsconnect.transaction;

/**
 * Exception thrown when an IMS transaction is not found.
 */
public class IMSTransactionNotFoundException extends IMSTransactionException {

    public IMSTransactionNotFoundException(String message) {
        super(message);
    }

    public IMSTransactionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
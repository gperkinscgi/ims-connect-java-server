package com.cgi.icbc.imsconnect.transaction;

import com.cgi.icbc.imsconnect.protocol.OTMAMessage;

/**
 * Result of an IMS transaction processing operation.
 */
public class IMSTransactionResult {

    private final boolean success;
    private final IMSTransactionState transaction;
    private final OTMAMessage response;
    private final Throwable error;

    private IMSTransactionResult(boolean success, IMSTransactionState transaction, OTMAMessage response, Throwable error) {
        this.success = success;
        this.transaction = transaction;
        this.response = response;
        this.error = error;
    }

    // Getters
    public boolean isSuccess() { return success; }
    public IMSTransactionState getTransaction() { return transaction; }
    public OTMAMessage getResponse() { return response; }
    public Throwable getError() { return error; }

    // Factory methods
    public static IMSTransactionResult success(IMSTransactionState transaction, OTMAMessage response) {
        return new IMSTransactionResult(true, transaction, response, null);
    }

    public static IMSTransactionResult failure(IMSTransactionState transaction, Throwable error) {
        return new IMSTransactionResult(false, transaction, null, error);
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("IMSTransactionResult{success=true, txnId='%s'}",
                    transaction != null ? transaction.getTransactionId() : "unknown");
        } else {
            return String.format("IMSTransactionResult{success=false, txnId='%s', error='%s'}",
                    transaction != null ? transaction.getTransactionId() : "unknown",
                    error != null ? error.getMessage() : "unknown");
        }
    }
}
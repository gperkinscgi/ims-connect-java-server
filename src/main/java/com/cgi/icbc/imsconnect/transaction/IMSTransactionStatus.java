package com.cgi.icbc.imsconnect.transaction;

/**
 * Status of an IMS transaction.
 */
public enum IMSTransactionStatus {

    /**
     * Transaction has been started and is being processed.
     */
    STARTED,

    /**
     * Transaction completed successfully.
     */
    COMPLETED,

    /**
     * Transaction was aborted due to an error.
     */
    ABORTED
}
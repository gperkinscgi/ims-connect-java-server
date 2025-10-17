package com.cgi.icbc.imsconnect.model;

/**
 * Enumeration of IMS Connect transaction types.
 */
public enum TransactionType {

    /**
     * Synchronous send-receive transaction.
     * Client sends a request and waits for a response.
     */
    SEND_RECEIVE,

    /**
     * Asynchronous send-only transaction.
     * Client sends a message without waiting for a response.
     */
    SEND_ONLY,

    /**
     * Receive-only operation.
     * Client polls for queued messages.
     */
    RECV_ONLY,

    /**
     * Acknowledgment message.
     */
    ACK,

    /**
     * Negative acknowledgment message.
     */
    NAK,

    /**
     * Resume tpipe operation.
     */
    RESUME_TPIPE,

    /**
     * Deallocate conversation.
     */
    DEALLOCATE,

    /**
     * Cancel timer operation.
     */
    CANCEL_TIMER,

    /**
     * Unknown or unsupported transaction type.
     */
    UNKNOWN
}
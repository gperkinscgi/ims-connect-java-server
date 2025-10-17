package com.cgi.icbc.imsconnect.transaction;

/**
 * Types of IMS messages.
 */
public enum IMSMessageType {

    /**
     * Standard IMS transaction.
     */
    TRANSACTION,

    /**
     * Conversational transaction (multi-message).
     */
    CONVERSATIONAL,

    /**
     * Response message.
     */
    RESPONSE,

    /**
     * IMS command.
     */
    COMMAND,

    /**
     * Status message.
     */
    STATUS
}
package com.cgi.icbc.imsconnect.server;

import com.cgi.icbc.imsconnect.protocol.OTMAMessage;

/**
 * Interface for handlers that process OTMA messages.
 * Extends the basic transaction handler to support OTMA-specific features.
 */
public interface OTMATransactionHandler {

    /**
     * Check if this handler can process the given OTMA message.
     */
    boolean canHandle(OTMAMessage message);

    /**
     * Process an OTMA message and return a response.
     */
    OTMAMessage handleOTMATransaction(OTMAMessage request);

    /**
     * Get the priority of this handler (higher values = higher priority).
     * Used when multiple handlers can process the same message type.
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Check if this handler supports conversational transactions.
     */
    default boolean supportsConversational() {
        return false;
    }

    /**
     * Check if this handler requires security validation.
     */
    default boolean requiresSecurityValidation() {
        return true;
    }

    /**
     * Get supported transaction codes for this handler.
     */
    default String[] getSupportedTransactionCodes() {
        return new String[0];
    }

    /**
     * Get supported LTERM names for this handler.
     */
    default String[] getSupportedLtermNames() {
        return new String[0]; // Empty means all LTERMs supported
    }
}
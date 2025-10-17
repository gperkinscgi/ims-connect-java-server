package com.cgi.icbc.imsconnect.conversation;

/**
 * Status of a conversational transaction.
 */
public enum ConversationStatus {

    /**
     * Conversation is active and can receive messages.
     */
    ACTIVE,

    /**
     * Conversation completed successfully.
     */
    COMPLETED,

    /**
     * Conversation was aborted due to an error.
     */
    ABORTED,

    /**
     * Conversation expired due to timeout.
     */
    EXPIRED,

    /**
     * Conversation is waiting for a response.
     */
    WAITING,

    /**
     * Conversation is suspended (temporarily inactive).
     */
    SUSPENDED
}
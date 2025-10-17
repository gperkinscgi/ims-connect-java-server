package com.cgi.icbc.imsconnect.conversation;

/**
 * Exception thrown when a conversation is not found.
 */
public class ConversationNotFoundException extends ConversationException {

    public ConversationNotFoundException(String message) {
        super(message);
    }

    public ConversationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
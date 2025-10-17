package com.cgi.icbc.imsconnect.conversation;

/**
 * Exception thrown when conversation management operations fail.
 */
public class ConversationException extends RuntimeException {

    public ConversationException(String message) {
        super(message);
    }

    public ConversationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConversationException(Throwable cause) {
        super(cause);
    }
}
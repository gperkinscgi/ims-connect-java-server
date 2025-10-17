package com.cgi.icbc.imsconnect.conversation;

import com.cgi.icbc.imsconnect.protocol.OTMAMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents the state of a conversational transaction.
 */
public class ConversationState {

    private final int conversationId;
    private final String clientId;
    private final String ltermName;
    private final String transactionCode;
    private final Instant createdAt;

    private volatile ConversationStatus status;
    private volatile Instant lastActivity;
    private volatile Instant endedAt;
    private volatile String errorMessage;

    private final AtomicLong messageCount = new AtomicLong(0);
    private final List<OTMAMessage> messageHistory = new ArrayList<>();
    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();
    private volatile boolean keepHistory = false;

    private ConversationState(Builder builder) {
        this.conversationId = builder.conversationId;
        this.clientId = builder.clientId;
        this.ltermName = builder.ltermName;
        this.transactionCode = builder.transactionCode;
        this.createdAt = builder.createdAt;
        this.status = builder.status;
        this.lastActivity = builder.lastActivity;
        this.keepHistory = builder.keepHistory;
    }

    // Getters
    public int getConversationId() { return conversationId; }
    public String getClientId() { return clientId; }
    public String getLtermName() { return ltermName; }
    public String getTransactionCode() { return transactionCode; }
    public Instant getCreatedAt() { return createdAt; }
    public ConversationStatus getStatus() { return status; }
    public Instant getLastActivity() { return lastActivity; }
    public Instant getEndedAt() { return endedAt; }
    public String getErrorMessage() { return errorMessage; }
    public long getMessageCount() { return messageCount.get(); }
    public boolean isKeepHistory() { return keepHistory; }

    // Setters
    public void setStatus(ConversationStatus status) { this.status = status; }
    public void setLastActivity(Instant lastActivity) { this.lastActivity = lastActivity; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setKeepHistory(boolean keepHistory) { this.keepHistory = keepHistory; }

    // Message management
    public void incrementMessageCount() {
        messageCount.incrementAndGet();
    }

    public synchronized void addMessage(OTMAMessage message) {
        if (keepHistory && message != null) {
            messageHistory.add(message);

            // Limit history size to prevent memory issues
            if (messageHistory.size() > 100) {
                messageHistory.remove(0);
            }
        }
    }

    public synchronized List<OTMAMessage> getMessageHistory() {
        return new ArrayList<>(messageHistory);
    }

    public synchronized void clearMessageHistory() {
        messageHistory.clear();
    }

    // Attribute management
    public void setAttribute(String key, Object value) {
        if (value != null) {
            attributes.put(key, value);
        } else {
            attributes.remove(key);
        }
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    // Status checks
    public boolean isActive() {
        return status == ConversationStatus.ACTIVE;
    }

    public boolean isCompleted() {
        return status == ConversationStatus.COMPLETED;
    }

    public boolean isAborted() {
        return status == ConversationStatus.ABORTED;
    }

    public boolean isExpired() {
        return status == ConversationStatus.EXPIRED;
    }

    // Duration calculations
    public long getDurationSeconds() {
        Instant end = endedAt != null ? endedAt : Instant.now();
        return java.time.Duration.between(createdAt, end).getSeconds();
    }

    public long getIdleTimeSeconds() {
        return java.time.Duration.between(lastActivity, Instant.now()).getSeconds();
    }

    // Validation
    public boolean isValid() {
        return conversationId > 0 && clientId != null && ltermName != null &&
               transactionCode != null && createdAt != null && status != null;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int conversationId;
        private String clientId;
        private String ltermName;
        private String transactionCode;
        private Instant createdAt = Instant.now();
        private ConversationStatus status = ConversationStatus.ACTIVE;
        private Instant lastActivity = Instant.now();
        private boolean keepHistory = false;

        public Builder conversationId(int conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder ltermName(String ltermName) {
            this.ltermName = ltermName;
            return this;
        }

        public Builder transactionCode(String transactionCode) {
            this.transactionCode = transactionCode;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder status(ConversationStatus status) {
            this.status = status;
            return this;
        }

        public Builder lastActivity(Instant lastActivity) {
            this.lastActivity = lastActivity;
            return this;
        }

        public Builder keepHistory(boolean keepHistory) {
            this.keepHistory = keepHistory;
            return this;
        }

        public ConversationState build() {
            if (conversationId <= 0) {
                throw new IllegalArgumentException("Conversation ID must be positive");
            }
            if (clientId == null || clientId.trim().isEmpty()) {
                throw new IllegalArgumentException("Client ID is required");
            }
            if (ltermName == null || ltermName.trim().isEmpty()) {
                throw new IllegalArgumentException("LTERM name is required");
            }
            if (transactionCode == null || transactionCode.trim().isEmpty()) {
                throw new IllegalArgumentException("Transaction code is required");
            }

            return new ConversationState(this);
        }
    }

    @Override
    public String toString() {
        return String.format("ConversationState{id=%d, client='%s', lterm='%s', txn='%s', status=%s, msgs=%d, duration=%ds}",
                conversationId, clientId, ltermName, transactionCode, status, messageCount.get(), getDurationSeconds());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ConversationState that = (ConversationState) obj;
        return conversationId == that.conversationId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(conversationId);
    }
}
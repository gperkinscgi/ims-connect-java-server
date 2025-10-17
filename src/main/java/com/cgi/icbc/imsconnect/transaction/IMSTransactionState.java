package com.cgi.icbc.imsconnect.transaction;

import com.cgi.icbc.imsconnect.protocol.OTMAMessage;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the state of an IMS transaction.
 */
public class IMSTransactionState {

    private final String transactionId;
    private final String clientId;
    private final String transactionCode;
    private final String ltermName;
    private final IMSMessageType messageType;
    private final boolean conversational;
    private final Integer conversationId;
    private final Instant startTime;

    private volatile IMSTransactionStatus status;
    private volatile Instant endTime;
    private volatile String errorMessage;
    private volatile OTMAMessage requestMessage;
    private volatile OTMAMessage responseMessage;

    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

    private IMSTransactionState(Builder builder) {
        this.transactionId = builder.transactionId;
        this.clientId = builder.clientId;
        this.transactionCode = builder.transactionCode;
        this.ltermName = builder.ltermName;
        this.messageType = builder.messageType;
        this.conversational = builder.conversational;
        this.conversationId = builder.conversationId;
        this.startTime = builder.startTime;
        this.status = builder.status;
    }

    // Getters
    public String getTransactionId() { return transactionId; }
    public String getClientId() { return clientId; }
    public String getTransactionCode() { return transactionCode; }
    public String getLtermName() { return ltermName; }
    public IMSMessageType getMessageType() { return messageType; }
    public boolean isConversational() { return conversational; }
    public Integer getConversationId() { return conversationId; }
    public Instant getStartTime() { return startTime; }
    public IMSTransactionStatus getStatus() { return status; }
    public Instant getEndTime() { return endTime; }
    public String getErrorMessage() { return errorMessage; }
    public OTMAMessage getRequestMessage() { return requestMessage; }
    public OTMAMessage getResponseMessage() { return responseMessage; }

    // Setters
    public void setStatus(IMSTransactionStatus status) { this.status = status; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setRequestMessage(OTMAMessage requestMessage) { this.requestMessage = requestMessage; }
    public void setResponseMessage(OTMAMessage responseMessage) { this.responseMessage = responseMessage; }

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
        return status == IMSTransactionStatus.STARTED;
    }

    public boolean isCompleted() {
        return status == IMSTransactionStatus.COMPLETED;
    }

    public boolean isAborted() {
        return status == IMSTransactionStatus.ABORTED;
    }

    // Duration calculations
    public long getDurationMs() {
        Instant end = endTime != null ? endTime : Instant.now();
        return java.time.Duration.between(startTime, end).toMillis();
    }

    public long getDurationSeconds() {
        return getDurationMs() / 1000;
    }

    // Validation
    public boolean isValid() {
        return transactionId != null && !transactionId.trim().isEmpty() &&
               clientId != null && !clientId.trim().isEmpty() &&
               transactionCode != null && !transactionCode.trim().isEmpty() &&
               startTime != null && status != null;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String transactionId;
        private String clientId;
        private String transactionCode;
        private String ltermName;
        private IMSMessageType messageType = IMSMessageType.TRANSACTION;
        private boolean conversational = false;
        private Integer conversationId;
        private Instant startTime = Instant.now();
        private IMSTransactionStatus status = IMSTransactionStatus.STARTED;

        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder transactionCode(String transactionCode) {
            this.transactionCode = transactionCode;
            return this;
        }

        public Builder ltermName(String ltermName) {
            this.ltermName = ltermName;
            return this;
        }

        public Builder messageType(IMSMessageType messageType) {
            this.messageType = messageType;
            return this;
        }

        public Builder conversational(boolean conversational) {
            this.conversational = conversational;
            return this;
        }

        public Builder conversationId(Integer conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder status(IMSTransactionStatus status) {
            this.status = status;
            return this;
        }

        public IMSTransactionState build() {
            if (transactionId == null || transactionId.trim().isEmpty()) {
                throw new IllegalArgumentException("Transaction ID is required");
            }
            if (clientId == null || clientId.trim().isEmpty()) {
                throw new IllegalArgumentException("Client ID is required");
            }
            if (transactionCode == null || transactionCode.trim().isEmpty()) {
                throw new IllegalArgumentException("Transaction code is required");
            }

            return new IMSTransactionState(this);
        }
    }

    @Override
    public String toString() {
        return String.format("IMSTransactionState{id='%s', client='%s', txn='%s', status=%s, conv=%s, duration=%dms}",
                transactionId, clientId, transactionCode, status,
                conversational ? conversationId : "N/A", getDurationMs());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        IMSTransactionState that = (IMSTransactionState) obj;
        return transactionId.equals(that.transactionId);
    }

    @Override
    public int hashCode() {
        return transactionId.hashCode();
    }
}
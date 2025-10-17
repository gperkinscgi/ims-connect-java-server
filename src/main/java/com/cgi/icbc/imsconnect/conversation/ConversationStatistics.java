package com.cgi.icbc.imsconnect.conversation;

/**
 * Statistics about conversational transactions.
 */
public class ConversationStatistics {

    private final long totalConversations;
    private final long activeConversations;
    private final long completedConversations;
    private final long abortedConversations;
    private final long expiredConversations;

    private ConversationStatistics(Builder builder) {
        this.totalConversations = builder.totalConversations;
        this.activeConversations = builder.activeConversations;
        this.completedConversations = builder.completedConversations;
        this.abortedConversations = builder.abortedConversations;
        this.expiredConversations = builder.expiredConversations;
    }

    // Getters
    public long getTotalConversations() { return totalConversations; }
    public long getActiveConversations() { return activeConversations; }
    public long getCompletedConversations() { return completedConversations; }
    public long getAbortedConversations() { return abortedConversations; }
    public long getExpiredConversations() { return expiredConversations; }

    // Calculated statistics
    public double getCompletionRate() {
        long finished = completedConversations + abortedConversations + expiredConversations;
        return finished > 0 ? (double) completedConversations / finished * 100 : 0.0;
    }

    public double getAbortRate() {
        long finished = completedConversations + abortedConversations + expiredConversations;
        return finished > 0 ? (double) abortedConversations / finished * 100 : 0.0;
    }

    public double getExpirationRate() {
        long finished = completedConversations + abortedConversations + expiredConversations;
        return finished > 0 ? (double) expiredConversations / finished * 100 : 0.0;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long totalConversations;
        private long activeConversations;
        private long completedConversations;
        private long abortedConversations;
        private long expiredConversations;

        public Builder totalConversations(long totalConversations) {
            this.totalConversations = totalConversations;
            return this;
        }

        public Builder activeConversations(long activeConversations) {
            this.activeConversations = activeConversations;
            return this;
        }

        public Builder completedConversations(long completedConversations) {
            this.completedConversations = completedConversations;
            return this;
        }

        public Builder abortedConversations(long abortedConversations) {
            this.abortedConversations = abortedConversations;
            return this;
        }

        public Builder expiredConversations(long expiredConversations) {
            this.expiredConversations = expiredConversations;
            return this;
        }

        public ConversationStatistics build() {
            return new ConversationStatistics(this);
        }
    }

    @Override
    public String toString() {
        return String.format("ConversationStatistics{total=%d, active=%d, completed=%d, aborted=%d, expired=%d, completion=%.1f%%}",
                totalConversations, activeConversations, completedConversations,
                abortedConversations, expiredConversations, getCompletionRate());
    }
}
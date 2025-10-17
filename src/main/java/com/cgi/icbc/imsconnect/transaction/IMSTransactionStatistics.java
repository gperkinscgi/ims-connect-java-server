package com.cgi.icbc.imsconnect.transaction;

/**
 * Statistics about IMS transactions.
 */
public class IMSTransactionStatistics {

    private final long totalTransactions;
    private final long activeTransactions;
    private final long completedTransactions;
    private final long abortedTransactions;
    private final double averageResponseTimeMs;

    private IMSTransactionStatistics(Builder builder) {
        this.totalTransactions = builder.totalTransactions;
        this.activeTransactions = builder.activeTransactions;
        this.completedTransactions = builder.completedTransactions;
        this.abortedTransactions = builder.abortedTransactions;
        this.averageResponseTimeMs = builder.averageResponseTimeMs;
    }

    // Getters
    public long getTotalTransactions() { return totalTransactions; }
    public long getActiveTransactions() { return activeTransactions; }
    public long getCompletedTransactions() { return completedTransactions; }
    public long getAbortedTransactions() { return abortedTransactions; }
    public double getAverageResponseTimeMs() { return averageResponseTimeMs; }

    // Calculated statistics
    public double getSuccessRate() {
        long finished = completedTransactions + abortedTransactions;
        return finished > 0 ? (double) completedTransactions / finished * 100 : 0.0;
    }

    public double getFailureRate() {
        long finished = completedTransactions + abortedTransactions;
        return finished > 0 ? (double) abortedTransactions / finished * 100 : 0.0;
    }

    public double getThroughputPerSecond() {
        // This would need timing information to calculate properly
        // For now, return 0 as placeholder
        return 0.0;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long totalTransactions;
        private long activeTransactions;
        private long completedTransactions;
        private long abortedTransactions;
        private double averageResponseTimeMs;

        public Builder totalTransactions(long totalTransactions) {
            this.totalTransactions = totalTransactions;
            return this;
        }

        public Builder activeTransactions(long activeTransactions) {
            this.activeTransactions = activeTransactions;
            return this;
        }

        public Builder completedTransactions(long completedTransactions) {
            this.completedTransactions = completedTransactions;
            return this;
        }

        public Builder abortedTransactions(long abortedTransactions) {
            this.abortedTransactions = abortedTransactions;
            return this;
        }

        public Builder averageResponseTimeMs(double averageResponseTimeMs) {
            this.averageResponseTimeMs = averageResponseTimeMs;
            return this;
        }

        public IMSTransactionStatistics build() {
            return new IMSTransactionStatistics(this);
        }
    }

    @Override
    public String toString() {
        return String.format("IMSTransactionStatistics{total=%d, active=%d, completed=%d, aborted=%d, avgResponse=%.1fms, success=%.1f%%}",
                totalTransactions, activeTransactions, completedTransactions,
                abortedTransactions, averageResponseTimeMs, getSuccessRate());
    }
}
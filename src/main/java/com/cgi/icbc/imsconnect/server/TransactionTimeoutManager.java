package com.cgi.icbc.imsconnect.server;

import com.cgi.icbc.imsconnect.model.IMSResponse;
import com.cgi.icbc.imsconnect.model.ResponseType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages transaction timeouts for IMS Connect operations.
 * Handles both client-specified timeouts and server-side default timeouts.
 */
public class TransactionTimeoutManager {

    private static final Logger logger = LoggerFactory.getLogger(TransactionTimeoutManager.class);

    private final Timer timer;
    private final ConcurrentHashMap<String, TransactionTimeout> activeTimeouts = new ConcurrentHashMap<>();
    private final AtomicLong transactionIdGenerator = new AtomicLong(1);
    private final long defaultTimeoutSeconds;
    private final long maxTimeoutSeconds;

    public TransactionTimeoutManager() {
        this(300, 3600); // Default: 5 minutes default, 1 hour max
    }

    public TransactionTimeoutManager(long defaultTimeoutSeconds, long maxTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        this.maxTimeoutSeconds = maxTimeoutSeconds;
        this.timer = new HashedWheelTimer();
    }

    /**
     * Starts a timeout for a transaction.
     *
     * @param ctx the channel context
     * @param clientId the client ID
     * @param timeoutValue the timeout value from IRM header (0-255)
     * @return transaction ID for timeout tracking
     */
    public String startTransactionTimeout(ChannelHandlerContext ctx, String clientId, byte timeoutValue) {
        long timeoutSeconds = calculateTimeoutSeconds(timeoutValue);
        String transactionId = generateTransactionId();

        TransactionTimeout transactionTimeout = new TransactionTimeout(
                transactionId, clientId, ctx, timeoutSeconds);

        Timeout timeout = timer.newTimeout(new TimeoutTask(transactionTimeout), timeoutSeconds, TimeUnit.SECONDS);
        transactionTimeout.setTimeout(timeout);

        activeTimeouts.put(transactionId, transactionTimeout);

        logger.debug("Started timeout for transaction {} (client: {}, timeout: {}s)",
                    transactionId, clientId, timeoutSeconds);

        return transactionId;
    }

    /**
     * Cancels a transaction timeout.
     *
     * @param transactionId the transaction ID
     * @return true if timeout was found and cancelled
     */
    public boolean cancelTransactionTimeout(String transactionId) {
        TransactionTimeout transactionTimeout = activeTimeouts.remove(transactionId);
        if (transactionTimeout != null) {
            transactionTimeout.getTimeout().cancel();
            logger.debug("Cancelled timeout for transaction: {}", transactionId);
            return true;
        } else {
            logger.debug("Transaction timeout not found: {}", transactionId);
            return false;
        }
    }

    /**
     * Cancels all timeouts for a specific client.
     *
     * @param clientId the client ID
     * @return number of timeouts cancelled
     */
    public int cancelClientTimeouts(String clientId) {
        int cancelledCount = 0;

        activeTimeouts.entrySet().removeIf(entry -> {
            TransactionTimeout transactionTimeout = entry.getValue();
            if (clientId.equals(transactionTimeout.getClientId())) {
                transactionTimeout.getTimeout().cancel();
                logger.debug("Cancelled timeout for transaction {} (client cleanup: {})",
                           entry.getKey(), clientId);
                return true;
            }
            return false;
        });

        if (cancelledCount > 0) {
            logger.info("Cancelled {} timeouts for client: {}", cancelledCount, clientId);
        }

        return cancelledCount;
    }

    /**
     * Extends the timeout for a transaction.
     *
     * @param transactionId the transaction ID
     * @param additionalSeconds additional seconds to add
     * @return true if timeout was extended
     */
    public boolean extendTransactionTimeout(String transactionId, long additionalSeconds) {
        TransactionTimeout transactionTimeout = activeTimeouts.get(transactionId);
        if (transactionTimeout != null) {
            // Cancel existing timeout
            transactionTimeout.getTimeout().cancel();

            // Create new timeout with extended time
            long newTimeoutSeconds = Math.min(transactionTimeout.getTimeoutSeconds() + additionalSeconds,
                                             maxTimeoutSeconds);

            Timeout newTimeout = timer.newTimeout(new TimeoutTask(transactionTimeout),
                                                 newTimeoutSeconds, TimeUnit.SECONDS);
            transactionTimeout.setTimeout(newTimeout);
            transactionTimeout.setTimeoutSeconds(newTimeoutSeconds);

            logger.debug("Extended timeout for transaction {} to {}s", transactionId, newTimeoutSeconds);
            return true;
        }

        return false;
    }

    /**
     * Gets the number of active timeouts.
     *
     * @return number of active timeouts
     */
    public int getActiveTimeoutCount() {
        return activeTimeouts.size();
    }

    /**
     * Shuts down the timeout manager.
     */
    public void shutdown() {
        // Cancel all active timeouts
        activeTimeouts.values().forEach(transactionTimeout ->
                transactionTimeout.getTimeout().cancel());
        activeTimeouts.clear();

        // Shutdown timer
        timer.stop();
        logger.info("Transaction timeout manager shutdown complete");
    }

    private long calculateTimeoutSeconds(byte timeoutValue) {
        if (timeoutValue == 0) {
            return defaultTimeoutSeconds;
        }

        // IMS Connect timeout is typically in multiples of seconds
        // The exact mapping depends on implementation, but common approach:
        // 0x01-0x3C = 1-60 seconds
        // 0x3D-0x78 = 61-120 seconds
        // 0x79-0xB4 = 121-180 seconds
        // etc.

        int unsignedValue = timeoutValue & 0xFF;
        long timeoutSeconds;

        if (unsignedValue <= 60) {
            timeoutSeconds = unsignedValue;
        } else if (unsignedValue <= 120) {
            timeoutSeconds = unsignedValue;
        } else if (unsignedValue <= 180) {
            timeoutSeconds = unsignedValue;
        } else {
            // For higher values, use a scaling factor
            timeoutSeconds = Math.min(unsignedValue * 2L, maxTimeoutSeconds);
        }

        return Math.max(1, Math.min(timeoutSeconds, maxTimeoutSeconds));
    }

    private String generateTransactionId() {
        return "TXN" + transactionIdGenerator.getAndIncrement();
    }

    /**
     * Timer task that handles transaction timeouts.
     */
    private class TimeoutTask implements TimerTask {
        private final TransactionTimeout transactionTimeout;

        public TimeoutTask(TransactionTimeout transactionTimeout) {
            this.transactionTimeout = transactionTimeout;
        }

        @Override
        public void run(Timeout timeout) throws Exception {
            String transactionId = transactionTimeout.getTransactionId();
            String clientId = transactionTimeout.getClientId();

            // Remove from active timeouts
            activeTimeouts.remove(transactionId);

            logger.warn("Transaction timeout occurred: {} (client: {})", transactionId, clientId);

            // Send timeout error response
            if (transactionTimeout.getChannelContext().channel().isActive()) {
                IMSResponse timeoutResponse = IMSResponse.error(408, 1, "Transaction timeout")
                        .build();

                transactionTimeout.getChannelContext().writeAndFlush(timeoutResponse)
                        .addListener(future -> {
                            if (future.isSuccess()) {
                                logger.debug("Sent timeout response for transaction: {}", transactionId);
                            } else {
                                logger.error("Failed to send timeout response for transaction: {}",
                                           transactionId, future.cause());
                            }
                        });
            } else {
                logger.debug("Channel inactive, skipping timeout response for transaction: {}", transactionId);
            }
        }
    }

    /**
     * Represents an active transaction timeout.
     */
    private static class TransactionTimeout {
        private final String transactionId;
        private final String clientId;
        private final ChannelHandlerContext channelContext;
        private volatile long timeoutSeconds;
        private volatile Timeout timeout;

        public TransactionTimeout(String transactionId, String clientId,
                                ChannelHandlerContext channelContext, long timeoutSeconds) {
            this.transactionId = transactionId;
            this.clientId = clientId;
            this.channelContext = channelContext;
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getTransactionId() { return transactionId; }
        public String getClientId() { return clientId; }
        public ChannelHandlerContext getChannelContext() { return channelContext; }
        public long getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public Timeout getTimeout() { return timeout; }
        public void setTimeout(Timeout timeout) { this.timeout = timeout; }

        @Override
        public String toString() {
            return String.format("TransactionTimeout{id='%s', clientId='%s', timeoutSeconds=%d}",
                               transactionId, clientId, timeoutSeconds);
        }
    }
}
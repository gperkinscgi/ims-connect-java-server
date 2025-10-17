package com.cgi.icbc.imsconnect.server;

import com.cgi.icbc.imsconnect.model.IMSResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Message queue implementation for async IMS Connect transactions.
 * Manages queued responses for send-only transactions and recv-only operations.
 */
public class MessageQueue {

    private static final Logger logger = LoggerFactory.getLogger(MessageQueue.class);

    private final ConcurrentHashMap<String, ClientQueue> clientQueues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, QueuedMessage> pendingAcknowledgments = new ConcurrentHashMap<>();
    private final int maxQueueSize;
    private final long messageTimeoutSeconds;

    public MessageQueue() {
        this(1000, 3600); // Default: 1000 messages per client, 1 hour timeout
    }

    public MessageQueue(int maxQueueSize, long messageTimeoutSeconds) {
        this.maxQueueSize = maxQueueSize;
        this.messageTimeoutSeconds = messageTimeoutSeconds;
    }

    /**
     * Queues a response message for a specific client.
     *
     * @param clientId the client ID
     * @param response the response to queue
     * @param requiresAck whether the message requires acknowledgment
     * @return message ID if queued successfully, null if queue is full
     */
    public String queueMessage(String clientId, IMSResponse response, boolean requiresAck) {
        ClientQueue queue = getOrCreateClientQueue(clientId);

        String messageId = UUID.randomUUID().toString();
        QueuedMessage queuedMessage = new QueuedMessage(messageId, response, requiresAck, Instant.now());

        if (queue.offer(queuedMessage)) {
            if (requiresAck) {
                pendingAcknowledgments.put(messageId, queuedMessage);
            }
            logger.debug("Queued message {} for client {}, requiresAck: {}", messageId, clientId, requiresAck);
            return messageId;
        } else {
            logger.warn("Queue full for client {}, dropping message", clientId);
            return null;
        }
    }

    /**
     * Retrieves the next message for a client (recv-only operation).
     *
     * @param clientId the client ID
     * @param waitTimeSeconds maximum time to wait for a message
     * @return optional containing the message if available
     */
    public Optional<QueuedMessage> pollMessage(String clientId, int waitTimeSeconds) {
        ClientQueue queue = clientQueues.get(clientId);
        if (queue == null) {
            logger.debug("No queue found for client: {}", clientId);
            return Optional.empty();
        }

        try {
            QueuedMessage message;
            if (waitTimeSeconds > 0) {
                message = queue.poll(waitTimeSeconds, TimeUnit.SECONDS);
            } else {
                message = queue.poll();
            }

            if (message != null) {
                logger.debug("Retrieved message {} for client {}", message.getMessageId(), clientId);
                return Optional.of(message);
            } else {
                logger.debug("No messages available for client: {}", clientId);
                return Optional.empty();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Interrupted while waiting for message for client: {}", clientId);
            return Optional.empty();
        }
    }

    /**
     * Acknowledges a message, removing it from pending acknowledgments.
     *
     * @param messageId the message ID to acknowledge
     * @return true if message was found and acknowledged
     */
    public boolean acknowledgeMessage(String messageId) {
        QueuedMessage message = pendingAcknowledgments.remove(messageId);
        if (message != null) {
            logger.debug("Acknowledged message: {}", messageId);
            return true;
        } else {
            logger.debug("Message {} not found in pending acknowledgments", messageId);
            return false;
        }
    }

    /**
     * Negative acknowledgment - returns message to the queue.
     *
     * @param messageId the message ID
     * @param clientId the client ID
     * @param retainMessage whether to retain the message
     * @return true if message was handled
     */
    public boolean negativeAcknowledge(String messageId, String clientId, boolean retainMessage) {
        QueuedMessage message = pendingAcknowledgments.remove(messageId);
        if (message != null) {
            if (retainMessage) {
                // Put message back at front of queue
                ClientQueue queue = getOrCreateClientQueue(clientId);
                if (!queue.offerFirst(message)) {
                    logger.warn("Failed to return NAK'd message {} to front of queue for client {}",
                               messageId, clientId);
                } else {
                    pendingAcknowledgments.put(messageId, message);
                    logger.debug("Returned NAK'd message {} to queue for client {}", messageId, clientId);
                }
            } else {
                logger.debug("Discarded NAK'd message: {}", messageId);
            }
            return true;
        } else {
            logger.debug("Message {} not found for NAK", messageId);
            return false;
        }
    }

    /**
     * Gets the queue size for a specific client.
     *
     * @param clientId the client ID
     * @return queue size, 0 if no queue exists
     */
    public int getQueueSize(String clientId) {
        ClientQueue queue = clientQueues.get(clientId);
        return queue != null ? queue.size() : 0;
    }

    /**
     * Clears all messages for a specific client.
     *
     * @param clientId the client ID
     * @return number of messages cleared
     */
    public int clearClientQueue(String clientId) {
        ClientQueue queue = clientQueues.remove(clientId);
        if (queue != null) {
            int size = queue.size();
            queue.clear();

            // Remove pending acknowledgments for this client
            pendingAcknowledgments.entrySet().removeIf(entry -> {
                // This is a simplified approach; in practice you'd track client-message associations
                return false; // TODO: Implement proper client-message tracking
            });

            logger.info("Cleared queue for client {}, removed {} messages", clientId, size);
            return size;
        }
        return 0;
    }

    /**
     * Cleans up expired messages across all queues.
     *
     * @return number of messages cleaned up
     */
    public int cleanupExpiredMessages() {
        int cleanedCount = 0;
        Instant cutoff = Instant.now().minusSeconds(messageTimeoutSeconds);

        // Clean up pending acknowledgments
        cleanedCount += pendingAcknowledgments.entrySet().removeIf(entry -> {
            if (entry.getValue().getTimestamp().isBefore(cutoff)) {
                logger.debug("Expired pending acknowledgment: {}", entry.getKey());
                return true;
            }
            return false;
        }) ? 1 : 0;

        // Clean up queued messages
        for (ClientQueue queue : clientQueues.values()) {
            cleanedCount += queue.removeExpiredMessages(cutoff);
        }

        if (cleanedCount > 0) {
            logger.info("Cleaned up {} expired messages", cleanedCount);
        }

        return cleanedCount;
    }

    /**
     * Gets statistics about the message queue system.
     *
     * @return queue statistics
     */
    public QueueStatistics getStatistics() {
        int totalQueues = clientQueues.size();
        int totalMessages = clientQueues.values().stream().mapToInt(ClientQueue::size).sum();
        int pendingAcks = pendingAcknowledgments.size();

        return new QueueStatistics(totalQueues, totalMessages, pendingAcks);
    }

    private ClientQueue getOrCreateClientQueue(String clientId) {
        return clientQueues.computeIfAbsent(clientId, id -> new ClientQueue(maxQueueSize));
    }

    /**
     * Client-specific message queue with additional functionality.
     */
    private static class ClientQueue {
        private final BlockingQueue<QueuedMessage> queue;
        private final int maxSize;

        public ClientQueue(int maxSize) {
            this.maxSize = maxSize;
            this.queue = new LinkedBlockingQueue<>(maxSize);
        }

        public boolean offer(QueuedMessage message) {
            return queue.offer(message);
        }

        public boolean offerFirst(QueuedMessage message) {
            // LinkedBlockingQueue doesn't support offerFirst, so we use a workaround
            if (queue.size() >= maxSize) {
                return false;
            }

            // Create new queue with the message first
            QueuedMessage[] existing = queue.toArray(new QueuedMessage[0]);
            queue.clear();
            queue.offer(message);
            for (QueuedMessage msg : existing) {
                queue.offer(msg);
            }
            return true;
        }

        public QueuedMessage poll() {
            return queue.poll();
        }

        public QueuedMessage poll(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }

        public int size() {
            return queue.size();
        }

        public void clear() {
            queue.clear();
        }

        public int removeExpiredMessages(Instant cutoff) {
            int removedCount = 0;
            QueuedMessage[] messages = queue.toArray(new QueuedMessage[0]);
            queue.clear();

            for (QueuedMessage message : messages) {
                if (message.getTimestamp().isAfter(cutoff)) {
                    queue.offer(message);
                } else {
                    removedCount++;
                }
            }

            return removedCount;
        }
    }

    /**
     * Represents a queued message with metadata.
     */
    public static class QueuedMessage {
        private final String messageId;
        private final IMSResponse response;
        private final boolean requiresAck;
        private final Instant timestamp;

        public QueuedMessage(String messageId, IMSResponse response, boolean requiresAck, Instant timestamp) {
            this.messageId = messageId;
            this.response = response;
            this.requiresAck = requiresAck;
            this.timestamp = timestamp;
        }

        public String getMessageId() { return messageId; }
        public IMSResponse getResponse() { return response; }
        public boolean requiresAck() { return requiresAck; }
        public Instant getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("QueuedMessage{id='%s', requiresAck=%b, timestamp=%s}",
                               messageId, requiresAck, timestamp);
        }
    }

    /**
     * Statistics about the message queue system.
     */
    public static class QueueStatistics {
        private final int totalQueues;
        private final int totalMessages;
        private final int pendingAcknowledgments;

        public QueueStatistics(int totalQueues, int totalMessages, int pendingAcknowledgments) {
            this.totalQueues = totalQueues;
            this.totalMessages = totalMessages;
            this.pendingAcknowledgments = pendingAcknowledgments;
        }

        public int getTotalQueues() { return totalQueues; }
        public int getTotalMessages() { return totalMessages; }
        public int getPendingAcknowledgments() { return pendingAcknowledgments; }

        @Override
        public String toString() {
            return String.format("QueueStats{queues=%d, messages=%d, pendingAcks=%d}",
                               totalQueues, totalMessages, pendingAcknowledgments);
        }
    }
}
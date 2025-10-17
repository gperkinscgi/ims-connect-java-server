package com.cgi.icbc.imsconnect.conversation;

import com.cgi.icbc.imsconnect.protocol.OTMAMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages conversational transactions and their state.
 *
 * Conversational transactions allow multiple message exchanges between client and server
 * while maintaining context and state across the conversation.
 */
@Component
public class ConversationManager {

    private static final Logger logger = LoggerFactory.getLogger(ConversationManager.class);

    private final ConcurrentHashMap<Integer, ConversationState> conversations = new ConcurrentHashMap<>();
    private final AtomicInteger conversationIdGenerator = new AtomicInteger(1000);
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    // Configuration
    private long conversationTimeoutMinutes = 30; // Default timeout
    private int maxConversations = 1000; // Maximum concurrent conversations

    public ConversationManager() {
        // Start cleanup task
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredConversations, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Start a new conversation.
     */
    public ConversationState startConversation(String clientId, String ltermName, String transactionCode) {
        if (conversations.size() >= maxConversations) {
            throw new ConversationException("Maximum number of conversations exceeded");
        }

        int conversationId = conversationIdGenerator.incrementAndGet();

        ConversationState state = ConversationState.builder()
            .conversationId(conversationId)
            .clientId(clientId)
            .ltermName(ltermName)
            .transactionCode(transactionCode)
            .status(ConversationStatus.ACTIVE)
            .createdAt(Instant.now())
            .lastActivity(Instant.now())
            .build();

        conversations.put(conversationId, state);

        logger.debug("Started conversation {} for client {} on lterm {}", conversationId, clientId, ltermName);
        return state;
    }

    /**
     * Get conversation state by ID.
     */
    public ConversationState getConversation(int conversationId) {
        ConversationState state = conversations.get(conversationId);
        if (state == null) {
            throw new ConversationNotFoundException("Conversation not found: " + conversationId);
        }

        // Update last activity
        state.setLastActivity(Instant.now());
        return state;
    }

    /**
     * Update conversation state with new message.
     */
    public void updateConversation(int conversationId, OTMAMessage message) {
        ConversationState state = getConversation(conversationId);

        // Update state based on message
        state.setLastActivity(Instant.now());
        state.incrementMessageCount();

        // Add message to history if configured
        if (state.isKeepHistory()) {
            state.addMessage(message);
        }

        // Check if conversation should be terminated
        if (message.isLastMessage()) {
            state.setStatus(ConversationStatus.COMPLETED);
            logger.debug("Conversation {} completed", conversationId);
        }
    }

    /**
     * End a conversation.
     */
    public void endConversation(int conversationId) {
        ConversationState state = conversations.get(conversationId);
        if (state != null) {
            state.setStatus(ConversationStatus.COMPLETED);
            state.setEndedAt(Instant.now());

            logger.debug("Ended conversation {} for client {}", conversationId, state.getClientId());

            // Remove completed conversations after a delay to allow for cleanup
            cleanupExecutor.schedule(() -> conversations.remove(conversationId), 5, TimeUnit.MINUTES);
        }
    }

    /**
     * Abort a conversation due to error.
     */
    public void abortConversation(int conversationId, String reason) {
        ConversationState state = conversations.get(conversationId);
        if (state != null) {
            state.setStatus(ConversationStatus.ABORTED);
            state.setEndedAt(Instant.now());
            state.setErrorMessage(reason);

            logger.warn("Aborted conversation {} for client {}: {}",
                conversationId, state.getClientId(), reason);

            // Remove aborted conversations immediately
            conversations.remove(conversationId);
        }
    }

    /**
     * Check if conversation exists and is active.
     */
    public boolean isConversationActive(int conversationId) {
        ConversationState state = conversations.get(conversationId);
        return state != null && state.getStatus() == ConversationStatus.ACTIVE;
    }

    /**
     * Get all active conversations for a client.
     */
    public java.util.List<ConversationState> getClientConversations(String clientId) {
        return conversations.values().stream()
            .filter(state -> clientId.equals(state.getClientId()))
            .filter(state -> state.getStatus() == ConversationStatus.ACTIVE)
            .toList();
    }

    /**
     * Get conversation statistics.
     */
    public ConversationStatistics getStatistics() {
        long activeCount = conversations.values().stream()
            .filter(state -> state.getStatus() == ConversationStatus.ACTIVE)
            .count();

        long completedCount = conversations.values().stream()
            .filter(state -> state.getStatus() == ConversationStatus.COMPLETED)
            .count();

        long abortedCount = conversations.values().stream()
            .filter(state -> state.getStatus() == ConversationStatus.ABORTED)
            .count();

        return ConversationStatistics.builder()
            .totalConversations(conversations.size())
            .activeConversations(activeCount)
            .completedConversations(completedCount)
            .abortedConversations(abortedCount)
            .build();
    }

    /**
     * Cleanup expired conversations.
     */
    private void cleanupExpiredConversations() {
        Instant cutoff = Instant.now().minusSeconds(conversationTimeoutMinutes * 60);

        conversations.entrySet().removeIf(entry -> {
            ConversationState state = entry.getValue();
            boolean isExpired = state.getLastActivity().isBefore(cutoff);

            if (isExpired && state.getStatus() == ConversationStatus.ACTIVE) {
                logger.warn("Conversation {} expired for client {}",
                    entry.getKey(), state.getClientId());
                state.setStatus(ConversationStatus.EXPIRED);
                state.setEndedAt(Instant.now());
                return true;
            }

            // Remove completed/aborted conversations after extended period
            boolean isOldCompleted = state.getStatus() != ConversationStatus.ACTIVE &&
                state.getEndedAt() != null &&
                state.getEndedAt().isBefore(cutoff);

            return isOldCompleted;
        });
    }

    /**
     * Create conversation ID for new conversation.
     */
    public int generateConversationId() {
        return conversationIdGenerator.incrementAndGet();
    }

    /**
     * Validate conversation message sequence.
     */
    public boolean validateMessageSequence(ConversationState state, OTMAMessage message) {
        // Validate conversation ID matches
        if (state.getConversationId() != message.getConversationId()) {
            logger.warn("Conversation ID mismatch: expected {}, got {}",
                state.getConversationId(), message.getConversationId());
            return false;
        }

        // Validate client ID matches
        if (!state.getClientId().equals(message.getClientId())) {
            logger.warn("Client ID mismatch in conversation {}: expected {}, got {}",
                state.getConversationId(), state.getClientId(), message.getClientId());
            return false;
        }

        // Validate LTERM name matches
        if (!state.getLtermName().equals(message.getLtermName())) {
            logger.warn("LTERM name mismatch in conversation {}: expected {}, got {}",
                state.getConversationId(), state.getLtermName(), message.getLtermName());
            return false;
        }

        return true;
    }

    // Configuration methods
    public void setConversationTimeoutMinutes(long timeoutMinutes) {
        this.conversationTimeoutMinutes = timeoutMinutes;
    }

    public void setMaxConversations(int maxConversations) {
        this.maxConversations = maxConversations;
    }

    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
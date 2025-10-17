package com.cgi.icbc.imsconnect.transaction;

import com.cgi.icbc.imsconnect.conversation.ConversationManager;
import com.cgi.icbc.imsconnect.conversation.ConversationState;
import com.cgi.icbc.imsconnect.protocol.OTMAMessage;
import com.cgi.icbc.imsconnect.security.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages IMS transaction lifecycle including conversational and non-conversational transactions.
 */
@Component
public class IMSTransactionManager {

    private static final Logger logger = LoggerFactory.getLogger(IMSTransactionManager.class);

    private final ConversationManager conversationManager;
    private final AuditLogger auditLogger;
    private final ConcurrentHashMap<String, IMSTransactionState> transactions = new ConcurrentHashMap<>();
    private final AtomicLong transactionIdGenerator = new AtomicLong(1);

    @Autowired
    public IMSTransactionManager(ConversationManager conversationManager, AuditLogger auditLogger) {
        this.conversationManager = conversationManager;
        this.auditLogger = auditLogger;
    }

    /**
     * Start a new IMS transaction.
     */
    public IMSTransactionState startTransaction(OTMAMessage message) {
        String transactionId = generateTransactionId();

        IMSTransactionState.Builder builder = IMSTransactionState.builder()
            .transactionId(transactionId)
            .clientId(message.getClientId())
            .transactionCode(message.getTransactionCode())
            .ltermName(message.getLtermName())
            .messageType(determineMessageType(message))
            .status(IMSTransactionStatus.STARTED)
            .startTime(Instant.now());

        // Handle conversational transactions
        if (message.isConversational()) {
            builder.conversational(true);

            if (message.isFirstMessage()) {
                // Start new conversation
                ConversationState conversation = conversationManager.startConversation(
                    message.getClientId(),
                    message.getLtermName(),
                    message.getTransactionCode()
                );
                builder.conversationId(conversation.getConversationId());

                logger.debug("Started conversational transaction {} with conversation {}",
                    transactionId, conversation.getConversationId());
            } else {
                // Continue existing conversation
                int conversationId = message.getConversationId();
                ConversationState conversation = conversationManager.getConversation(conversationId);

                if (!conversationManager.validateMessageSequence(conversation, message)) {
                    throw new IMSTransactionException("Invalid message sequence for conversation " + conversationId);
                }

                builder.conversationId(conversationId);
                conversationManager.updateConversation(conversationId, message);

                logger.debug("Continuing conversational transaction {} in conversation {}",
                    transactionId, conversationId);
            }
        }

        IMSTransactionState transaction = builder.build();
        transactions.put(transactionId, transaction);

        // Audit transaction start
        auditLogger.logTransaction("TRANSACTION_STARTED", message.getClientId(),
            message.getTransactionCode(), null, true,
            "TxnId: " + transactionId + ", Type: " + transaction.getMessageType());

        return transaction;
    }

    /**
     * Complete a transaction successfully.
     */
    public void completeTransaction(String transactionId, OTMAMessage responseMessage) {
        IMSTransactionState transaction = getTransaction(transactionId);

        transaction.setStatus(IMSTransactionStatus.COMPLETED);
        transaction.setEndTime(Instant.now());
        transaction.setResponseMessage(responseMessage);

        // Handle conversational transaction completion
        if (transaction.isConversational() && responseMessage.isLastMessage()) {
            conversationManager.endConversation(transaction.getConversationId());
            logger.debug("Ended conversation {} for completed transaction {}",
                transaction.getConversationId(), transactionId);
        }

        // Audit transaction completion
        auditLogger.logTransaction("TRANSACTION_COMPLETED", transaction.getClientId(),
            transaction.getTransactionCode(), null, true,
            "TxnId: " + transactionId + ", Duration: " + transaction.getDurationMs() + "ms");

        logger.debug("Completed transaction {} in {}ms", transactionId, transaction.getDurationMs());
    }

    /**
     * Abort a transaction due to error.
     */
    public void abortTransaction(String transactionId, String reason, Throwable error) {
        IMSTransactionState transaction = getTransaction(transactionId);

        transaction.setStatus(IMSTransactionStatus.ABORTED);
        transaction.setEndTime(Instant.now());
        transaction.setErrorMessage(reason);

        // Handle conversational transaction abort
        if (transaction.isConversational()) {
            conversationManager.abortConversation(transaction.getConversationId(), reason);
            logger.debug("Aborted conversation {} for failed transaction {}",
                transaction.getConversationId(), transactionId);
        }

        // Audit transaction abort
        auditLogger.logTransaction("TRANSACTION_ABORTED", transaction.getClientId(),
            transaction.getTransactionCode(), null, false,
            "TxnId: " + transactionId + ", Reason: " + reason);

        logger.warn("Aborted transaction {}: {}", transactionId, reason, error);
    }

    /**
     * Get transaction state by ID.
     */
    public IMSTransactionState getTransaction(String transactionId) {
        IMSTransactionState transaction = transactions.get(transactionId);
        if (transaction == null) {
            throw new IMSTransactionNotFoundException("Transaction not found: " + transactionId);
        }
        return transaction;
    }

    /**
     * Check if transaction exists and is active.
     */
    public boolean isTransactionActive(String transactionId) {
        IMSTransactionState transaction = transactions.get(transactionId);
        return transaction != null && transaction.getStatus() == IMSTransactionStatus.STARTED;
    }

    /**
     * Get all active transactions for a client.
     */
    public java.util.List<IMSTransactionState> getClientTransactions(String clientId) {
        return transactions.values().stream()
            .filter(txn -> clientId.equals(txn.getClientId()))
            .filter(txn -> txn.getStatus() == IMSTransactionStatus.STARTED)
            .toList();
    }

    /**
     * Get transaction statistics.
     */
    public IMSTransactionStatistics getStatistics() {
        long totalTransactions = transactions.size();
        long activeTransactions = transactions.values().stream()
            .filter(txn -> txn.getStatus() == IMSTransactionStatus.STARTED)
            .count();
        long completedTransactions = transactions.values().stream()
            .filter(txn -> txn.getStatus() == IMSTransactionStatus.COMPLETED)
            .count();
        long abortedTransactions = transactions.values().stream()
            .filter(txn -> txn.getStatus() == IMSTransactionStatus.ABORTED)
            .count();

        // Calculate average response time for completed transactions
        double avgResponseTime = transactions.values().stream()
            .filter(txn -> txn.getStatus() == IMSTransactionStatus.COMPLETED)
            .mapToLong(IMSTransactionState::getDurationMs)
            .average()
            .orElse(0.0);

        return IMSTransactionStatistics.builder()
            .totalTransactions(totalTransactions)
            .activeTransactions(activeTransactions)
            .completedTransactions(completedTransactions)
            .abortedTransactions(abortedTransactions)
            .averageResponseTimeMs(avgResponseTime)
            .build();
    }

    /**
     * Clean up completed transactions.
     */
    public void cleanupCompletedTransactions() {
        Instant cutoff = Instant.now().minusSeconds(300); // Keep for 5 minutes

        transactions.entrySet().removeIf(entry -> {
            IMSTransactionState txn = entry.getValue();
            return txn.getStatus() != IMSTransactionStatus.STARTED &&
                   txn.getEndTime() != null &&
                   txn.getEndTime().isBefore(cutoff);
        });
    }

    /**
     * Handle transaction timeout.
     */
    public void timeoutTransaction(String transactionId) {
        IMSTransactionState transaction = transactions.get(transactionId);
        if (transaction != null && transaction.getStatus() == IMSTransactionStatus.STARTED) {
            abortTransaction(transactionId, "Transaction timeout", null);
        }
    }

    /**
     * Create response message for transaction.
     */
    public OTMAMessage createResponse(IMSTransactionState transaction, byte[] responseData) {
        // Get the original request message to build proper response
        OTMAMessage requestMessage = transaction.getRequestMessage();
        if (requestMessage == null) {
            throw new IMSTransactionException("No request message found for transaction " + transaction.getTransactionId());
        }

        return OTMAMessage.createResponse(requestMessage, responseData);
    }

    private String generateTransactionId() {
        return "TXN" + System.currentTimeMillis() + "_" + transactionIdGenerator.incrementAndGet();
    }

    private IMSMessageType determineMessageType(OTMAMessage message) {
        if (message.isConversational()) {
            return IMSMessageType.CONVERSATIONAL;
        } else if (message.isTransaction()) {
            return IMSMessageType.TRANSACTION;
        } else if (message.isResponse()) {
            return IMSMessageType.RESPONSE;
        } else {
            return IMSMessageType.COMMAND;
        }
    }

    /**
     * Validate transaction state transition.
     */
    private boolean isValidStateTransition(IMSTransactionStatus from, IMSTransactionStatus to) {
        return switch (from) {
            case STARTED -> to == IMSTransactionStatus.COMPLETED || to == IMSTransactionStatus.ABORTED;
            case COMPLETED, ABORTED -> false; // Terminal states
        };
    }

    /**
     * Process transaction with proper lifecycle management.
     */
    public IMSTransactionResult processTransaction(OTMAMessage message, TransactionProcessor processor) {
        IMSTransactionState transaction = null;
        String transactionId = null;

        try {
            // Start transaction
            transaction = startTransaction(message);
            transactionId = transaction.getTransactionId();

            // Set request message
            transaction.setRequestMessage(message);

            // Process the transaction
            OTMAMessage response = processor.process(transaction, message);

            // Complete transaction
            completeTransaction(transactionId, response);

            return IMSTransactionResult.success(transaction, response);

        } catch (Exception e) {
            // Abort transaction on error
            if (transactionId != null) {
                abortTransaction(transactionId, "Processing error: " + e.getMessage(), e);
            }

            return IMSTransactionResult.failure(transaction, e);
        }
    }

    @FunctionalInterface
    public interface TransactionProcessor {
        OTMAMessage process(IMSTransactionState transaction, OTMAMessage request) throws Exception;
    }
}
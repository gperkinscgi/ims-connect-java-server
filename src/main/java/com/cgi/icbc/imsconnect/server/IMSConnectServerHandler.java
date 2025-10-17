package com.cgi.icbc.imsconnect.server;

import com.cgi.icbc.imsconnect.model.IMSRequest;
import com.cgi.icbc.imsconnect.model.IMSResponse;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for handling IMS Connect server operations.
 * Implementations provide the business logic for processing IMS transactions.
 */
public interface IMSConnectServerHandler {

    /**
     * Processes a synchronous transaction (send-receive).
     *
     * @param request the IMS request to process
     * @return CompletableFuture containing the response
     */
    CompletableFuture<IMSResponse> processTransaction(IMSRequest request);

    /**
     * Handles an asynchronous message (send-only).
     *
     * @param request the IMS request to handle
     * @param clientId the client ID for response routing
     */
    void handleAsyncMessage(IMSRequest request, String clientId);

    /**
     * Polls for queued responses for a specific client (recv-only).
     *
     * @param clientId the client ID to poll responses for
     * @return Optional containing a response if available
     */
    Optional<IMSResponse> pollResponse(String clientId);

    /**
     * Acknowledges receipt of a message.
     *
     * @param clientId the client ID
     * @param messageId optional message identifier
     */
    void acknowledgeMessage(String clientId, String messageId);

    /**
     * Sends a negative acknowledgment for a message.
     *
     * @param clientId the client ID
     * @param messageId optional message identifier
     * @param reasonCode NAK reason code
     * @param retainMessage whether to retain the message
     */
    void negativeAcknowledge(String clientId, String messageId, int reasonCode, boolean retainMessage);

    /**
     * Validates client credentials.
     *
     * @param userId user ID
     * @param groupId group ID
     * @param password password
     * @return true if credentials are valid
     */
    default boolean validateCredentials(String userId, String groupId, String password) {
        // Default implementation - override for custom authentication
        return true;
    }

    /**
     * Generates a unique client ID if requested.
     *
     * @return generated client ID
     */
    default String generateClientId() {
        return "CLI" + System.currentTimeMillis() % 100000;
    }
}
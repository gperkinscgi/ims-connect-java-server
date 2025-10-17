package com.cgi.icbc.imsconnect.handlers;

import com.cgi.icbc.imsconnect.protocol.OTMAMessage;
import com.cgi.icbc.imsconnect.server.OTMATransactionHandler;
import com.cgi.icbc.imsconnect.util.EbcdicConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handler for IMS Connect system messages including PING and NOTIFY.
 * These messages are used for health monitoring and system notifications.
 */
@Component
public class SystemMessageHandler implements OTMATransactionHandler {

    private static final Logger logger = LoggerFactory.getLogger(SystemMessageHandler.class);

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public boolean canHandle(OTMAMessage message) {
        String transactionCode = message.getTransactionCode();
        return "PING".equals(transactionCode) ||
               "NOTIFY".equals(transactionCode) ||
               "ECHO".equals(transactionCode) ||
               "STATUS".equals(transactionCode);
    }

    @Override
    public OTMAMessage handleOTMATransaction(OTMAMessage request) {
        String transactionCode = request.getTransactionCode();

        try {
            logger.debug("Processing system message: {} from client: {}",
                transactionCode, request.getClientId());

            return switch (transactionCode) {
                case "PING" -> handlePing(request);
                case "NOTIFY" -> handleNotify(request);
                case "ECHO" -> handleEcho(request);
                case "STATUS" -> handleStatus(request);
                default -> createErrorResponse(request, "Unknown system message: " + transactionCode);
            };

        } catch (Exception e) {
            logger.error("Error processing system message: {}", transactionCode, e);
            return createErrorResponse(request, "System message processing failed: " + e.getMessage());
        }
    }

    private OTMAMessage handlePing(OTMAMessage request) {
        logger.debug("Processing PING request from client: {}", request.getClientId());

        // Extract ping data if present
        byte[] messageData = request.getMessageData();
        String pingData = "";
        if (messageData != null && messageData.length > 0) {
            pingData = EbcdicConverter.ebcdicToAscii(messageData).trim();
        }

        // Build PONG response
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        StringBuilder response = new StringBuilder();
        response.append(String.format("%-8s", "PONG"));
        response.append(String.format("%-4s", "0000")); // Success code
        response.append(String.format("%-23s", timestamp));
        response.append(String.format("%-16s", request.getClientId() != null ? request.getClientId() : "UNKNOWN"));

        // Echo back ping data if provided
        if (!pingData.isEmpty()) {
            response.append(String.format("%-32s", pingData));
        } else {
            response.append(String.format("%-32s", "SERVER_ALIVE"));
        }

        response.append(String.format("%-20s", " ")); // Padding

        byte[] responseData = EbcdicConverter.asciiToEbcdic(response.toString());
        return OTMAMessage.createResponse(request, responseData);
    }

    private OTMAMessage handleNotify(OTMAMessage request) {
        logger.debug("Processing NOTIFY request from client: {}", request.getClientId());

        // Extract notification data
        byte[] messageData = request.getMessageData();
        String notifyData = "";
        if (messageData != null && messageData.length > 0) {
            notifyData = EbcdicConverter.ebcdicToAscii(messageData).trim();
        }

        // Parse notification type and message
        String notificationType = "INFO";
        String notificationMessage = notifyData;

        if (notifyData.length() > 8) {
            notificationType = notifyData.substring(0, 8).trim();
            notificationMessage = notifyData.substring(8).trim();
        }

        // Log the notification
        switch (notificationType.toUpperCase()) {
            case "ERROR" -> logger.error("Client notification: {}", notificationMessage);
            case "WARN" -> logger.warn("Client notification: {}", notificationMessage);
            case "INFO" -> logger.info("Client notification: {}", notificationMessage);
            case "DEBUG" -> logger.debug("Client notification: {}", notificationMessage);
            default -> logger.info("Client notification [{}]: {}", notificationType, notificationMessage);
        }

        // Build acknowledgment response
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        StringBuilder response = new StringBuilder();
        response.append(String.format("%-8s", "NOTIFYOK"));
        response.append(String.format("%-4s", "0000")); // Success code
        response.append(String.format("%-23s", timestamp));
        response.append(String.format("%-16s", request.getClientId() != null ? request.getClientId() : "UNKNOWN"));
        response.append(String.format("%-8s", notificationType));
        response.append(String.format("%-20s", "ACKNOWLEDGED"));
        response.append(String.format("%-20s", " ")); // Padding

        byte[] responseData = EbcdicConverter.asciiToEbcdic(response.toString());
        return OTMAMessage.createResponse(request, responseData);
    }

    private OTMAMessage handleEcho(OTMAMessage request) {
        logger.debug("Processing ECHO request from client: {}", request.getClientId());

        // Extract echo data
        byte[] messageData = request.getMessageData();
        String echoData = "";
        if (messageData != null && messageData.length > 0) {
            echoData = EbcdicConverter.ebcdicToAscii(messageData).trim();
        }

        // Build echo response - return exactly what was sent
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        StringBuilder response = new StringBuilder();
        response.append(String.format("%-8s", "ECHO"));
        response.append(String.format("%-4s", "0000")); // Success code
        response.append(String.format("%-23s", timestamp));

        // Echo back the original data
        if (!echoData.isEmpty()) {
            // Ensure we don't exceed message limits
            String truncatedEcho = echoData.length() > 64 ? echoData.substring(0, 64) : echoData;
            response.append(String.format("%-64s", truncatedEcho));
        } else {
            response.append(String.format("%-64s", "NO_DATA_PROVIDED"));
        }

        byte[] responseData = EbcdicConverter.asciiToEbcdic(response.toString());
        return OTMAMessage.createResponse(request, responseData);
    }

    private OTMAMessage handleStatus(OTMAMessage request) {
        logger.debug("Processing STATUS request from client: {}", request.getClientId());

        // Build status response with server information
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        int availableProcessors = runtime.availableProcessors();

        StringBuilder response = new StringBuilder();
        response.append(String.format("%-8s", "STATUS"));
        response.append(String.format("%-4s", "0000")); // Success code
        response.append(String.format("%-23s", timestamp));
        response.append(String.format("%-16s", "IMS_CONNECT_SERVER"));
        response.append(String.format("%-8s", "ACTIVE"));
        response.append(String.format("%-8d", usedMemory / 1024 / 1024)); // Used memory in MB
        response.append(String.format("%-8d", totalMemory / 1024 / 1024)); // Total memory in MB
        response.append(String.format("%-4d", availableProcessors)); // CPU cores
        response.append(String.format("%-20s", " ")); // Padding

        byte[] responseData = EbcdicConverter.asciiToEbcdic(response.toString());
        return OTMAMessage.createResponse(request, responseData);
    }

    private OTMAMessage createErrorResponse(OTMAMessage request, String errorMessage) {
        StringBuilder response = new StringBuilder();
        response.append(String.format("%-8s", "ERROR"));
        response.append(String.format("%-4s", "9999"));
        response.append(String.format("%-80s", errorMessage));
        response.append(String.format("%-20s", " "));

        byte[] responseData = EbcdicConverter.asciiToEbcdic(response.toString());
        return OTMAMessage.createResponse(request, responseData);
    }

    @Override
    public int getPriority() {
        return 1000; // Highest priority for system messages
    }

    @Override
    public boolean supportsConversational() {
        return false; // System messages are not conversational
    }

    @Override
    public boolean requiresSecurityValidation() {
        return false; // System messages typically don't require full security validation
    }

    @Override
    public String[] getSupportedTransactionCodes() {
        return new String[]{"PING", "NOTIFY", "ECHO", "STATUS"};
    }

    @Override
    public String[] getSupportedLtermNames() {
        return new String[0]; // System messages support all LTERMs
    }
}
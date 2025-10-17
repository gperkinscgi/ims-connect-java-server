package com.cgi.icbc.imsconnect.handlers;

import com.cgi.icbc.imsconnect.protocol.IRMHeader;
import com.cgi.icbc.imsconnect.server.IMSTransactionHandler;
import com.cgi.icbc.imsconnect.util.EbcdicConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handler for legacy IMS Connect system messages (non-OTMA).
 * Handles PING, NOTIFY, ECHO, and STATUS messages for legacy clients.
 */
@Component
public class LegacySystemMessageHandler implements IMSTransactionHandler {

    private static final Logger logger = LoggerFactory.getLogger(LegacySystemMessageHandler.class);

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public boolean canHandle(IRMHeader header, String messageData) {
        // Check if this is a system message by looking at the transaction code in the message
        if (messageData == null || messageData.length() < 8) {
            return false;
        }

        String transactionCode = messageData.substring(0, 8).trim();
        return "PING".equals(transactionCode) ||
               "NOTIFY".equals(transactionCode) ||
               "ECHO".equals(transactionCode) ||
               "STATUS".equals(transactionCode);
    }

    @Override
    public byte[] handleTransaction(IRMHeader header, String messageData) {
        try {
            String transactionCode = messageData.substring(0, 8).trim();
            String payload = messageData.length() > 8 ? messageData.substring(8) : "";

            logger.debug("Processing legacy system message: {} from client: {}",
                transactionCode, header.getClientId());

            return switch (transactionCode) {
                case "PING" -> handlePing(header, payload);
                case "NOTIFY" -> handleNotify(header, payload);
                case "ECHO" -> handleEcho(header, payload);
                case "STATUS" -> handleStatus(header, payload);
                default -> createErrorResponse("Unknown system message: " + transactionCode);
            };

        } catch (Exception e) {
            logger.error("Error processing legacy system message", e);
            return createErrorResponse("System message processing failed: " + e.getMessage());
        }
    }

    private byte[] handlePing(IRMHeader header, String payload) {
        logger.debug("Processing legacy PING request from client: {}", header.getClientId());

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        StringBuilder response = new StringBuilder();
        response.append(String.format("%-8s", "PONG"));
        response.append(String.format("%-4s", "0000")); // Success code
        response.append(String.format("%-23s", timestamp));
        response.append(String.format("%-16s", header.getClientId() != null ? header.getClientId() : "UNKNOWN"));

        // Echo back ping data if provided
        if (!payload.trim().isEmpty()) {
            response.append(String.format("%-32s", payload.trim()));
        } else {
            response.append(String.format("%-32s", "SERVER_ALIVE"));
        }

        response.append(String.format("%-20s", " ")); // Padding

        return EbcdicConverter.asciiToEbcdic(response.toString());
    }

    private byte[] handleNotify(IRMHeader header, String payload) {
        logger.debug("Processing legacy NOTIFY request from client: {}", header.getClientId());

        // Parse notification type and message
        String notificationType = "INFO";
        String notificationMessage = payload.trim();

        if (payload.length() > 8) {
            notificationType = payload.substring(0, 8).trim();
            notificationMessage = payload.substring(8).trim();
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
        response.append(String.format("%-16s", header.getClientId() != null ? header.getClientId() : "UNKNOWN"));
        response.append(String.format("%-8s", notificationType));
        response.append(String.format("%-20s", "ACKNOWLEDGED"));
        response.append(String.format("%-20s", " ")); // Padding

        return EbcdicConverter.asciiToEbcdic(response.toString());
    }

    private byte[] handleEcho(IRMHeader header, String payload) {
        logger.debug("Processing legacy ECHO request from client: {}", header.getClientId());

        // Build echo response - return exactly what was sent
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        StringBuilder response = new StringBuilder();
        response.append(String.format("%-8s", "ECHO"));
        response.append(String.format("%-4s", "0000")); // Success code
        response.append(String.format("%-23s", timestamp));

        // Echo back the original data
        if (!payload.trim().isEmpty()) {
            // Ensure we don't exceed message limits
            String truncatedEcho = payload.length() > 64 ? payload.substring(0, 64) : payload;
            response.append(String.format("%-64s", truncatedEcho));
        } else {
            response.append(String.format("%-64s", "NO_DATA_PROVIDED"));
        }

        return EbcdicConverter.asciiToEbcdic(response.toString());
    }

    private byte[] handleStatus(IRMHeader header, String payload) {
        logger.debug("Processing legacy STATUS request from client: {}", header.getClientId());

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

        return EbcdicConverter.asciiToEbcdic(response.toString());
    }

    private byte[] createErrorResponse(String errorMessage) {
        StringBuilder response = new StringBuilder();
        response.append(String.format("%-8s", "ERROR"));
        response.append(String.format("%-4s", "9999"));
        response.append(String.format("%-80s", errorMessage));
        response.append(String.format("%-20s", " "));

        return EbcdicConverter.asciiToEbcdic(response.toString());
    }

    @Override
    public int getPriority() {
        return 1000; // Highest priority for system messages
    }

    @Override
    public String[] getSupportedTransactionCodes() {
        return new String[]{"PING", "NOTIFY", "ECHO", "STATUS"};
    }
}
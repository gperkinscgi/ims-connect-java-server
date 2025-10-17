package com.cgi.icbc.imsconnect.monitoring;

import com.cgi.icbc.imsconnect.handlers.SystemMessageHandler;
import com.cgi.icbc.imsconnect.protocol.IRMHeader;
import com.cgi.icbc.imsconnect.protocol.OTMAHeader;
import com.cgi.icbc.imsconnect.protocol.OTMAMessage;
import com.cgi.icbc.imsconnect.util.EbcdicConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Health indicator that uses system message handlers to check server health.
 * Tests internal PING functionality to verify message processing is working.
 */
@Component
public class SystemMessageHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(SystemMessageHealthIndicator.class);

    private final SystemMessageHandler systemMessageHandler;

    @Autowired
    public SystemMessageHealthIndicator(SystemMessageHandler systemMessageHandler) {
        this.systemMessageHandler = systemMessageHandler;
    }

    @Override
    public Health health() {
        try {
            // Create a test PING message
            OTMAMessage pingRequest = createTestPingMessage();

            // Process the PING message
            long startTime = System.currentTimeMillis();
            OTMAMessage response = systemMessageHandler.handleOTMATransaction(pingRequest);
            long responseTime = System.currentTimeMillis() - startTime;

            // Validate the response
            if (response != null && isValidPongResponse(response)) {
                return Health.up()
                    .withDetail("message", "System message processing is healthy")
                    .withDetail("ping_response_time_ms", responseTime)
                    .withDetail("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .withDetail("handlers_active", "SystemMessageHandler")
                    .build();
            } else {
                return Health.down()
                    .withDetail("message", "Invalid PING response")
                    .withDetail("response", response != null ? "present" : "null")
                    .withDetail("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .build();
            }

        } catch (Exception e) {
            logger.error("Health check failed for system message processing", e);
            return Health.down()
                .withDetail("message", "System message processing failed")
                .withDetail("error", e.getMessage())
                .withDetail("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
        }
    }

    private OTMAMessage createTestPingMessage() {
        // Create IRM header for test message
        IRMHeader irmHeader = new IRMHeader();
        irmHeader.setLength((short) 32);
        irmHeader.setFlags1((byte) 0x00);
        irmHeader.setFlags2((byte) 0x00);
        irmHeader.setFlags3((byte) 0x00);
        irmHeader.setFlags4((byte) 0x00);
        irmHeader.setFlags5((byte) 0x00);
        irmHeader.setClientId("HEALTH_CHECK");
        irmHeader.setDestinationId("PING");

        // Create OTMA header for test message
        OTMAHeader otmaHeader = new OTMAHeader();
        otmaHeader.setLength((short) 20);
        otmaHeader.setVersion((byte) 0x01);
        otmaHeader.setFlags((byte) 0x00);
        otmaHeader.setConversationId(0);
        otmaHeader.setLtermName("HEALTH");
        otmaHeader.setMessageType(OTMAHeader.MSG_TYPE_TRANSACTION);

        // Create test message data
        String testData = "HEALTH_CHECK_" + System.currentTimeMillis();
        byte[] messageData = EbcdicConverter.asciiToEbcdic(testData);

        // Create complete OTMA message
        OTMAMessage message = new OTMAMessage();
        message.setIrmHeader(irmHeader);
        message.setOtmaHeader(otmaHeader);
        message.setMessageData(messageData);

        return message;
    }

    private boolean isValidPongResponse(OTMAMessage response) {
        try {
            byte[] responseData = response.getMessageData();
            if (responseData == null || responseData.length < 8) {
                return false;
            }

            String responseString = EbcdicConverter.ebcdicToAscii(responseData);
            String responseCode = responseString.substring(0, 8).trim();

            // Check for valid PONG response
            return "PONG".equals(responseCode);

        } catch (Exception e) {
            logger.warn("Failed to validate PONG response", e);
            return false;
        }
    }
}
package com.cgi.icbc.imsconnect.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit logging component for security events and transactions.
 */
@Component
public class AuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    /**
     * Logs a transaction event.
     *
     * @param event the event type
     * @param clientId the client ID
     * @param transactionCode the transaction code
     * @param userId the user ID (if available)
     * @param success whether the transaction was successful
     * @param details additional details
     */
    public void logTransaction(String event, String clientId, String transactionCode,
                             String userId, boolean success, String details) {
        String correlationId = generateCorrelationId();

        try {
            MDC.put("correlationId", correlationId);
            MDC.put("eventType", "TRANSACTION");
            MDC.put("clientId", clientId != null ? clientId : "unknown");
            MDC.put("transactionCode", transactionCode != null ? transactionCode : "unknown");
            MDC.put("userId", userId != null ? userId : "anonymous");
            MDC.put("success", String.valueOf(success));
            MDC.put("timestamp", Instant.now().toString());

            if (success) {
                auditLog.info("Transaction {} completed - Client: {}, TxnCode: {}, User: {}, Details: {}",
                             event, clientId, transactionCode, userId, details);
            } else {
                auditLog.warn("Transaction {} failed - Client: {}, TxnCode: {}, User: {}, Details: {}",
                             event, clientId, transactionCode, userId, details);
            }

        } finally {
            MDC.clear();
        }
    }

    /**
     * Logs a security event.
     *
     * @param event the security event type
     * @param clientId the client ID
     * @param userId the user ID
     * @param sourceIp the source IP address
     * @param success whether the event was successful
     * @param details additional details
     */
    public void logSecurityEvent(String event, String clientId, String userId, String sourceIp,
                                boolean success, String details) {
        String correlationId = generateCorrelationId();

        try {
            MDC.put("correlationId", correlationId);
            MDC.put("eventType", "SECURITY");
            MDC.put("clientId", clientId != null ? clientId : "unknown");
            MDC.put("userId", userId != null ? userId : "anonymous");
            MDC.put("sourceIp", sourceIp != null ? sourceIp : "unknown");
            MDC.put("success", String.valueOf(success));
            MDC.put("timestamp", Instant.now().toString());

            if (success) {
                auditLog.info("Security event {} - Client: {}, User: {}, IP: {}, Details: {}",
                             event, clientId, userId, sourceIp, details);
            } else {
                auditLog.warn("Security event {} failed - Client: {}, User: {}, IP: {}, Details: {}",
                             event, clientId, userId, sourceIp, details);
            }

        } finally {
            MDC.clear();
        }
    }

    /**
     * Logs a connection event.
     *
     * @param event the connection event type
     * @param clientId the client ID
     * @param sourceIp the source IP address
     * @param details additional details
     */
    public void logConnectionEvent(String event, String clientId, String sourceIp, String details) {
        String correlationId = generateCorrelationId();

        try {
            MDC.put("correlationId", correlationId);
            MDC.put("eventType", "CONNECTION");
            MDC.put("clientId", clientId != null ? clientId : "unknown");
            MDC.put("sourceIp", sourceIp != null ? sourceIp : "unknown");
            MDC.put("timestamp", Instant.now().toString());

            auditLog.info("Connection event {} - Client: {}, IP: {}, Details: {}",
                         event, clientId, sourceIp, details);

        } finally {
            MDC.clear();
        }
    }

    /**
     * Logs an administrative event.
     *
     * @param event the admin event type
     * @param adminUser the admin user
     * @param action the action performed
     * @param target the target of the action
     * @param success whether the action was successful
     * @param details additional details
     */
    public void logAdminEvent(String event, String adminUser, String action, String target,
                            boolean success, String details) {
        String correlationId = generateCorrelationId();

        try {
            MDC.put("correlationId", correlationId);
            MDC.put("eventType", "ADMIN");
            MDC.put("adminUser", adminUser != null ? adminUser : "unknown");
            MDC.put("action", action != null ? action : "unknown");
            MDC.put("target", target != null ? target : "unknown");
            MDC.put("success", String.valueOf(success));
            MDC.put("timestamp", Instant.now().toString());

            if (success) {
                auditLog.info("Admin event {} - User: {}, Action: {}, Target: {}, Details: {}",
                             event, adminUser, action, target, details);
            } else {
                auditLog.warn("Admin event {} failed - User: {}, Action: {}, Target: {}, Details: {}",
                             event, adminUser, action, target, details);
            }

        } finally {
            MDC.clear();
        }
    }

    private String generateCorrelationId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
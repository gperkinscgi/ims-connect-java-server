package com.cgi.icbc.imsconnect.security;

import com.cgi.icbc.imsconnect.protocol.IRMHeader;
import com.cgi.icbc.imsconnect.server.IMSResponse;
import com.cgi.icbc.imsconnect.server.IMSTransactionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Abstract base class for transaction handlers that require RACF security validation.
 * Provides common security validation and audit logging functionality.
 */
public abstract class SecureTransactionHandler implements IMSTransactionHandler {

    private static final Logger logger = LoggerFactory.getLogger(SecureTransactionHandler.class);

    @Autowired
    protected RACFSecurityParser securityParser;

    @Autowired
    protected TransactionSecurityValidator securityValidator;

    @Autowired
    protected AuditLogger auditLogger;

    @Override
    public final IMSResponse handleTransaction(IRMHeader header, String messageData) {
        SecurityContext securityContext = null;

        try {
            // Parse security context from IRM header
            securityContext = securityParser.parseSecurityContext(header);

            // Validate minimum security requirements
            securityValidator.validateMinimumSecurity(securityContext);

            // Check transaction-specific authorization
            if (!securityValidator.canExecuteTransaction(securityContext, header.getTransactionCodeAsString())) {
                auditLogger.logSecurityEvent("UNAUTHORIZED_TRANSACTION",
                    header.getClientIdAsString(), securityContext.getUserId(),
                    null, false, "Transaction: " + header.getTransactionCodeAsString());
                return createSecurityErrorResponse("Unauthorized transaction");
            }

            // Log successful security validation
            auditLogger.logSecurityEvent("TRANSACTION_AUTHORIZED",
                header.getClientIdAsString(), securityContext.getUserId(),
                null, true, "Transaction: " + header.getTransactionCodeAsString());

            // Delegate to specific transaction handler
            IMSResponse response = handleSecureTransaction(header, messageData, securityContext);

            // Log successful transaction completion
            auditLogger.logTransaction("TRANSACTION_COMPLETED",
                header.getClientIdAsString(), header.getTransactionCodeAsString(),
                securityContext.getUserId(), response.isSuccess(),
                "Response length: " + (response.getData() != null ? response.getData().length() : 0));

            return response;

        } catch (SecurityTokenException | SecurityValidationException e) {
            logger.warn("Security validation failed for transaction {}: {}",
                header.getTransactionCodeAsString(), e.getMessage());

            String userId = securityContext != null ? securityContext.getUserId() : "unknown";
            auditLogger.logSecurityEvent("SECURITY_VALIDATION_FAILED",
                header.getClientIdAsString(), userId, null, false, e.getMessage());

            return createSecurityErrorResponse("Security validation failed");

        } catch (Exception e) {
            logger.error("Unexpected error processing secure transaction {}",
                header.getTransactionCodeAsString(), e);

            String userId = securityContext != null ? securityContext.getUserId() : "unknown";
            auditLogger.logTransaction("TRANSACTION_ERROR",
                header.getClientIdAsString(), header.getTransactionCodeAsString(),
                userId, false, "Error: " + e.getMessage());

            return createSystemErrorResponse("Transaction processing failed");
        }
    }

    /**
     * Handle the transaction with validated security context.
     * Subclasses must implement this method to provide transaction-specific logic.
     */
    protected abstract IMSResponse handleSecureTransaction(IRMHeader header, String messageData, SecurityContext securityContext);

    /**
     * Check if the security context allows access to the specified account.
     */
    protected boolean validateAccountAccess(SecurityContext securityContext, String accountNumber) {
        try {
            boolean hasAccess = securityValidator.canAccessAccount(securityContext, accountNumber);

            if (!hasAccess) {
                auditLogger.logSecurityEvent("UNAUTHORIZED_ACCOUNT_ACCESS",
                    null, securityContext.getUserId(), null, false,
                    "Account: " + accountNumber);
            }

            return hasAccess;

        } catch (Exception e) {
            logger.error("Error validating account access for user {} and account {}",
                securityContext.getUserId(), accountNumber, e);
            return false;
        }
    }

    /**
     * Check if the security context allows the specified operation on a resource.
     */
    protected boolean validateResourceAccess(SecurityContext securityContext, String resource, String operation) {
        try {
            boolean hasAccess = securityValidator.canPerformOperation(securityContext, operation, resource);

            if (!hasAccess) {
                auditLogger.logSecurityEvent("UNAUTHORIZED_RESOURCE_ACCESS",
                    null, securityContext.getUserId(), null, false,
                    "Resource: " + resource + ", Operation: " + operation);
            }

            return hasAccess;

        } catch (Exception e) {
            logger.error("Error validating resource access for user {}, resource {}, operation {}",
                securityContext.getUserId(), resource, operation, e);
            return false;
        }
    }

    /**
     * Create a standardized security error response.
     */
    protected IMSResponse createSecurityErrorResponse(String message) {
        return IMSResponse.builder()
            .success(false)
            .responseCode("9001")  // Security error code
            .message("SECURITY_ERROR")
            .data(buildErrorMessage("9001", message))
            .build();
    }

    /**
     * Create a standardized system error response.
     */
    protected IMSResponse createSystemErrorResponse(String message) {
        return IMSResponse.builder()
            .success(false)
            .responseCode("9999")  // System error code
            .message("SYSTEM_ERROR")
            .data(buildErrorMessage("9999", message))
            .build();
    }

    /**
     * Create a standardized business error response.
     */
    protected IMSResponse createBusinessErrorResponse(String errorCode, String message) {
        return IMSResponse.builder()
            .success(false)
            .responseCode(errorCode)
            .message(message)
            .data(buildErrorMessage(errorCode, message))
            .build();
    }

    /**
     * Create a successful response.
     */
    protected IMSResponse createSuccessResponse(String responseData) {
        return IMSResponse.builder()
            .success(true)
            .responseCode("0000")
            .message("SUCCESS")
            .data(responseData)
            .build();
    }

    private String buildErrorMessage(String errorCode, String message) {
        // Build fixed-format error message for mainframe client
        StringBuilder response = new StringBuilder();
        response.append(String.format("%-8s", "ERROR"));     // Message type
        response.append(String.format("%-4s", errorCode));   // Error code
        response.append(String.format("%-80s", message));    // Error message
        response.append(String.format("%-20s", " "));        // Reserved space

        return com.cgi.icbc.imsconnect.util.EbcdicConverter.asciiToEbcdic(response.toString());
    }
}
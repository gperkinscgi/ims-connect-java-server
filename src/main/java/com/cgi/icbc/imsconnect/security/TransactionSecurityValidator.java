package com.cgi.icbc.imsconnect.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates transaction authorization based on RACF security context.
 */
@Component
public class TransactionSecurityValidator {

    private static final Logger logger = LoggerFactory.getLogger(TransactionSecurityValidator.class);

    private final RACFValidationService racfValidationService;

    @Autowired
    public TransactionSecurityValidator(RACFValidationService racfValidationService) {
        this.racfValidationService = racfValidationService;
    }

    /**
     * Check if the security context allows execution of the specified transaction.
     */
    public boolean canExecuteTransaction(SecurityContext securityContext, String transactionCode) {
        if (securityContext == null || !securityContext.isValid()) {
            logger.warn("Invalid security context for transaction: {}", transactionCode);
            return false;
        }

        if (transactionCode == null || transactionCode.trim().isEmpty()) {
            logger.warn("Invalid transaction code");
            return false;
        }

        String normalizedTxnCode = transactionCode.trim().toUpperCase();
        String userId = securityContext.getUserId();

        try {
            // Check if user has specific transaction authority
            if (securityContext.canExecuteTransaction(normalizedTxnCode)) {
                logger.debug("User {} authorized for transaction {} via authorities", userId, normalizedTxnCode);
                return true;
            }

            // Check external RACF validation if configured
            if (racfValidationService.isExternalValidationEnabled()) {
                boolean racfAuthorized = racfValidationService.hasTransactionAccess(userId, normalizedTxnCode);
                if (racfAuthorized) {
                    logger.debug("User {} authorized for transaction {} via external RACF", userId, normalizedTxnCode);
                    return true;
                }
            }

            // Check transaction-specific business rules
            if (hasTransactionSpecificAccess(securityContext, normalizedTxnCode)) {
                logger.debug("User {} authorized for transaction {} via business rules", userId, normalizedTxnCode);
                return true;
            }

            logger.warn("User {} denied access to transaction {}", userId, normalizedTxnCode);
            return false;

        } catch (Exception e) {
            logger.error("Error validating transaction access for user {} and transaction {}", userId, normalizedTxnCode, e);
            return false;
        }
    }

    /**
     * Check if the security context allows access to the specified account.
     */
    public boolean canAccessAccount(SecurityContext securityContext, String accountNumber) {
        if (securityContext == null || !securityContext.isValid()) {
            logger.warn("Invalid security context for account access: {}", accountNumber);
            return false;
        }

        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            logger.warn("Invalid account number");
            return false;
        }

        String userId = securityContext.getUserId();

        try {
            // Administrators can access any account
            if (securityContext.isAdministrator()) {
                logger.debug("Admin user {} granted access to account {}", userId, accountNumber);
                return true;
            }

            // Check if user has general account access authority
            if (securityContext.hasAuthorityPattern("ACCOUNT.*") ||
                securityContext.hasAuthorityPattern("ACCOUNT.READ")) {
                logger.debug("User {} has general account access for {}", userId, accountNumber);
                return true;
            }

            // Check account-specific access rules
            if (hasAccountSpecificAccess(securityContext, accountNumber)) {
                logger.debug("User {} has specific access to account {}", userId, accountNumber);
                return true;
            }

            // External RACF validation for account access
            if (racfValidationService.isExternalValidationEnabled()) {
                String resourceName = "ACCOUNT." + accountNumber;
                boolean racfAuthorized = racfValidationService.checkResourceAccess(userId, resourceName, "READ");
                if (racfAuthorized) {
                    logger.debug("User {} authorized for account {} via external RACF", userId, accountNumber);
                    return true;
                }
            }

            logger.warn("User {} denied access to account {}", userId, accountNumber);
            return false;

        } catch (Exception e) {
            logger.error("Error validating account access for user {} and account {}", userId, accountNumber, e);
            return false;
        }
    }

    /**
     * Check if the security context allows the specified operation (READ, WRITE, UPDATE, DELETE).
     */
    public boolean canPerformOperation(SecurityContext securityContext, String operation, String resource) {
        if (securityContext == null || !securityContext.isValid()) {
            return false;
        }

        if (operation == null || resource == null) {
            return false;
        }

        String normalizedOperation = operation.trim().toUpperCase();
        String normalizedResource = resource.trim().toUpperCase();
        String userId = securityContext.getUserId();

        try {
            // Check for admin privileges
            if (securityContext.isAdministrator()) {
                logger.debug("Admin user {} granted {} access to {}", userId, normalizedOperation, normalizedResource);
                return true;
            }

            // Check specific operation authority
            String operationAuthority = normalizedResource + "." + normalizedOperation;
            if (securityContext.hasAuthorityPattern(operationAuthority)) {
                logger.debug("User {} authorized for {} on {} via specific authority", userId, normalizedOperation, normalizedResource);
                return true;
            }

            // Check wildcard operation authority
            String wildcardAuthority = normalizedResource + ".*";
            if (securityContext.hasAuthorityPattern(wildcardAuthority)) {
                logger.debug("User {} authorized for {} on {} via wildcard authority", userId, normalizedOperation, normalizedResource);
                return true;
            }

            // Special handling for read-only users
            if (securityContext.isReadOnly() && "READ".equals(normalizedOperation)) {
                logger.debug("Read-only user {} granted read access to {}", userId, normalizedResource);
                return true;
            }

            // External RACF validation
            if (racfValidationService.isExternalValidationEnabled()) {
                boolean racfAuthorized = racfValidationService.checkResourceAccess(userId, normalizedResource, normalizedOperation);
                if (racfAuthorized) {
                    logger.debug("User {} authorized for {} on {} via external RACF", userId, normalizedOperation, normalizedResource);
                    return true;
                }
            }

            logger.warn("User {} denied {} access to {}", userId, normalizedOperation, normalizedResource);
            return false;

        } catch (Exception e) {
            logger.error("Error validating operation access for user {}, operation {}, resource {}", userId, normalizedOperation, normalizedResource, e);
            return false;
        }
    }

    /**
     * Validate that the security context meets minimum requirements for any transaction.
     */
    public void validateMinimumSecurity(SecurityContext securityContext) {
        if (securityContext == null) {
            throw new SecurityValidationException("Security context is required");
        }

        if (!securityContext.isValid()) {
            throw new SecurityValidationException("Security context is invalid");
        }

        if (securityContext.isExpired()) {
            throw new SecurityValidationException("Security token has expired");
        }

        String userId = securityContext.getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            throw new SecurityValidationException("User ID is required");
        }

        // Check if user has basic IMS Connect access
        if (!securityContext.hasAuthorityPattern("IMS.CONNECT")) {
            throw new SecurityValidationException("User does not have basic IMS Connect access");
        }
    }

    private boolean hasTransactionSpecificAccess(SecurityContext securityContext, String transactionCode) {
        String userId = securityContext.getUserId();

        // Business-specific transaction access rules
        return switch (transactionCode) {
            case "BALINQ" -> {
                // Balance inquiry - most users can do this
                yield securityContext.isOperator() || securityContext.isReadOnly() ||
                      securityContext.hasAuthorityPattern("BANKING.INQUIRY");
            }
            case "TRANSFER" -> {
                // Fund transfers - requires operator privileges
                yield securityContext.isOperator() &&
                      securityContext.hasAuthorityPattern("BANKING.TRANSFER");
            }
            case "DEPOSIT", "WITHDRAW" -> {
                // Cash operations - requires specific authority
                yield securityContext.isOperator() &&
                      securityContext.hasAuthorityPattern("BANKING.CASH");
            }
            case "ADMINRPT" -> {
                // Administrative reports - admin only
                yield securityContext.isAdministrator();
            }
            default -> {
                // Unknown transactions - require explicit authority
                yield securityContext.hasAuthorityPattern("IMS.TXN." + transactionCode);
            }
        };
    }

    private boolean hasAccountSpecificAccess(SecurityContext securityContext, String accountNumber) {
        // Example business rules for account access
        String userId = securityContext.getUserId();

        // Customer service representatives might have access to accounts in their branch
        if (securityContext.isMemberOf("CSR")) {
            String branchCode = extractBranchCode(accountNumber);
            String userBranch = extractUserBranch(userId);
            return branchCode != null && branchCode.equals(userBranch);
        }

        // Account managers might have access to their assigned accounts
        if (securityContext.isMemberOf("ACCOUNT_MGR")) {
            return isAssignedAccount(userId, accountNumber);
        }

        return false;
    }

    private String extractBranchCode(String accountNumber) {
        // Extract branch code from account number (first 3 digits in this example)
        if (accountNumber != null && accountNumber.length() >= 3) {
            return accountNumber.substring(0, 3);
        }
        return null;
    }

    private String extractUserBranch(String userId) {
        // Extract user's branch from user ID (assume last 3 characters)
        if (userId != null && userId.length() >= 3) {
            return userId.substring(userId.length() - 3);
        }
        return null;
    }

    private boolean isAssignedAccount(String userId, String accountNumber) {
        // In a real implementation, this would check a database or external system
        // For demo purposes, just return false
        return false;
    }
}
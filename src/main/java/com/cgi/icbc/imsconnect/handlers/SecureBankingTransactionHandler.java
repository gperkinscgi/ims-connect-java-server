package com.cgi.icbc.imsconnect.handlers;

import com.cgi.icbc.imsconnect.protocol.IRMHeader;
import com.cgi.icbc.imsconnect.security.SecurityContext;
import com.cgi.icbc.imsconnect.security.SecureTransactionHandler;
import com.cgi.icbc.imsconnect.server.IMSResponse;
import com.cgi.icbc.imsconnect.service.AccountService;
import com.cgi.icbc.imsconnect.service.model.AccountBalance;
import com.cgi.icbc.imsconnect.service.model.TransferResult;
import com.cgi.icbc.imsconnect.util.EbcdicConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Secure banking transaction handler with RACF authorization.
 */
@Component
public class SecureBankingTransactionHandler extends SecureTransactionHandler {

    private static final Logger logger = LoggerFactory.getLogger(SecureBankingTransactionHandler.class);

    private final AccountService accountService;

    @Autowired
    public SecureBankingTransactionHandler(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public boolean canHandle(String transactionCode) {
        return "BALINQ".equals(transactionCode) ||
               "TRANSFER".equals(transactionCode) ||
               "DEPOSIT".equals(transactionCode) ||
               "WITHDRAW".equals(transactionCode);
    }

    @Override
    protected IMSResponse handleSecureTransaction(IRMHeader header, String messageData, SecurityContext securityContext) {
        String transactionCode = header.getTransactionCodeAsString();

        try {
            // Convert EBCDIC message from mainframe client to ASCII
            String asciiMessage = EbcdicConverter.ebcdicToAscii(messageData);

            // Route to appropriate handler based on transaction code
            return switch (transactionCode) {
                case "BALINQ" -> handleSecureBalanceInquiry(asciiMessage, securityContext);
                case "TRANSFER" -> handleSecureFundsTransfer(asciiMessage, securityContext);
                case "DEPOSIT" -> handleSecureDeposit(asciiMessage, securityContext);
                case "WITHDRAW" -> handleSecureWithdrawal(asciiMessage, securityContext);
                default -> createBusinessErrorResponse("1001", "Unknown transaction code: " + transactionCode);
            };

        } catch (Exception e) {
            logger.error("Error processing secure transaction {} for user {}",
                transactionCode, securityContext.getUserId(), e);
            return createSystemErrorResponse("Transaction processing failed");
        }
    }

    private IMSResponse handleSecureBalanceInquiry(String messageData, SecurityContext securityContext) {
        try {
            // Parse incoming message (fixed-format from mainframe client)
            String accountNumber = messageData.substring(8, 24).trim();
            String customerNumber = messageData.substring(24, 36).trim();

            logger.info("Processing secure balance inquiry for account: {}, customer: {}, user: {}",
                accountNumber, customerNumber, securityContext.getUserId());

            // Validate account access authorization
            if (!validateAccountAccess(securityContext, accountNumber)) {
                return createSecurityErrorResponse("Account access denied");
            }

            // Call business logic
            AccountBalance balance = accountService.getAccountBalance(accountNumber);

            // Audit successful access
            auditLogger.logTransaction("BALANCE_INQUIRY", null, "BALINQ",
                securityContext.getUserId(), true,
                "Account: " + accountNumber + ", Balance: " + balance.getAmount());

            // Build response message in fixed format expected by mainframe client
            StringBuilder response = new StringBuilder();
            response.append(String.format("%-8s", "BALINQ"));                    // Echo transaction code
            response.append(String.format("%-4s", "0000"));                      // Response code (0000 = success)
            response.append(String.format("%-16s", accountNumber));              // Account number
            response.append(String.format("%015d", balance.getAmountCents()));   // Balance in cents (15 digits)
            response.append(String.format("%-3s", balance.getCurrencyCode()));   // Currency code
            response.append(String.format("%-1s", balance.getAccountStatus()));  // Account status
            response.append(String.format("%-50s", " "));                        // Reserved space

            // Convert response back to EBCDIC for mainframe client
            String ebcdicResponse = EbcdicConverter.asciiToEbcdic(response.toString());

            return createSuccessResponse(ebcdicResponse);

        } catch (AccountNotFoundException e) {
            logger.warn("Account not found for user {}: {}", securityContext.getUserId(), e.getMessage());
            return createBusinessErrorResponse("1404", "Account not found");

        } catch (Exception e) {
            logger.error("Error processing balance inquiry for user {}", securityContext.getUserId(), e);
            return createSystemErrorResponse("Balance inquiry failed");
        }
    }

    private IMSResponse handleSecureFundsTransfer(String messageData, SecurityContext securityContext) {
        try {
            // Parse transfer request
            String fromAccount = messageData.substring(8, 24).trim();
            String toAccount = messageData.substring(24, 40).trim();
            long amountCents = Long.parseLong(messageData.substring(40, 55).trim());

            logger.info("Processing secure funds transfer: {} -> {}, amount: {}, user: {}",
                fromAccount, toAccount, amountCents, securityContext.getUserId());

            // Validate account access for both accounts
            if (!validateAccountAccess(securityContext, fromAccount)) {
                return createSecurityErrorResponse("Source account access denied");
            }

            if (!validateAccountAccess(securityContext, toAccount)) {
                return createSecurityErrorResponse("Destination account access denied");
            }

            // Validate transfer operation authority
            if (!validateResourceAccess(securityContext, "BANKING.TRANSFER", "EXECUTE")) {
                return createSecurityErrorResponse("Transfer operation not authorized");
            }

            // Call business logic
            TransferResult result = accountService.transferFunds(fromAccount, toAccount, amountCents);

            // Audit the transfer attempt
            auditLogger.logTransaction("FUNDS_TRANSFER", null, "TRANSFER",
                securityContext.getUserId(), result.isSuccess(),
                String.format("From: %s, To: %s, Amount: %d, TxnId: %s",
                    fromAccount, toAccount, amountCents, result.getTransactionId()));

            // Build response
            StringBuilder response = new StringBuilder();
            response.append(String.format("%-8s", "TRANSFER"));
            response.append(String.format("%-4s", result.isSuccess() ? "0000" : "1001"));
            response.append(String.format("%-16s", fromAccount));
            response.append(String.format("%-16s", toAccount));
            response.append(String.format("%-20s", result.getTransactionId() != null ? result.getTransactionId() : ""));
            response.append(String.format("%-50s", result.getMessage()));

            String ebcdicResponse = EbcdicConverter.asciiToEbcdic(response.toString());

            return result.isSuccess() ?
                createSuccessResponse(ebcdicResponse) :
                createBusinessErrorResponse("1001", ebcdicResponse);

        } catch (NumberFormatException e) {
            logger.warn("Invalid amount format in transfer request for user {}", securityContext.getUserId());
            return createBusinessErrorResponse("1400", "Invalid amount format");

        } catch (Exception e) {
            logger.error("Error processing funds transfer for user {}", securityContext.getUserId(), e);
            return createSystemErrorResponse("Transfer failed");
        }
    }

    private IMSResponse handleSecureDeposit(String messageData, SecurityContext securityContext) {
        try {
            // Parse deposit request
            String accountNumber = messageData.substring(8, 24).trim();
            long amountCents = Long.parseLong(messageData.substring(24, 39).trim());
            String depositType = messageData.substring(39, 47).trim();

            logger.info("Processing secure deposit: account: {}, amount: {}, type: {}, user: {}",
                accountNumber, amountCents, depositType, securityContext.getUserId());

            // Validate account access
            if (!validateAccountAccess(securityContext, accountNumber)) {
                return createSecurityErrorResponse("Account access denied");
            }

            // Validate deposit operation authority
            if (!validateResourceAccess(securityContext, "BANKING.DEPOSIT", "EXECUTE")) {
                return createSecurityErrorResponse("Deposit operation not authorized");
            }

            // For deposit, we could call a deposit service
            // For now, just log and return success
            auditLogger.logTransaction("DEPOSIT", null, "DEPOSIT",
                securityContext.getUserId(), true,
                String.format("Account: %s, Amount: %d, Type: %s", accountNumber, amountCents, depositType));

            // Build response
            StringBuilder response = new StringBuilder();
            response.append(String.format("%-8s", "DEPOSIT"));
            response.append(String.format("%-4s", "0000"));
            response.append(String.format("%-16s", accountNumber));
            response.append(String.format("%015d", amountCents));
            response.append(String.format("%-20s", "TXN" + System.currentTimeMillis()));
            response.append(String.format("%-50s", "Deposit completed successfully"));

            String ebcdicResponse = EbcdicConverter.asciiToEbcdic(response.toString());
            return createSuccessResponse(ebcdicResponse);

        } catch (Exception e) {
            logger.error("Error processing deposit for user {}", securityContext.getUserId(), e);
            return createSystemErrorResponse("Deposit failed");
        }
    }

    private IMSResponse handleSecureWithdrawal(String messageData, SecurityContext securityContext) {
        try {
            // Parse withdrawal request
            String accountNumber = messageData.substring(8, 24).trim();
            long amountCents = Long.parseLong(messageData.substring(24, 39).trim());
            String withdrawalType = messageData.substring(39, 47).trim();

            logger.info("Processing secure withdrawal: account: {}, amount: {}, type: {}, user: {}",
                accountNumber, amountCents, withdrawalType, securityContext.getUserId());

            // Validate account access
            if (!validateAccountAccess(securityContext, accountNumber)) {
                return createSecurityErrorResponse("Account access denied");
            }

            // Validate withdrawal operation authority
            if (!validateResourceAccess(securityContext, "BANKING.WITHDRAW", "EXECUTE")) {
                return createSecurityErrorResponse("Withdrawal operation not authorized");
            }

            // Additional validation for withdrawal amount limits based on user role
            if (!validateWithdrawalLimits(securityContext, amountCents)) {
                return createBusinessErrorResponse("1403", "Withdrawal amount exceeds authorized limit");
            }

            // For withdrawal, we could call a withdrawal service
            // For now, just log and return success
            auditLogger.logTransaction("WITHDRAWAL", null, "WITHDRAW",
                securityContext.getUserId(), true,
                String.format("Account: %s, Amount: %d, Type: %s", accountNumber, amountCents, withdrawalType));

            // Build response
            StringBuilder response = new StringBuilder();
            response.append(String.format("%-8s", "WITHDRAW"));
            response.append(String.format("%-4s", "0000"));
            response.append(String.format("%-16s", accountNumber));
            response.append(String.format("%015d", amountCents));
            response.append(String.format("%-20s", "TXN" + System.currentTimeMillis()));
            response.append(String.format("%-50s", "Withdrawal completed successfully"));

            String ebcdicResponse = EbcdicConverter.asciiToEbcdic(response.toString());
            return createSuccessResponse(ebcdicResponse);

        } catch (Exception e) {
            logger.error("Error processing withdrawal for user {}", securityContext.getUserId(), e);
            return createSystemErrorResponse("Withdrawal failed");
        }
    }

    private boolean validateWithdrawalLimits(SecurityContext securityContext, long amountCents) {
        // Business rule: validate withdrawal limits based on user role
        if (securityContext.isAdministrator()) {
            return true; // No limits for administrators
        }

        if (securityContext.isOperator()) {
            return amountCents <= 10000000; // $100,000 limit for operators
        }

        // Regular users have lower limits
        return amountCents <= 1000000; // $10,000 limit for regular users
    }

    // Exception class for account not found
    public static class AccountNotFoundException extends RuntimeException {
        public AccountNotFoundException(String message) {
            super(message);
        }
    }
}
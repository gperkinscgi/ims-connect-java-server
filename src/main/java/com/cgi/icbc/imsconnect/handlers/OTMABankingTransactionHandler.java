package com.cgi.icbc.imsconnect.handlers;

import com.cgi.icbc.imsconnect.protocol.OTMAMessage;
import com.cgi.icbc.imsconnect.server.OTMATransactionHandler;
import com.cgi.icbc.imsconnect.service.AccountService;
import com.cgi.icbc.imsconnect.service.model.AccountBalance;
import com.cgi.icbc.imsconnect.util.EbcdicConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * OTMA-aware banking transaction handler.
 * Demonstrates how to handle OTMA messages with enhanced features like conversation support.
 */
@Component
public class OTMABankingTransactionHandler implements OTMATransactionHandler {

    private static final Logger logger = LoggerFactory.getLogger(OTMABankingTransactionHandler.class);

    private final AccountService accountService;

    @Autowired
    public OTMABankingTransactionHandler(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public boolean canHandle(OTMAMessage message) {
        String transactionCode = message.getTransactionCode();
        return "BALINQ".equals(transactionCode) ||
               "TRANSFER".equals(transactionCode) ||
               "DEPOSIT".equals(transactionCode) ||
               "WITHDRAW".equals(transactionCode) ||
               "CONVERS".equals(transactionCode); // Conversational banking
    }

    @Override
    public OTMAMessage handleOTMATransaction(OTMAMessage request) {
        String transactionCode = request.getTransactionCode();

        try {
            logger.debug("Processing OTMA banking transaction: {} for client: {}",
                transactionCode, request.getClientId());

            // Handle conversational vs non-conversational transactions
            if (request.isConversational()) {
                return handleConversationalTransaction(request);
            } else {
                return handleStandardTransaction(request);
            }

        } catch (Exception e) {
            logger.error("Error processing OTMA banking transaction: {}", transactionCode, e);
            return createErrorResponse(request, "Transaction processing failed: " + e.getMessage());
        }
    }

    private OTMAMessage handleStandardTransaction(OTMAMessage request) {
        String transactionCode = request.getTransactionCode();

        return switch (transactionCode) {
            case "BALINQ" -> handleBalanceInquiry(request);
            case "TRANSFER" -> handleFundsTransfer(request);
            case "DEPOSIT" -> handleDeposit(request);
            case "WITHDRAW" -> handleWithdrawal(request);
            default -> createErrorResponse(request, "Unknown transaction code: " + transactionCode);
        };
    }

    private OTMAMessage handleConversationalTransaction(OTMAMessage request) {
        logger.debug("Handling conversational transaction: conversation ID = {}", request.getConversationId());

        // Get message data
        byte[] messageData = request.getMessageData();
        String asciiMessage = EbcdicConverter.ebcdicToAscii(messageData);

        if (request.isFirstMessage()) {
            // First message in conversation - initialize
            return startConversationalBanking(request, asciiMessage);
        } else {
            // Continue conversation
            return continueConversationalBanking(request, asciiMessage);
        }
    }

    private OTMAMessage startConversationalBanking(OTMAMessage request, String messageData) {
        // Parse initial request
        String command = messageData.trim().toUpperCase();

        StringBuilder response = new StringBuilder();
        response.append("WELCOME TO CONVERSATIONAL BANKING\n");
        response.append("CONVERSATION ID: ").append(request.getConversationId()).append("\n");
        response.append("AVAILABLE COMMANDS:\n");
        response.append("  BALANCE <account> - Check account balance\n");
        response.append("  TRANSFER <from> <to> <amount> - Transfer funds\n");
        response.append("  HISTORY <account> - View transaction history\n");
        response.append("  EXIT - End conversation\n");
        response.append("ENTER COMMAND: ");

        return createConversationalResponse(request, response.toString(), false);
    }

    private OTMAMessage continueConversationalBanking(OTMAMessage request, String messageData) {
        String command = messageData.trim().toUpperCase();
        String[] parts = command.split("\\s+");

        if (parts.length == 0) {
            return createConversationalResponse(request, "INVALID COMMAND. TRY AGAIN: ", false);
        }

        String action = parts[0];

        return switch (action) {
            case "BALANCE" -> {
                if (parts.length < 2) {
                    yield createConversationalResponse(request, "USAGE: BALANCE <account>\nENTER COMMAND: ", false);
                }
                yield handleConversationalBalance(request, parts[1]);
            }
            case "TRANSFER" -> {
                if (parts.length < 4) {
                    yield createConversationalResponse(request, "USAGE: TRANSFER <from> <to> <amount>\nENTER COMMAND: ", false);
                }
                yield handleConversationalTransfer(request, parts[1], parts[2], parts[3]);
            }
            case "HISTORY" -> {
                if (parts.length < 2) {
                    yield createConversationalResponse(request, "USAGE: HISTORY <account>\nENTER COMMAND: ", false);
                }
                yield handleConversationalHistory(request, parts[1]);
            }
            case "EXIT" -> createConversationalResponse(request, "GOODBYE. CONVERSATION ENDED.", true);
            default -> createConversationalResponse(request, "UNKNOWN COMMAND: " + action + "\nENTER COMMAND: ", false);
        };
    }

    private OTMAMessage handleConversationalBalance(OTMAMessage request, String accountNumber) {
        try {
            AccountBalance balance = accountService.getAccountBalance(accountNumber);
            String response = String.format("ACCOUNT %s BALANCE: %s %s\nENTER COMMAND: ",
                accountNumber, balance.getAmount(), balance.getCurrencyCode());
            return createConversationalResponse(request, response, false);
        } catch (Exception e) {
            return createConversationalResponse(request,
                "ERROR: " + e.getMessage() + "\nENTER COMMAND: ", false);
        }
    }

    private OTMAMessage handleConversationalTransfer(OTMAMessage request, String from, String to, String amountStr) {
        try {
            long amountCents = (long) (Double.parseDouble(amountStr) * 100);
            // In a real implementation, call transfer service
            String response = String.format("TRANSFER OF $%.2f FROM %s TO %s COMPLETED\nENTER COMMAND: ",
                amountCents / 100.0, from, to);
            return createConversationalResponse(request, response, false);
        } catch (NumberFormatException e) {
            return createConversationalResponse(request,
                "INVALID AMOUNT: " + amountStr + "\nENTER COMMAND: ", false);
        } catch (Exception e) {
            return createConversationalResponse(request,
                "TRANSFER FAILED: " + e.getMessage() + "\nENTER COMMAND: ", false);
        }
    }

    private OTMAMessage handleConversationalHistory(OTMAMessage request, String accountNumber) {
        // In a real implementation, retrieve transaction history
        String response = String.format("TRANSACTION HISTORY FOR ACCOUNT %s:\n", accountNumber) +
                         "2023-01-15  DEPOSIT    +$1,000.00\n" +
                         "2023-01-10  WITHDRAWAL -$500.00\n" +
                         "2023-01-05  TRANSFER   -$250.00\n" +
                         "ENTER COMMAND: ";
        return createConversationalResponse(request, response, false);
    }

    private OTMAMessage handleBalanceInquiry(OTMAMessage request) {
        byte[] messageData = request.getMessageData();
        String asciiMessage = EbcdicConverter.ebcdicToAscii(messageData);

        // Parse account number from message
        String accountNumber = asciiMessage.substring(8, 24).trim();

        try {
            AccountBalance balance = accountService.getAccountBalance(accountNumber);

            // Build response in fixed format
            StringBuilder response = new StringBuilder();
            response.append(String.format("%-8s", "BALINQ"));
            response.append(String.format("%-4s", "0000"));
            response.append(String.format("%-16s", accountNumber));
            response.append(String.format("%015d", balance.getAmountCents()));
            response.append(String.format("%-3s", balance.getCurrencyCode()));
            response.append(String.format("%-1s", balance.getAccountStatus()));
            response.append(String.format("%-50s", " "));

            byte[] responseData = EbcdicConverter.asciiToEbcdic(response.toString());
            return OTMAMessage.createResponse(request, responseData);

        } catch (Exception e) {
            return createErrorResponse(request, "Balance inquiry failed: " + e.getMessage());
        }
    }

    private OTMAMessage handleFundsTransfer(OTMAMessage request) {
        // Similar to handleBalanceInquiry but for transfers
        // Implementation would parse transfer details and process
        return createErrorResponse(request, "Transfer not implemented in this example");
    }

    private OTMAMessage handleDeposit(OTMAMessage request) {
        return createErrorResponse(request, "Deposit not implemented in this example");
    }

    private OTMAMessage handleWithdrawal(OTMAMessage request) {
        return createErrorResponse(request, "Withdrawal not implemented in this example");
    }

    private OTMAMessage createConversationalResponse(OTMAMessage request, String responseText, boolean isLast) {
        byte[] responseData = EbcdicConverter.asciiToEbcdic(responseText);
        OTMAMessage response = OTMAMessage.createResponse(request, responseData);

        // Set conversational flags
        response.getOtmaHeader().setConversationId(request.getConversationId());
        response.getOtmaHeader().setMessageType(com.cgi.icbc.imsconnect.protocol.OTMAHeader.MSG_TYPE_CONVERSATION);
        response.getOtmaHeader().setLastMessage(isLast);
        response.getOtmaHeader().setContinueConversation(!isLast);

        return response;
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
        return 100; // High priority for banking transactions
    }

    @Override
    public boolean supportsConversational() {
        return true; // This handler supports conversational transactions
    }

    @Override
    public String[] getSupportedTransactionCodes() {
        return new String[]{"BALINQ", "TRANSFER", "DEPOSIT", "WITHDRAW", "CONVERS"};
    }

    @Override
    public String[] getSupportedLtermNames() {
        return new String[]{"BANK01", "BANK02", "BANKTST"}; // Banking LTERMs
    }
}
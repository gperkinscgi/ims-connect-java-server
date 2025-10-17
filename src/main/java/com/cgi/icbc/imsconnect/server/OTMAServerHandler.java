package com.cgi.icbc.imsconnect.server;

import com.cgi.icbc.imsconnect.conversation.ConversationManager;
import com.cgi.icbc.imsconnect.protocol.OTMAMessage;
import com.cgi.icbc.imsconnect.security.AuditLogger;
import com.cgi.icbc.imsconnect.security.RACFSecurityParser;
import com.cgi.icbc.imsconnect.security.SecurityContext;
import com.cgi.icbc.imsconnect.security.TransactionSecurityValidator;
import com.cgi.icbc.imsconnect.transaction.IMSTransactionManager;
import com.cgi.icbc.imsconnect.transaction.IMSTransactionResult;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Comparator;

/**
 * Main server handler for OTMA messages.
 * Routes messages to appropriate transaction handlers and manages conversations.
 */
@Component
public class OTMAServerHandler extends SimpleChannelInboundHandler<OTMAMessage> {

    private static final Logger logger = LoggerFactory.getLogger(OTMAServerHandler.class);

    private final List<OTMATransactionHandler> transactionHandlers;
    private final IMSTransactionManager transactionManager;
    private final ConversationManager conversationManager;
    private final RACFSecurityParser securityParser;
    private final TransactionSecurityValidator securityValidator;
    private final AuditLogger auditLogger;

    @Autowired
    public OTMAServerHandler(List<OTMATransactionHandler> transactionHandlers,
                           IMSTransactionManager transactionManager,
                           ConversationManager conversationManager,
                           RACFSecurityParser securityParser,
                           TransactionSecurityValidator securityValidator,
                           AuditLogger auditLogger) {
        this.transactionHandlers = transactionHandlers;
        this.transactionManager = transactionManager;
        this.conversationManager = conversationManager;
        this.securityParser = securityParser;
        this.securityValidator = securityValidator;
        this.auditLogger = auditLogger;

        // Sort handlers by priority (highest first)
        this.transactionHandlers.sort(Comparator.comparingInt(OTMATransactionHandler::getPriority).reversed());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, OTMAMessage message) throws Exception {
        logger.debug("Received OTMA message: {}", message);

        try {
            // Process the transaction with proper lifecycle management
            IMSTransactionResult result = transactionManager.processTransaction(message, (transaction, request) -> {
                // Find appropriate handler
                OTMATransactionHandler handler = findHandler(request);
                if (handler == null) {
                    throw new IllegalArgumentException("No handler found for transaction: " + request.getTransactionCode());
                }

                // Validate security if required
                if (handler.requiresSecurityValidation()) {
                    validateSecurity(request);
                }

                // Process the transaction
                return handler.handleOTMATransaction(request);
            });

            // Send response if successful
            if (result.isSuccess() && result.getResponse() != null) {
                ctx.writeAndFlush(result.getResponse());
                logger.debug("Sent OTMA response for transaction: {}", result.getTransaction().getTransactionId());
            } else if (!result.isSuccess()) {
                // Send error response
                OTMAMessage errorResponse = createErrorResponse(message, result.getError());
                ctx.writeAndFlush(errorResponse);
                logger.error("Transaction failed: {}", result.getError().getMessage());
            }

        } catch (Exception e) {
            logger.error("Error processing OTMA message: {}", message, e);

            // Send error response
            OTMAMessage errorResponse = createErrorResponse(message, e);
            ctx.writeAndFlush(errorResponse);

            // Audit the error
            auditLogger.logTransaction("OTMA_MESSAGE_ERROR",
                message.getClientId(), message.getTransactionCode(), null, false,
                "Error: " + e.getMessage());
        }
    }

    private OTMATransactionHandler findHandler(OTMAMessage message) {
        return transactionHandlers.stream()
            .filter(handler -> handler.canHandle(message))
            .findFirst()
            .orElse(null);
    }

    private void validateSecurity(OTMAMessage message) {
        try {
            // Parse security context from IRM header
            SecurityContext securityContext = securityParser.parseSecurityContext(message.getIrmHeader());

            // Validate minimum security requirements
            securityValidator.validateMinimumSecurity(securityContext);

            // Check transaction authorization
            if (!securityValidator.canExecuteTransaction(securityContext, message.getTransactionCode())) {
                auditLogger.logSecurityEvent("UNAUTHORIZED_OTMA_TRANSACTION",
                    message.getClientId(), securityContext.getUserId(), null, false,
                    "Transaction: " + message.getTransactionCode());
                throw new SecurityException("Unauthorized transaction: " + message.getTransactionCode());
            }

            logger.debug("Security validation passed for OTMA transaction: {}", message.getTransactionCode());

        } catch (Exception e) {
            logger.error("Security validation failed for OTMA message: {}", message, e);
            throw new SecurityException("Security validation failed", e);
        }
    }

    private OTMAMessage createErrorResponse(OTMAMessage requestMessage, Throwable error) {
        try {
            // Build error response data
            String errorMessage = error != null ? error.getMessage() : "Unknown error";
            String errorResponse = String.format("%-8s%-4s%-80s%-20s",
                "ERROR", "9999", errorMessage, " ");

            byte[] responseData = com.cgi.icbc.imsconnect.util.EbcdicConverter.asciiToEbcdic(errorResponse);

            // Create response message
            OTMAMessage response = OTMAMessage.createResponse(requestMessage, responseData);

            return response;

        } catch (Exception e) {
            logger.error("Failed to create error response", e);

            // Return minimal error response
            try {
                byte[] minimalError = com.cgi.icbc.imsconnect.util.EbcdicConverter.asciiToEbcdic("ERROR");
                return OTMAMessage.createResponse(requestMessage, minimalError);
            } catch (Exception e2) {
                logger.error("Failed to create minimal error response", e2);
                return null;
            }
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("OTMA client connected: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("OTMA client disconnected: {}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in OTMA server handler", cause);
        ctx.close();
    }
}
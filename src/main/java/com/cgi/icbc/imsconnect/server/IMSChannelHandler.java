package com.cgi.icbc.imsconnect.server;

import com.cgi.icbc.imsconnect.model.*;
import com.cgi.icbc.imsconnect.protocol.IRMHeader;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Main channel handler for IMS Connect protocol logic.
 * Routes incoming requests to the appropriate handler methods based on transaction type.
 */
public class IMSChannelHandler extends SimpleChannelInboundHandler<IMSRequest> {

    private static final Logger logger = LoggerFactory.getLogger(IMSChannelHandler.class);

    private final IMSConnectServerHandler messageHandler;
    private final ServerConfiguration config;
    private String connectionId;

    public IMSChannelHandler(IMSConnectServerHandler messageHandler, ServerConfiguration config) {
        this.messageHandler = messageHandler;
        this.config = config;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        connectionId = ctx.channel().id().asShortText();
        logger.info("IMS Connect client connected: {}", connectionId);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("IMS Connect client disconnected: {}", connectionId);
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IMSRequest request) throws Exception {
        logger.debug("Processing request: {}", request);

        try {
            // Validate credentials if present
            if (!validateCredentials(request)) {
                sendErrorResponse(ctx, 8, 1, "Authentication failed");
                return;
            }

            // Route based on transaction type
            switch (request.getTransactionType()) {
                case SEND_RECEIVE:
                    handleSendReceive(ctx, request);
                    break;
                case SEND_ONLY:
                    handleSendOnly(ctx, request);
                    break;
                case RECV_ONLY:
                    handleRecvOnly(ctx, request);
                    break;
                case ACK:
                    handleAck(ctx, request);
                    break;
                case NAK:
                    handleNak(ctx, request);
                    break;
                case DEALLOCATE:
                    handleDeallocate(ctx, request);
                    break;
                case CANCEL_TIMER:
                    handleCancelTimer(ctx, request);
                    break;
                case RESUME_TPIPE:
                    handleResumeTpipe(ctx, request);
                    break;
                default:
                    sendErrorResponse(ctx, 12, 1, "Unsupported transaction type");
            }

        } catch (Exception e) {
            logger.error("Error processing request: {}", request, e);
            sendErrorResponse(ctx, 16, 1, "Internal processing error");
        }
    }

    private void handleSendReceive(ChannelHandlerContext ctx, IMSRequest request) {
        logger.debug("Handling send-receive transaction: {}", request.getTransactionCode());

        CompletableFuture<IMSResponse> responseFuture = messageHandler.processTransaction(request);

        responseFuture.thenAccept(response -> {
            // Add generated client ID if requested
            if (isClientIdRequested(request) && response.getGeneratedClientId().isEmpty()) {
                String generatedId = messageHandler.generateClientId();
                response = IMSResponse.success()
                        .withDataSegments(response.getDataSegments())
                        .withGeneratedClientId(generatedId)
                        .withModName(response.getModName().orElse(null))
                        .build();
            }

            ctx.writeAndFlush(response);
            logger.debug("Sent response for transaction: {}", request.getTransactionCode());

        }).exceptionally(throwable -> {
            logger.error("Error processing transaction: {}", request.getTransactionCode(), throwable);
            sendErrorResponse(ctx, 20, 1, "Transaction processing failed");
            return null;
        });
    }

    private void handleSendOnly(ChannelHandlerContext ctx, IMSRequest request) {
        logger.debug("Handling send-only transaction: {}", request.getTransactionCode());

        try {
            String clientId = request.getClientId();
            if (clientId.trim().isEmpty()) {
                clientId = messageHandler.generateClientId();
            }

            messageHandler.handleAsyncMessage(request, clientId);

            // Send ACK response based on message type
            IMSResponse ackResponse;
            if (requiresAckResponse(request)) {
                ackResponse = IMSResponse.ack()
                        .withGeneratedClientId(clientId)
                        .build();
            } else {
                // Simple acknowledgment without data
                ackResponse = IMSResponse.ack().build();
            }

            ctx.writeAndFlush(ackResponse);
            logger.debug("Sent ACK for send-only transaction: {}", request.getTransactionCode());

        } catch (Exception e) {
            logger.error("Error handling send-only transaction", e);
            sendErrorResponse(ctx, 24, 1, "Send-only processing failed");
        }
    }

    private void handleRecvOnly(ChannelHandlerContext ctx, IMSRequest request) {
        logger.debug("Handling recv-only transaction for client: {}", request.getClientId());

        try {
            String clientId = request.getClientId();
            Optional<IMSResponse> response = messageHandler.pollResponse(clientId);

            if (response.isPresent()) {
                ctx.writeAndFlush(response.get());
                logger.debug("Sent queued response for client: {}", clientId);
            } else {
                // No messages available - send empty response
                IMSResponse emptyResponse = IMSResponse.success().build();
                ctx.writeAndFlush(emptyResponse);
                logger.debug("No messages available for client: {}", clientId);
            }

        } catch (Exception e) {
            logger.error("Error handling recv-only transaction", e);
            sendErrorResponse(ctx, 28, 1, "Recv-only processing failed");
        }
    }

    private void handleAck(ChannelHandlerContext ctx, IMSRequest request) {
        logger.debug("Handling ACK from client: {}", request.getClientId());

        try {
            messageHandler.acknowledgeMessage(request.getClientId(), null);
            // ACK messages typically don't require a response
        } catch (Exception e) {
            logger.error("Error handling ACK", e);
        }
    }

    private void handleNak(ChannelHandlerContext ctx, IMSRequest request) {
        logger.debug("Handling NAK from client: {}", request.getClientId());

        try {
            // Extract NAK reason from header
            int reasonCode = request.getHeader().getNakReasonCode();
            boolean retainMessage = (request.getHeader().getCommunicationFlags() & IRMHeader.F0_SYNC_NAK) != 0;

            messageHandler.negativeAcknowledge(request.getClientId(), null, reasonCode, retainMessage);

        } catch (Exception e) {
            logger.error("Error handling NAK", e);
        }
    }

    private void handleDeallocate(ChannelHandlerContext ctx, IMSRequest request) {
        logger.debug("Handling deallocate request for client: {}", request.getClientId());

        try {
            // Send success response and close connection
            IMSResponse response = IMSResponse.success().build();
            ctx.writeAndFlush(response).addListener(future -> {
                logger.info("Deallocating connection for client: {}", request.getClientId());
                ctx.close();
            });

        } catch (Exception e) {
            logger.error("Error handling deallocate", e);
            ctx.close();
        }
    }

    private void handleCancelTimer(ChannelHandlerContext ctx, IMSRequest request) {
        logger.debug("Handling cancel timer request for client: {}", request.getClientId());

        try {
            // Implementation would cancel timers for the specified client
            // For now, just send success response
            IMSResponse response = IMSResponse.success().build();
            ctx.writeAndFlush(response);

        } catch (Exception e) {
            logger.error("Error handling cancel timer", e);
            sendErrorResponse(ctx, 32, 1, "Cancel timer failed");
        }
    }

    private void handleResumeTpipe(ChannelHandlerContext ctx, IMSRequest request) {
        logger.debug("Handling resume tpipe request for client: {}", request.getClientId());

        // Resume tpipe is similar to recv-only but may have different semantics
        handleRecvOnly(ctx, request);
    }

    private boolean validateCredentials(IMSRequest request) {
        String userId = request.getUserId();
        String groupId = request.getGroupId();

        // Skip validation if no credentials provided
        if (userId.trim().isEmpty()) {
            return true;
        }

        // Note: password is not easily extractable from current IRMHeader implementation
        // In a real implementation, you'd need to access the password field
        return messageHandler.validateCredentials(userId, groupId, "");
    }

    private boolean isClientIdRequested(IMSRequest request) {
        return (request.getHeader().getUserFlags1() & 0x02) != 0; // IRMF1CIDREQ flag
    }

    private boolean requiresAckResponse(IMSRequest request) {
        byte msgType = request.getHeader().getMessageType();
        return msgType == IRMHeader.MSG_SEND_ONLY_ACK || msgType == IRMHeader.MSG_SYNC_RESP_ACK;
    }

    private void sendErrorResponse(ChannelHandlerContext ctx, int returnCode, int reasonCode, String message) {
        IMSResponse errorResponse = IMSResponse.error(returnCode, reasonCode, message).build();
        ctx.writeAndFlush(errorResponse);
        logger.debug("Sent error response: {} - {}", returnCode, message);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.ALL_IDLE) {
                logger.info("Connection idle timeout, closing: {}", connectionId);
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in channel handler for connection: {}", connectionId, cause);
        ctx.close();
    }
}
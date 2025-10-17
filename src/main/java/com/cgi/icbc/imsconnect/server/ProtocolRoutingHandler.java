package com.cgi.icbc.imsconnect.server;

import com.cgi.icbc.imsconnect.protocol.IRMHeader;
import com.cgi.icbc.imsconnect.protocol.OTMAMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Protocol routing handler that determines whether to use legacy IMS Connect
 * or OTMA message processing based on message headers.
 */
public class ProtocolRoutingHandler extends SimpleChannelInboundHandler<OTMAMessage> {

    private static final Logger logger = LoggerFactory.getLogger(ProtocolRoutingHandler.class);

    private final IMSConnectServerHandler legacyHandler;
    private final OTMAServerHandler otmaHandler;

    public ProtocolRoutingHandler(IMSConnectServerHandler legacyHandler, OTMAServerHandler otmaHandler) {
        this.legacyHandler = legacyHandler;
        this.otmaHandler = otmaHandler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, OTMAMessage message) throws Exception {
        try {
            // Check if this is a full OTMA message or legacy IMS Connect message
            IRMHeader irmHeader = message.getIrmHeader();

            if (irmHeader.hasFlag(IRMHeader.F5_NO_OTMA) || message.getOtmaHeader() == null) {
                // Legacy IMS Connect message - convert to legacy format and process
                logger.debug("Routing message to legacy IMS Connect handler");
                processLegacyMessage(ctx, message);
            } else {
                // Full OTMA message - process with OTMA handler
                logger.debug("Routing message to OTMA handler");
                otmaHandler.channelRead0(ctx, message);
            }

        } catch (Exception e) {
            logger.error("Error routing message", e);
            ctx.fireExceptionCaught(e);
        }
    }

    private void processLegacyMessage(ChannelHandlerContext ctx, OTMAMessage otmaMessage) {
        try {
            // Convert OTMA message to legacy format for processing
            IRMHeader irmHeader = otmaMessage.getIrmHeader();
            byte[] messageData = otmaMessage.getMessageData();

            // Create legacy message structure
            LegacyIMSMessage legacyMessage = new LegacyIMSMessage(irmHeader, messageData);

            // Process with legacy handler
            // Note: We need to adapt the interface since legacy handler expects different format
            if (legacyHandler instanceof DefaultIMSServerHandler) {
                DefaultIMSServerHandler defaultHandler = (DefaultIMSServerHandler) legacyHandler;
                defaultHandler.processLegacyMessage(ctx, legacyMessage);
            } else {
                logger.warn("Legacy handler does not support legacy message processing: {}",
                    legacyHandler.getClass().getSimpleName());
                ctx.fireExceptionCaught(new UnsupportedOperationException(
                    "Legacy handler does not support legacy message processing"));
            }

        } catch (Exception e) {
            logger.error("Error processing legacy message", e);
            ctx.fireExceptionCaught(e);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("Protocol routing handler active: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("Protocol routing handler inactive: {}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in protocol routing handler", cause);
        super.exceptionCaught(ctx, cause);
    }

    /**
     * Simple wrapper for legacy IMS messages.
     */
    public static class LegacyIMSMessage {
        private final IRMHeader irmHeader;
        private final byte[] messageData;

        public LegacyIMSMessage(IRMHeader irmHeader, byte[] messageData) {
            this.irmHeader = irmHeader;
            this.messageData = messageData;
        }

        public IRMHeader getIrmHeader() {
            return irmHeader;
        }

        public byte[] getMessageData() {
            return messageData;
        }
    }
}
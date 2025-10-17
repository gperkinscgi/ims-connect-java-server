package com.cgi.icbc.imsconnect.server;

import com.cgi.icbc.imsconnect.protocol.OTMAMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty encoder for outgoing OTMA messages.
 * Converts OTMAMessage objects into binary protocol format.
 */
public class OTMAMessageEncoder extends MessageToByteEncoder<OTMAMessage> {

    private static final Logger logger = LoggerFactory.getLogger(OTMAMessageEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, OTMAMessage message, ByteBuf out) throws Exception {
        try {
            logger.debug("Encoding OTMA message: {}", message);

            // Validate message before encoding
            if (!message.isValid()) {
                throw new ProtocolException("Invalid OTMA message structure");
            }

            // Write the complete OTMA message to the output buffer
            message.writeTo(out);

            logger.debug("Successfully encoded OTMA message of {} bytes", message.calculateTotalLength());

        } catch (Exception e) {
            logger.error("Failed to encode OTMA message: {}", message, e);
            throw new ProtocolException("Failed to encode OTMA message", e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in OTMA message encoder", cause);
        super.exceptionCaught(ctx, cause);
    }
}
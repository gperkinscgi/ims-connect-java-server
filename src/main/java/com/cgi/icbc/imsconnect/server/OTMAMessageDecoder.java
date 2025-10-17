package com.cgi.icbc.imsconnect.server;

import com.cgi.icbc.imsconnect.protocol.OTMAMessage;
import com.cgi.icbc.imsconnect.protocol.IRMHeader;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Netty decoder for incoming OTMA messages.
 * Handles both traditional IMS Connect messages and OTMA-enhanced messages.
 */
public class OTMAMessageDecoder extends ByteToMessageDecoder {

    private static final Logger logger = LoggerFactory.getLogger(OTMAMessageDecoder.class);

    private static final int MIN_IRM_HEADER_SIZE = 32;  // Minimum IRM header size
    private static final int MIN_OTMA_HEADER_SIZE = 20; // Minimum OTMA header size
    private static final int MAX_MESSAGE_SIZE = 10 * 1024 * 1024; // 10MB max message size

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Need at least 4 bytes to read total message length
        if (in.readableBytes() < 4) {
            return; // Wait for more data
        }

        // Mark reader index to reset if we don't have complete message
        in.markReaderIndex();

        try {
            // Read total message length (first 4 bytes)
            int totalLength = in.readInt();

            // Validate message length
            if (totalLength < MIN_IRM_HEADER_SIZE) {
                throw new ProtocolException("Message too small: " + totalLength);
            }

            if (totalLength > MAX_MESSAGE_SIZE) {
                throw new ProtocolException("Message too large: " + totalLength);
            }

            // Check if we have the complete message
            // We already read 4 bytes, so we need (totalLength - 4) more bytes
            if (in.readableBytes() < totalLength - 4) {
                in.resetReaderIndex(); // Reset to before we read the length
                return; // Wait for more data
            }

            logger.debug("Decoding message of {} bytes", totalLength);

            // Reset to start of message and parse
            in.resetReaderIndex();

            // Create a slice for the complete message
            ByteBuf messageBuffer = in.readSlice(totalLength);

            // Determine message type and decode accordingly
            OTMAMessage message = decodeMessage(messageBuffer);

            if (message != null) {
                out.add(message);
                logger.debug("Successfully decoded OTMA message: {}", message);
            }

        } catch (Exception e) {
            logger.error("Error decoding OTMA message", e);
            in.resetReaderIndex();
            throw new ProtocolException("Failed to decode OTMA message", e);
        }
    }

    private OTMAMessage decodeMessage(ByteBuf buffer) throws Exception {
        // Create a duplicate to avoid affecting the original buffer
        ByteBuf messageBuf = buffer.duplicate();

        // Parse IRM header first
        IRMHeader irmHeader = IRMHeader.parseFrom(messageBuf);

        // Check if this is an OTMA message
        if (irmHeader.hasFlag(IRMHeader.F5_NO_OTMA)) {
            // Legacy IMS Connect message without OTMA headers
            return decodeLegacyMessage(irmHeader, messageBuf);
        } else {
            // Full OTMA message with OTMA headers
            return OTMAMessage.parseFrom(buffer);
        }
    }

    private OTMAMessage decodeLegacyMessage(IRMHeader irmHeader, ByteBuf buffer) {
        logger.debug("Decoding legacy IMS Connect message without OTMA headers");

        try {
            // Create a minimal OTMA message structure for legacy compatibility
            OTMAMessage message = new OTMAMessage();
            message.setIrmHeader(irmHeader);

            // Create default OTMA header for legacy messages
            com.cgi.icbc.imsconnect.protocol.OTMAHeader otmaHeader =
                new com.cgi.icbc.imsconnect.protocol.OTMAHeader();
            otmaHeader.setLtermName(irmHeader.getDestinationIdAsString());
            otmaHeader.setMessageType(com.cgi.icbc.imsconnect.protocol.OTMAHeader.MSG_TYPE_TRANSACTION);
            message.setOtmaHeader(otmaHeader);

            // Read remaining data as message segments
            if (buffer.readableBytes() > 0) {
                byte[] remainingData = new byte[buffer.readableBytes()];
                buffer.readBytes(remainingData);

                // Create single message segment for legacy data
                message.setMessageData(remainingData);
            }

            return message;

        } catch (Exception e) {
            logger.error("Failed to decode legacy message", e);
            throw new ProtocolException("Failed to decode legacy message", e);
        }
    }

    /**
     * Check if buffer contains a complete message.
     */
    private boolean hasCompleteMessage(ByteBuf buffer) {
        if (buffer.readableBytes() < 4) {
            return false;
        }

        // Peek at the total length without advancing reader index
        int totalLength = buffer.getInt(buffer.readerIndex());

        return buffer.readableBytes() >= totalLength;
    }

    /**
     * Validate message structure without fully parsing.
     */
    private boolean isValidMessageStructure(ByteBuf buffer) {
        if (buffer.readableBytes() < MIN_IRM_HEADER_SIZE) {
            return false;
        }

        try {
            // Save reader index
            int readerIndex = buffer.readerIndex();

            // Read and validate total length
            int totalLength = buffer.readInt();
            if (totalLength < MIN_IRM_HEADER_SIZE || totalLength > MAX_MESSAGE_SIZE) {
                return false;
            }

            // Check IRM header length
            short irmLength = buffer.readShort();
            if (irmLength < MIN_IRM_HEADER_SIZE) {
                return false;
            }

            // Basic structure validation passed
            buffer.readerIndex(readerIndex); // Restore reader index
            return true;

        } catch (Exception e) {
            logger.debug("Message structure validation failed", e);
            return false;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in OTMA message decoder", cause);
        super.exceptionCaught(ctx, cause);
    }
}
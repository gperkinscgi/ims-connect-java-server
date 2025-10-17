package com.cgi.icbc.imsconnect.server;

import com.cgi.icbc.imsconnect.model.IMSRequest;
import com.cgi.icbc.imsconnect.model.MessageSegment;
import com.cgi.icbc.imsconnect.model.TransactionType;
import com.cgi.icbc.imsconnect.protocol.IRMHeader;
import com.cgi.icbc.imsconnect.protocol.MessageSegmentParser;
import com.cgi.icbc.imsconnect.protocol.ProtocolException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Netty decoder for incoming IMS Connect messages.
 * Decodes the binary protocol into IMSRequest objects.
 */
public class IMSMessageDecoder extends ByteToMessageDecoder {

    private static final Logger logger = LoggerFactory.getLogger(IMSMessageDecoder.class);

    private static final int MIN_MESSAGE_SIZE = 32; // Minimum IRM header size
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024; // 1MB max message size

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Need at least 4 bytes to read total message length
        if (in.readableBytes() < 4) {
            return;
        }

        // Mark the current reader index
        int readerIndex = in.readerIndex();

        // Read total message length (first 4 bytes, big-endian)
        int totalLength = in.readInt();

        // Validate message length
        if (totalLength < MIN_MESSAGE_SIZE || totalLength > MAX_MESSAGE_SIZE) {
            logger.error("Invalid message length: {}", totalLength);
            ctx.close();
            return;
        }

        // Check if we have the complete message
        // totalLength includes the 4-byte length field itself
        if (in.readableBytes() < totalLength - 4) {
            // Not enough data yet, reset reader index and wait for more
            in.readerIndex(readerIndex);
            return;
        }

        try {
            // Parse the complete message
            IMSRequest request = parseMessage(in, totalLength);
            out.add(request);

            logger.debug("Decoded IMS message: {}", request);

        } catch (ProtocolException e) {
            logger.error("Protocol error decoding message", e);
            ctx.close();
        } catch (Exception e) {
            logger.error("Unexpected error decoding message", e);
            ctx.close();
        }
    }

    private IMSRequest parseMessage(ByteBuf buffer, int totalLength) throws ProtocolException {
        // Parse IRM Header
        IRMHeader header = IRMHeader.parseFrom(buffer);

        // Determine transaction type from message type
        TransactionType transactionType = determineTransactionType(header.getMessageType());

        // Calculate remaining bytes for message segments
        int headerSize = header.getIrmLength() + 4; // +4 for total length field
        int segmentDataSize = totalLength - headerSize;

        List<MessageSegment> segments;
        if (segmentDataSize > 0) {
            // Extract segment data
            ByteBuf segmentBuffer = buffer.readSlice(segmentDataSize);
            segments = MessageSegmentParser.parseSegments(segmentBuffer);
        } else {
            // No segments (e.g., ACK/NAK messages)
            segments = List.of();
        }

        return new IMSRequest(header, segments, transactionType);
    }

    private TransactionType determineTransactionType(byte messageType) {
        switch (messageType) {
            case IRMHeader.MSG_SEND_RECEIVE:
                return TransactionType.SEND_RECEIVE;
            case IRMHeader.MSG_SEND_ONLY:
            case IRMHeader.MSG_SEND_ONLY_ACK:
            case IRMHeader.MSG_SYNC_RESP:
            case IRMHeader.MSG_SYNC_RESP_ACK:
                return TransactionType.SEND_ONLY;
            case IRMHeader.MSG_RESUME_TPIPE:
                return TransactionType.RECV_ONLY;
            case IRMHeader.MSG_ACK:
                return TransactionType.ACK;
            case IRMHeader.MSG_NACK:
                return TransactionType.NAK;
            case IRMHeader.MSG_DEALLOCATE:
                return TransactionType.DEALLOCATE;
            case IRMHeader.MSG_CANCEL_TIMER:
                return TransactionType.CANCEL_TIMER;
            default:
                logger.warn("Unknown message type: 0x{:02X}", messageType & 0xFF);
                return TransactionType.UNKNOWN;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in IMSMessageDecoder", cause);
        ctx.close();
    }
}
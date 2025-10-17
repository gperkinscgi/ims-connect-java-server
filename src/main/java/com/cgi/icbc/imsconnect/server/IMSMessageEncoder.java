package com.cgi.icbc.imsconnect.server;

import com.cgi.icbc.imsconnect.model.IMSResponse;
import com.cgi.icbc.imsconnect.model.MessageSegment;
import com.cgi.icbc.imsconnect.model.ResponseType;
import com.cgi.icbc.imsconnect.protocol.MessageSegmentParser;
import com.cgi.icbc.imsconnect.protocol.ResponseBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteOrder;

/**
 * Netty encoder for outgoing IMS Connect response messages.
 * Encodes IMSResponse objects into the binary protocol format.
 */
public class IMSMessageEncoder extends MessageToByteEncoder<IMSResponse> {

    private static final Logger logger = LoggerFactory.getLogger(IMSMessageEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, IMSResponse response, ByteBuf out) throws Exception {
        try {
            ByteBuf responseBuffer = encodeResponse(response);
            out.writeBytes(responseBuffer);
            responseBuffer.release();

            logger.debug("Encoded IMS response: {}", response);

        } catch (Exception e) {
            logger.error("Error encoding IMS response", e);
            // Send error response
            ByteBuf errorResponse = createErrorResponse(500, 1, "Internal server error");
            out.writeBytes(errorResponse);
            errorResponse.release();
        }
    }

    private ByteBuf encodeResponse(IMSResponse response) {
        switch (response.getResponseType()) {
            case SUCCESS:
                return encodeSuccessResponse(response);
            case ERROR:
                return encodeErrorResponse(response);
            case ACK:
                return encodeAckResponse(response);
            case NAK:
                return encodeNakResponse(response);
            default:
                throw new IllegalArgumentException("Unknown response type: " + response.getResponseType());
        }
    }

    private ByteBuf encodeSuccessResponse(IMSResponse response) {
        // Build Complete Status Message (CSM) + data segments
        ByteBuf csmBuffer = ResponseBuilder.createCompleteStatusMessage();

        // Add generated client ID segment if present
        if (response.getGeneratedClientId().isPresent()) {
            ByteBuf cidBuffer = ResponseBuilder.createClientIdSegment(response.getGeneratedClientId().get());
            csmBuffer = combineBufs(csmBuffer, cidBuffer);
        }

        // Add MFS mod name segment if present
        if (response.getModName().isPresent()) {
            ByteBuf rmmBuffer = ResponseBuilder.createRequestModSegment(response.getModName().get());
            csmBuffer = combineBufs(csmBuffer, rmmBuffer);
        }

        // Add data segments
        if (!response.getDataSegments().isEmpty()) {
            ByteBuf dataBuffer = MessageSegmentParser.serializeSegments(response.getDataSegments());
            csmBuffer = combineBufs(csmBuffer, dataBuffer);
        } else {
            // Add empty message trailer
            ByteBuf emptyBuffer = MessageSegmentParser.createEmptyMessage();
            csmBuffer = combineBufs(csmBuffer, emptyBuffer);
        }

        // Add total length prefix
        return addTotalLengthPrefix(csmBuffer);
    }

    private ByteBuf encodeErrorResponse(IMSResponse response) {
        // Build Request Status Message (RSM)
        ByteBuf rsmBuffer = ResponseBuilder.createRequestStatusMessage(
                response.getReturnCode(),
                response.getReasonCode()
        );

        return addTotalLengthPrefix(rsmBuffer);
    }

    private ByteBuf encodeAckResponse(IMSResponse response) {
        // Simple ACK response with CSM
        ByteBuf csmBuffer = ResponseBuilder.createCompleteStatusMessage();
        ByteBuf emptyBuffer = MessageSegmentParser.createEmptyMessage();
        ByteBuf combined = combineBufs(csmBuffer, emptyBuffer);

        return addTotalLengthPrefix(combined);
    }

    private ByteBuf encodeNakResponse(IMSResponse response) {
        // NAK response with RSM
        ByteBuf rsmBuffer = ResponseBuilder.createRequestStatusMessage(
                response.getReturnCode(),
                response.getReasonCode()
        );

        return addTotalLengthPrefix(rsmBuffer);
    }

    private ByteBuf addTotalLengthPrefix(ByteBuf content) {
        int totalLength = content.readableBytes() + 4; // +4 for length field itself
        ByteBuf result = content.alloc().buffer(totalLength).order(ByteOrder.BIG_ENDIAN);

        result.writeInt(totalLength);
        result.writeBytes(content);
        content.release();

        return result;
    }

    private ByteBuf combineBufs(ByteBuf first, ByteBuf second) {
        ByteBuf combined = first.alloc().buffer(first.readableBytes() + second.readableBytes());
        combined.writeBytes(first);
        combined.writeBytes(second);
        first.release();
        second.release();
        return combined;
    }

    private ByteBuf createErrorResponse(int returnCode, int reasonCode, String message) {
        ByteBuf rsmBuffer = ResponseBuilder.createRequestStatusMessage(returnCode, reasonCode);
        return addTotalLengthPrefix(rsmBuffer);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in IMSMessageEncoder", cause);
        ctx.close();
    }
}
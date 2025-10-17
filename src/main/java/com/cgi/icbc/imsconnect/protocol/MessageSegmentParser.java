package com.cgi.icbc.imsconnect.protocol;

import com.cgi.icbc.imsconnect.model.MessageSegment;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for IMS Connect message segments following LL/ZZ format.
 * Each segment starts with a 2-byte length (LL) and 2-byte zero field (ZZ),
 * followed by the actual data.
 */
public class MessageSegmentParser {

    private static final int SEGMENT_HEADER_SIZE = 4; // LL (2) + ZZ (2)
    private static final int MIN_SEGMENT_SIZE = SEGMENT_HEADER_SIZE;
    private static final int MAX_SEGMENT_SIZE = 32 * 1024; // 32KB max

    /**
     * Parses message segments from a ByteBuf containing the message body
     * (after the IRM header).
     *
     * @param buffer ByteBuf containing message segments
     * @return List of parsed MessageSegments
     * @throws ProtocolException if segment format is invalid
     */
    public static List<MessageSegment> parseSegments(ByteBuf buffer) throws ProtocolException {
        List<MessageSegment> segments = new ArrayList<>();

        while (buffer.isReadable()) {
            // Check for end-of-message trailer (4 bytes of 0x00 0x04 0x00 0x00)
            if (buffer.readableBytes() >= 4) {
                int mark = buffer.readerIndex();
                int possibleTrailer = buffer.readInt();
                if (possibleTrailer == 0x00040000) {
                    // Found trailer, end of message
                    break;
                } else {
                    // Not trailer, reset position
                    buffer.readerIndex(mark);
                }
            }

            // Need at least segment header
            if (buffer.readableBytes() < SEGMENT_HEADER_SIZE) {
                throw new ProtocolException("Insufficient data for segment header");
            }

            // Read segment length (LL)
            short segmentLength = buffer.readShort();
            if (segmentLength < MIN_SEGMENT_SIZE || segmentLength > MAX_SEGMENT_SIZE) {
                throw new ProtocolException("Invalid segment length: " + segmentLength);
            }

            // Read ZZ field (should be 0x0000)
            short zzField = buffer.readShort();
            // Note: ZZ field validation is optional, some implementations may use it

            // Calculate data length (total length - header)
            int dataLength = segmentLength - SEGMENT_HEADER_SIZE;
            if (dataLength < 0) {
                throw new ProtocolException("Invalid data length: " + dataLength);
            }

            // Check if enough data is available
            if (buffer.readableBytes() < dataLength) {
                throw new ProtocolException("Insufficient data for segment body");
            }

            // Read segment data
            byte[] segmentData = new byte[dataLength];
            buffer.readBytes(segmentData);

            segments.add(new MessageSegment(segmentData));
        }

        return segments;
    }

    /**
     * Serializes message segments to ByteBuf with LL/ZZ format.
     *
     * @param segments List of MessageSegments to serialize
     * @return ByteBuf containing serialized segments with trailer
     */
    public static ByteBuf serializeSegments(List<MessageSegment> segments) {
        // Calculate total size needed
        int totalSize = 0;
        for (MessageSegment segment : segments) {
            totalSize += segment.getTotalLength();
        }
        totalSize += 4; // Add space for trailer

        ByteBuf buffer = Unpooled.buffer(totalSize).order(ByteOrder.BIG_ENDIAN);

        // Write each segment
        for (MessageSegment segment : segments) {
            writeSegment(buffer, segment);
        }

        // Write trailer (0x00 0x04 0x00 0x00)
        buffer.writeInt(0x00040000);

        return buffer;
    }

    /**
     * Writes a single segment to the ByteBuf with LL/ZZ header.
     *
     * @param buffer ByteBuf to write to
     * @param segment MessageSegment to write
     */
    private static void writeSegment(ByteBuf buffer, MessageSegment segment) {
        byte[] data = segment.getData();
        short totalLength = (short) (data.length + SEGMENT_HEADER_SIZE);

        // Write LL (length including header)
        buffer.writeShort(totalLength);

        // Write ZZ (zero field)
        buffer.writeShort(0x0000);

        // Write data
        buffer.writeBytes(data);
    }

    /**
     * Creates a single message segment from string data.
     *
     * @param data String data for the segment
     * @return ByteBuf containing the segment with LL/ZZ header and trailer
     */
    public static ByteBuf createSingleSegmentMessage(String data) {
        List<MessageSegment> segments = new ArrayList<>();
        segments.add(MessageSegment.fromString(data));
        return serializeSegments(segments);
    }

    /**
     * Creates an empty message with just the trailer.
     *
     * @return ByteBuf containing only the message trailer
     */
    public static ByteBuf createEmptyMessage() {
        ByteBuf buffer = Unpooled.buffer(4).order(ByteOrder.BIG_ENDIAN);
        buffer.writeInt(0x00040000); // Trailer
        return buffer;
    }
}
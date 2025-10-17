package com.cgi.icbc.imsconnect.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an IMS message segment with LL/ZZ format.
 *
 * IMS messages are composed of segments, each with:
 * - LL: 2-byte length field (includes the LL and ZZ fields)
 * - ZZ: 2-byte control field (segment type and flags)
 * - Data: Variable length segment data
 */
public class MessageSegment {

    private short length;        // LL: Total length including LL and ZZ
    private short control;       // ZZ: Control field
    private byte[] data;         // Segment data

    // Control field (ZZ) constants
    public static final short ZZ_FIRST_SEGMENT = 0x0000;      // First segment of message
    public static final short ZZ_MIDDLE_SEGMENT = 0x4000;     // Middle segment
    public static final short ZZ_LAST_SEGMENT = 0x8000;       // Last segment
    public static final short ZZ_ONLY_SEGMENT = 0xC000;       // Only segment (complete message)

    // Special segment types
    public static final short ZZ_SCRATCH_PAD = 0x0001;        // Scratch pad area
    public static final short ZZ_INPUT_MSG = 0x0002;          // Input message
    public static final short ZZ_OUTPUT_MSG = 0x0003;         // Output message
    public static final short ZZ_STATUS_MSG = 0x0004;         // Status message

    public MessageSegment() {
        this.length = 4; // Minimum length (LL + ZZ)
        this.control = ZZ_ONLY_SEGMENT;
        this.data = new byte[0];
    }

    public MessageSegment(short control, byte[] data) {
        this.control = control;
        this.data = data != null ? data.clone() : new byte[0];
        this.length = (short) (4 + this.data.length);
    }

    /**
     * Parse a single message segment from ByteBuf.
     */
    public static MessageSegment parseFrom(ByteBuf buffer) {
        if (buffer.readableBytes() < 4) {
            throw new IllegalArgumentException("Buffer too small for message segment header");
        }

        MessageSegment segment = new MessageSegment();

        // Read LL (length)
        segment.length = buffer.readShort();

        // Read ZZ (control)
        segment.control = buffer.readShort();

        // Calculate data length
        int dataLength = segment.length - 4;
        if (dataLength < 0) {
            throw new IllegalArgumentException("Invalid segment length: " + segment.length);
        }

        if (buffer.readableBytes() < dataLength) {
            throw new IllegalArgumentException("Buffer too small for segment data");
        }

        // Read segment data
        if (dataLength > 0) {
            segment.data = new byte[dataLength];
            buffer.readBytes(segment.data);
        } else {
            segment.data = new byte[0];
        }

        return segment;
    }

    /**
     * Parse all message segments from ByteBuf.
     */
    public static List<MessageSegment> parseSegments(ByteBuf buffer) {
        List<MessageSegment> segments = new ArrayList<>();

        while (buffer.readableBytes() >= 4) {
            MessageSegment segment = parseFrom(buffer);
            segments.add(segment);

            // Check if this is the last segment
            if (segment.isLastSegment() || segment.isOnlySegment()) {
                break;
            }
        }

        return segments;
    }

    /**
     * Write segment to ByteBuf.
     */
    public void writeTo(ByteBuf buffer) {
        buffer.writeShort(length);
        buffer.writeShort(control);
        if (data.length > 0) {
            buffer.writeBytes(data);
        }
    }

    /**
     * Serialize segment to byte array.
     */
    public byte[] toByteArray() {
        ByteBuf buffer = Unpooled.buffer(length);
        try {
            writeTo(buffer);
            byte[] result = new byte[buffer.readableBytes()];
            buffer.readBytes(result);
            return result;
        } finally {
            buffer.release();
        }
    }

    /**
     * Create segments from message data, splitting if necessary.
     */
    public static List<MessageSegment> createSegments(byte[] messageData, int maxSegmentSize) {
        List<MessageSegment> segments = new ArrayList<>();

        if (messageData == null || messageData.length == 0) {
            // Empty message - create single empty segment
            segments.add(new MessageSegment(ZZ_ONLY_SEGMENT, new byte[0]));
            return segments;
        }

        int maxDataSize = maxSegmentSize - 4; // Account for LL/ZZ overhead
        if (maxDataSize <= 0) {
            throw new IllegalArgumentException("Max segment size too small");
        }

        if (messageData.length <= maxDataSize) {
            // Single segment
            segments.add(new MessageSegment(ZZ_ONLY_SEGMENT, messageData));
        } else {
            // Multiple segments
            int offset = 0;
            int remaining = messageData.length;
            boolean isFirst = true;

            while (remaining > 0) {
                int segmentDataSize = Math.min(remaining, maxDataSize);
                byte[] segmentData = new byte[segmentDataSize];
                System.arraycopy(messageData, offset, segmentData, 0, segmentDataSize);

                short control;
                if (isFirst && remaining <= maxDataSize) {
                    control = ZZ_ONLY_SEGMENT;
                } else if (isFirst) {
                    control = ZZ_FIRST_SEGMENT;
                } else if (remaining <= maxDataSize) {
                    control = ZZ_LAST_SEGMENT;
                } else {
                    control = ZZ_MIDDLE_SEGMENT;
                }

                segments.add(new MessageSegment(control, segmentData));

                offset += segmentDataSize;
                remaining -= segmentDataSize;
                isFirst = false;
            }
        }

        return segments;
    }

    /**
     * Reassemble segments into complete message data.
     */
    public static byte[] reassembleSegments(List<MessageSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return new byte[0];
        }

        // Calculate total data length
        int totalLength = segments.stream()
                .mapToInt(segment -> segment.data.length)
                .sum();

        // Reassemble data
        byte[] result = new byte[totalLength];
        int offset = 0;

        for (MessageSegment segment : segments) {
            System.arraycopy(segment.data, 0, result, offset, segment.data.length);
            offset += segment.data.length;
        }

        return result;
    }

    // Getters and setters
    public short getLength() { return length; }
    public void setLength(short length) { this.length = length; }

    public short getControl() { return control; }
    public void setControl(short control) { this.control = control; }

    public byte[] getData() { return data != null ? data.clone() : new byte[0]; }
    public void setData(byte[] data) {
        this.data = data != null ? data.clone() : new byte[0];
        this.length = (short) (4 + this.data.length);
    }

    // Control field helper methods
    public boolean isFirstSegment() {
        return (control & 0xC000) == ZZ_FIRST_SEGMENT;
    }

    public boolean isMiddleSegment() {
        return (control & 0xC000) == ZZ_MIDDLE_SEGMENT;
    }

    public boolean isLastSegment() {
        return (control & 0xC000) == ZZ_LAST_SEGMENT;
    }

    public boolean isOnlySegment() {
        return (control & 0xC000) == ZZ_ONLY_SEGMENT;
    }

    public short getSegmentType() {
        return (short) (control & 0x3FFF); // Lower 14 bits
    }

    public void setSegmentType(short type) {
        this.control = (short) ((control & 0xC000) | (type & 0x3FFF));
    }

    // Segment type checks
    public boolean isScratchPad() {
        return getSegmentType() == ZZ_SCRATCH_PAD;
    }

    public boolean isInputMessage() {
        return getSegmentType() == ZZ_INPUT_MSG;
    }

    public boolean isOutputMessage() {
        return getSegmentType() == ZZ_OUTPUT_MSG;
    }

    public boolean isStatusMessage() {
        return getSegmentType() == ZZ_STATUS_MSG;
    }

    // Validation
    public boolean isValid() {
        return length >= 4 && length == (4 + data.length);
    }

    // Factory methods
    public static MessageSegment createInputMessage(byte[] data) {
        return new MessageSegment((short) (ZZ_ONLY_SEGMENT | ZZ_INPUT_MSG), data);
    }

    public static MessageSegment createOutputMessage(byte[] data) {
        return new MessageSegment((short) (ZZ_ONLY_SEGMENT | ZZ_OUTPUT_MSG), data);
    }

    public static MessageSegment createStatusMessage(byte[] data) {
        return new MessageSegment((short) (ZZ_ONLY_SEGMENT | ZZ_STATUS_MSG), data);
    }

    @Override
    public String toString() {
        String segmentTypeStr = switch (control & 0xC000) {
            case ZZ_FIRST_SEGMENT -> "FIRST";
            case ZZ_MIDDLE_SEGMENT -> "MIDDLE";
            case ZZ_LAST_SEGMENT -> "LAST";
            case ZZ_ONLY_SEGMENT -> "ONLY";
            default -> "UNKNOWN";
        };

        return String.format("MessageSegment{length=%d, control=0x%04X, type=%s, dataLen=%d}",
                length, control & 0xFFFF, segmentTypeStr, data.length);
    }
}
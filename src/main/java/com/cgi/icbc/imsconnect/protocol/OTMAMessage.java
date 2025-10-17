package com.cgi.icbc.imsconnect.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete OTMA message structure containing IRM header, OTMA header, and message segments.
 *
 * Message structure:
 * +------------------+
 * |   IRM Header     | (64+ bytes)
 * +------------------+
 * |   OTMA Header    | (20+ bytes)
 * +------------------+
 * | Message Segments | (LL/ZZ format)
 * +------------------+
 */
public class OTMAMessage {

    private IRMHeader irmHeader;
    private OTMAHeader otmaHeader;
    private List<MessageSegment> segments;

    public OTMAMessage() {
        this.irmHeader = new IRMHeader();
        this.otmaHeader = new OTMAHeader();
        this.segments = new ArrayList<>();
    }

    public OTMAMessage(IRMHeader irmHeader, OTMAHeader otmaHeader) {
        this.irmHeader = irmHeader;
        this.otmaHeader = otmaHeader;
        this.segments = new ArrayList<>();
    }

    public OTMAMessage(IRMHeader irmHeader, OTMAHeader otmaHeader, List<MessageSegment> segments) {
        this.irmHeader = irmHeader;
        this.otmaHeader = otmaHeader;
        this.segments = segments != null ? new ArrayList<>(segments) : new ArrayList<>();
    }

    /**
     * Parse complete OTMA message from ByteBuf.
     */
    public static OTMAMessage parseFrom(ByteBuf buffer) {
        if (buffer.readableBytes() < 84) { // Minimum: 64 (IRM) + 20 (OTMA)
            throw new IllegalArgumentException("Buffer too small for OTMA message");
        }

        // Parse IRM header
        IRMHeader irmHeader = IRMHeader.parseFrom(buffer);

        // Check if OTMA headers are present
        if (irmHeader.hasFlag(IRMHeader.F5_NO_OTMA)) {
            throw new IllegalArgumentException("Message marked as no OTMA headers");
        }

        // Parse OTMA header
        OTMAHeader otmaHeader = OTMAHeader.parseFrom(buffer);

        // Parse message segments
        List<MessageSegment> segments = MessageSegment.parseSegments(buffer);

        return new OTMAMessage(irmHeader, otmaHeader, segments);
    }

    /**
     * Write complete OTMA message to ByteBuf.
     */
    public void writeTo(ByteBuf buffer) {
        // Calculate total message length
        int totalLength = calculateTotalLength();
        irmHeader.setTotalLength(totalLength);

        // Write IRM header
        irmHeader.writeTo(buffer);

        // Write OTMA header
        otmaHeader.writeTo(buffer);

        // Write message segments
        for (MessageSegment segment : segments) {
            segment.writeTo(buffer);
        }
    }

    /**
     * Serialize complete OTMA message to byte array.
     */
    public byte[] toByteArray() {
        int totalLength = calculateTotalLength();
        ByteBuf buffer = Unpooled.buffer(totalLength);
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
     * Calculate total message length.
     */
    public int calculateTotalLength() {
        int length = irmHeader.calculateHeaderLength() + otmaHeader.getLength();

        for (MessageSegment segment : segments) {
            length += segment.getLength();
        }

        return length;
    }

    /**
     * Get message data from all segments combined.
     */
    public byte[] getMessageData() {
        return MessageSegment.reassembleSegments(segments);
    }

    /**
     * Set message data, automatically creating segments.
     */
    public void setMessageData(byte[] data) {
        setMessageData(data, 32768); // Default max segment size
    }

    /**
     * Set message data with specified max segment size.
     */
    public void setMessageData(byte[] data, int maxSegmentSize) {
        this.segments = MessageSegment.createSegments(data, maxSegmentSize);
    }

    /**
     * Add a message segment.
     */
    public void addSegment(MessageSegment segment) {
        if (segment != null) {
            segments.add(segment);
        }
    }

    /**
     * Add message data as input segment.
     */
    public void addInputData(byte[] data) {
        if (data != null && data.length > 0) {
            segments.add(MessageSegment.createInputMessage(data));
        }
    }

    /**
     * Add message data as output segment.
     */
    public void addOutputData(byte[] data) {
        if (data != null && data.length > 0) {
            segments.add(MessageSegment.createOutputMessage(data));
        }
    }

    // Getters and setters
    public IRMHeader getIrmHeader() { return irmHeader; }
    public void setIrmHeader(IRMHeader irmHeader) { this.irmHeader = irmHeader; }

    public OTMAHeader getOtmaHeader() { return otmaHeader; }
    public void setOtmaHeader(OTMAHeader otmaHeader) { this.otmaHeader = otmaHeader; }

    public List<MessageSegment> getSegments() { return new ArrayList<>(segments); }
    public void setSegments(List<MessageSegment> segments) {
        this.segments = segments != null ? new ArrayList<>(segments) : new ArrayList<>();
    }

    // Convenience methods
    public String getTransactionCode() {
        return irmHeader != null ? irmHeader.getTransactionCodeAsString() : null;
    }

    public void setTransactionCode(String transactionCode) {
        if (irmHeader != null) {
            irmHeader.setTransactionCode(transactionCode);
        }
    }

    public String getClientId() {
        return irmHeader != null ? irmHeader.getClientIdAsString() : null;
    }

    public void setClientId(String clientId) {
        if (irmHeader != null) {
            irmHeader.setClientId(clientId);
        }
    }

    public String getLtermName() {
        return otmaHeader != null ? otmaHeader.getLtermNameAsString() : null;
    }

    public void setLtermName(String ltermName) {
        if (otmaHeader != null) {
            otmaHeader.setLtermName(ltermName);
        }
    }

    public int getConversationId() {
        return otmaHeader != null ? otmaHeader.getConversationId() : 0;
    }

    public void setConversationId(int conversationId) {
        if (otmaHeader != null) {
            otmaHeader.setConversationId(conversationId);
        }
    }

    // Message type checks
    public boolean isConversational() {
        return otmaHeader != null && otmaHeader.isConversational();
    }

    public boolean isTransaction() {
        return otmaHeader != null && otmaHeader.isTransaction();
    }

    public boolean isResponse() {
        return otmaHeader != null && otmaHeader.isResponse();
    }

    public boolean isFirstMessage() {
        return otmaHeader != null && otmaHeader.isFirstMessage();
    }

    public boolean isLastMessage() {
        return otmaHeader != null && otmaHeader.isLastMessage();
    }

    // Validation
    public boolean isValid() {
        return irmHeader != null && irmHeader.isValid() &&
               otmaHeader != null && otmaHeader.isValid() &&
               segments != null;
    }

    // Factory methods
    public static OTMAMessage createTransaction(String transactionCode, String clientId, String ltermName, byte[] data) {
        IRMHeader irmHeader = new IRMHeader();
        irmHeader.setTransactionCode(transactionCode);
        irmHeader.setClientId(clientId);
        irmHeader.setArchitecture(IRMHeader.IRMARCH6); // Use enhanced architecture for OTMA
        irmHeader.setFlags(IRMHeader.IRMSYNCH);

        OTMAHeader otmaHeader = OTMAHeader.createTransaction(ltermName);

        OTMAMessage message = new OTMAMessage(irmHeader, otmaHeader);
        message.setMessageData(data);

        return message;
    }

    public static OTMAMessage createConversational(String transactionCode, String clientId, String ltermName,
                                                   int conversationId, byte[] data) {
        IRMHeader irmHeader = new IRMHeader();
        irmHeader.setTransactionCode(transactionCode);
        irmHeader.setClientId(clientId);
        irmHeader.setArchitecture(IRMHeader.IRMARCH6);
        irmHeader.setFlags(IRMHeader.IRMSYNCH);

        OTMAHeader otmaHeader = OTMAHeader.createConversational(ltermName, conversationId);

        OTMAMessage message = new OTMAMessage(irmHeader, otmaHeader);
        message.setMessageData(data);

        return message;
    }

    public static OTMAMessage createResponse(OTMAMessage requestMessage, byte[] responseData) {
        IRMHeader responseIrmHeader = new IRMHeader();
        responseIrmHeader.setTransactionCode(requestMessage.getTransactionCode());
        responseIrmHeader.setClientId(requestMessage.getClientId());
        responseIrmHeader.setArchitecture(IRMHeader.IRMARCH6);
        responseIrmHeader.setFlags(IRMHeader.IRMSYNCH);

        OTMAHeader responseOtmaHeader = OTMAHeader.createResponse(requestMessage.getOtmaHeader());

        OTMAMessage response = new OTMAMessage(responseIrmHeader, responseOtmaHeader);
        response.setMessageData(responseData);

        return response;
    }

    @Override
    public String toString() {
        return String.format("OTMAMessage{txnCode='%s', clientId='%s', lterm='%s', convId=%d, segments=%d, totalLen=%d}",
                getTransactionCode(), getClientId(), getLtermName(), getConversationId(),
                segments.size(), calculateTotalLength());
    }
}
package com.cgi.icbc.imsconnect.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import com.cgi.icbc.imsconnect.util.EbcdicConverter;

import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * OTMA (Open Transaction Manager Access) Header implementation.
 *
 * The OTMA header provides IMS-specific routing and transaction state information
 * that follows the IRM header in IMS Connect messages.
 */
public class OTMAHeader {

    // OTMA header fields (20+ bytes)
    private short length;                    // 2 bytes - Length of OTMA header
    private byte version;                    // 1 byte - OTMA version
    private byte flags;                      // 1 byte - OTMA flags
    private int conversationId;              // 4 bytes - Conversation ID for multi-message transactions
    private byte[] ltermName;                // 8 bytes - IMS logical terminal name
    private byte messageType;                // 1 byte - IMS message type
    private byte syncLevel;                  // 1 byte - Synchronization level
    private byte commitMode;                 // 1 byte - Commit mode (0=commit mode 0, 1=commit mode 1)
    private byte responseMode;               // 1 byte - Response mode

    // OTMA version constants
    public static final byte OTMA_VERSION_1 = 0x01;
    public static final byte OTMA_VERSION_2 = 0x02;

    // OTMA flags
    public static final byte OTMA_FLAG_HOLD = 0x80;          // Hold conversation
    public static final byte OTMA_FLAG_CONTINUE = 0x40;      // Continue conversation
    public static final byte OTMA_FLAG_LAST = 0x20;         // Last message in conversation
    public static final byte OTMA_FLAG_FIRST = 0x10;        // First message in conversation
    public static final byte OTMA_FLAG_ACK_REQUIRED = 0x08; // Acknowledgment required
    public static final byte OTMA_FLAG_SYNC = 0x04;         // Synchronous processing
    public static final byte OTMA_FLAG_DEQUEUE = 0x02;      // Dequeue message
    public static final byte OTMA_FLAG_RESPONSE = 0x01;     // Response message

    // IMS message types
    public static final byte MSG_TYPE_COMMAND = 0x01;        // IMS command
    public static final byte MSG_TYPE_TRANSACTION = 0x02;    // IMS transaction
    public static final byte MSG_TYPE_RESPONSE = 0x03;      // IMS response
    public static final byte MSG_TYPE_STATUS = 0x04;        // IMS status message
    public static final byte MSG_TYPE_CONVERSATION = 0x05;   // Conversational message

    // Sync levels
    public static final byte SYNC_NONE = 0x00;              // No synchronization
    public static final byte SYNC_CONFIRM = 0x01;          // Confirm synchronization
    public static final byte SYNC_SYNCPOINT = 0x02;        // Syncpoint synchronization

    // Response modes
    public static final byte RESPONSE_NONE = 0x00;          // No response expected
    public static final byte RESPONSE_IMMEDIATE = 0x01;     // Immediate response
    public static final byte RESPONSE_DEFERRED = 0x02;      // Deferred response

    // Default constructor
    public OTMAHeader() {
        this.length = 20; // Minimum OTMA header size
        this.version = OTMA_VERSION_2;
        this.flags = 0;
        this.conversationId = 0;
        this.ltermName = new byte[8];
        this.messageType = MSG_TYPE_TRANSACTION;
        this.syncLevel = SYNC_NONE;
        this.commitMode = 0;
        this.responseMode = RESPONSE_IMMEDIATE;
    }

    // Constructor with parameters
    public OTMAHeader(byte version, byte flags, int conversationId, String ltermName) {
        this();
        this.version = version;
        this.flags = flags;
        this.conversationId = conversationId;
        setLtermName(ltermName);
    }

    /**
     * Parse OTMA header from ByteBuf.
     */
    public static OTMAHeader parseFrom(ByteBuf buffer) {
        if (buffer.readableBytes() < 20) {
            throw new IllegalArgumentException("Buffer too small for OTMA header (need at least 20 bytes)");
        }

        OTMAHeader header = new OTMAHeader();

        // Read header fields
        header.length = buffer.readShort();
        header.version = buffer.readByte();
        header.flags = buffer.readByte();
        header.conversationId = buffer.readInt();

        // Read LTERM name (8 bytes)
        buffer.readBytes(header.ltermName);

        header.messageType = buffer.readByte();
        header.syncLevel = buffer.readByte();
        header.commitMode = buffer.readByte();
        header.responseMode = buffer.readByte();

        return header;
    }

    /**
     * Write OTMA header to ByteBuf.
     */
    public void writeTo(ByteBuf buffer) {
        buffer.writeShort(length);
        buffer.writeByte(version);
        buffer.writeByte(flags);
        buffer.writeInt(conversationId);
        buffer.writeBytes(ltermName);
        buffer.writeByte(messageType);
        buffer.writeByte(syncLevel);
        buffer.writeByte(commitMode);
        buffer.writeByte(responseMode);
    }

    /**
     * Serialize OTMA header to byte array.
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

    // Getters and setters
    public short getLength() { return length; }
    public void setLength(short length) { this.length = length; }

    public byte getVersion() { return version; }
    public void setVersion(byte version) { this.version = version; }

    public byte getFlags() { return flags; }
    public void setFlags(byte flags) { this.flags = flags; }

    public int getConversationId() { return conversationId; }
    public void setConversationId(int conversationId) { this.conversationId = conversationId; }

    public byte[] getLtermName() { return Arrays.copyOf(ltermName, ltermName.length); }
    public void setLtermName(byte[] ltermName) {
        System.arraycopy(ltermName, 0, this.ltermName, 0, Math.min(8, ltermName.length));
    }

    public String getLtermNameAsString() { return EbcdicConverter.fromFixedLengthEbcdic(ltermName); }
    public void setLtermName(String ltermName) {
        this.ltermName = EbcdicConverter.toFixedLengthEbcdic(ltermName, 8, ' ');
    }

    public byte getMessageType() { return messageType; }
    public void setMessageType(byte messageType) { this.messageType = messageType; }

    public byte getSyncLevel() { return syncLevel; }
    public void setSyncLevel(byte syncLevel) { this.syncLevel = syncLevel; }

    public byte getCommitMode() { return commitMode; }
    public void setCommitMode(byte commitMode) { this.commitMode = commitMode; }

    public byte getResponseMode() { return responseMode; }
    public void setResponseMode(byte responseMode) { this.responseMode = responseMode; }

    // Flag helper methods
    public boolean hasFlag(byte flag) {
        return (flags & flag) != 0;
    }

    public void setFlag(byte flag) {
        this.flags |= flag;
    }

    public void clearFlag(byte flag) {
        this.flags &= ~flag;
    }

    // Convenience flag methods
    public boolean isHoldConversation() { return hasFlag(OTMA_FLAG_HOLD); }
    public void setHoldConversation(boolean hold) {
        if (hold) setFlag(OTMA_FLAG_HOLD); else clearFlag(OTMA_FLAG_HOLD);
    }

    public boolean isContinueConversation() { return hasFlag(OTMA_FLAG_CONTINUE); }
    public void setContinueConversation(boolean cont) {
        if (cont) setFlag(OTMA_FLAG_CONTINUE); else clearFlag(OTMA_FLAG_CONTINUE);
    }

    public boolean isLastMessage() { return hasFlag(OTMA_FLAG_LAST); }
    public void setLastMessage(boolean last) {
        if (last) setFlag(OTMA_FLAG_LAST); else clearFlag(OTMA_FLAG_LAST);
    }

    public boolean isFirstMessage() { return hasFlag(OTMA_FLAG_FIRST); }
    public void setFirstMessage(boolean first) {
        if (first) setFlag(OTMA_FLAG_FIRST); else clearFlag(OTMA_FLAG_FIRST);
    }

    public boolean isAckRequired() { return hasFlag(OTMA_FLAG_ACK_REQUIRED); }
    public void setAckRequired(boolean ack) {
        if (ack) setFlag(OTMA_FLAG_ACK_REQUIRED); else clearFlag(OTMA_FLAG_ACK_REQUIRED);
    }

    public boolean isSynchronous() { return hasFlag(OTMA_FLAG_SYNC); }
    public void setSynchronous(boolean sync) {
        if (sync) setFlag(OTMA_FLAG_SYNC); else clearFlag(OTMA_FLAG_SYNC);
    }

    public boolean isResponse() { return hasFlag(OTMA_FLAG_RESPONSE); }
    public void setResponse(boolean response) {
        if (response) setFlag(OTMA_FLAG_RESPONSE); else clearFlag(OTMA_FLAG_RESPONSE);
    }

    // Message type helper methods
    public boolean isCommand() { return messageType == MSG_TYPE_COMMAND; }
    public boolean isTransaction() { return messageType == MSG_TYPE_TRANSACTION; }
    public boolean isResponseMessage() { return messageType == MSG_TYPE_RESPONSE; }
    public boolean isStatusMessage() { return messageType == MSG_TYPE_STATUS; }
    public boolean isConversational() { return messageType == MSG_TYPE_CONVERSATION; }

    // Validation
    public boolean isValid() {
        return length >= 20 && version > 0 && ltermName != null;
    }

    // Create conversational transaction
    public static OTMAHeader createConversational(String ltermName, int conversationId) {
        OTMAHeader header = new OTMAHeader();
        header.setLtermName(ltermName);
        header.setConversationId(conversationId);
        header.setMessageType(MSG_TYPE_CONVERSATION);
        header.setFirstMessage(true);
        header.setHoldConversation(true);
        header.setSynchronous(true);
        return header;
    }

    // Create simple transaction
    public static OTMAHeader createTransaction(String ltermName) {
        OTMAHeader header = new OTMAHeader();
        header.setLtermName(ltermName);
        header.setMessageType(MSG_TYPE_TRANSACTION);
        header.setFirstMessage(true);
        header.setLastMessage(true);
        header.setSynchronous(true);
        return header;
    }

    // Create response header
    public static OTMAHeader createResponse(OTMAHeader requestHeader) {
        OTMAHeader header = new OTMAHeader();
        header.setLtermName(requestHeader.getLtermNameAsString());
        header.setConversationId(requestHeader.getConversationId());
        header.setMessageType(MSG_TYPE_RESPONSE);
        header.setResponse(true);
        header.setLastMessage(requestHeader.isLastMessage());
        return header;
    }

    @Override
    public String toString() {
        return String.format("OTMAHeader{length=%d, version=%d, flags=0x%02X, convId=%d, lterm='%s', msgType=%d, sync=%d}",
                length, version, flags & 0xFF, conversationId, getLtermNameAsString(), messageType, syncLevel);
    }
}
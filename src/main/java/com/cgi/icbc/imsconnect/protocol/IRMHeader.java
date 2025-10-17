package com.cgi.icbc.imsconnect.protocol;

import com.cgi.icbc.imsconnect.util.EbcdicConverter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * IMS Request Message Header implementation based on HWSSMPL1 exit routine.
 * Represents the complete IRM header structure including fixed and user portions.
 */
public class IRMHeader {

    // Fixed portion of IRM header (32 bytes)
    private int totalLength;           // 4 bytes - Total length of the message
    private short irmLength;           // 2 bytes - Length of IRM header (big-endian)
    private byte architecture;         // 1 byte - IRM architecture type
    private byte communicationFlags;   // 1 byte - F0 communication flags
    private byte[] irmId;             // 8 bytes - IRM identifier
    private short nakReasonCode;       // 2 bytes - NAK reason code
    private short reserved1;           // 2 bytes - reserved
    private byte inputMessageType;     // 1 byte - F5 input message type
    private byte timeout;              // 1 byte - delay wait timer
    private byte connectionType;       // 1 byte - socket connection type
    private byte encodingScheme;       // 1 byte - unicode encoding scheme
    private byte[] clientId;          // 8 bytes - client id

    // User portion of header - HWSSMPL1 format (88+ bytes)
    private byte userFlags1;           // 1 byte - F1 user flags
    private byte commitMode;           // 1 byte - F2 commit mode
    private byte syncFlags;            // 1 byte - F3 sync-level flags
    private byte messageType;          // 1 byte - F4 message type
    private byte[] transactionCode;    // 8 bytes - IMS transaction code
    private byte[] destinationId;      // 8 bytes - IMS datastore name
    private byte[] ltermName;          // 8 bytes - IMS lterm name
    private byte[] userId;             // 8 bytes - RACF user id
    private byte[] groupId;            // 8 bytes - RACF group id
    private byte[] password;           // 8 bytes - RACF password
    private byte[] applicationName;    // 8 bytes - RACF application name
    private byte[] rerouteName;        // 8 bytes - reroute tpipe name
    private byte[] tagAdapter;         // 8 bytes - XML adapter name
    private byte[] tagMap;             // 8 bytes - XML converter name
    private byte[] modName;            // 8 bytes - MFS modname

    // Architecture types
    public static final byte IRMARCH0 = 0;  // Base architectural structure
    public static final byte IRMARCH1 = 1;  // IRMARCH0 + reroute fields
    public static final byte IRMARCH2 = 2;  // IRMARCH1 + XML fields
    public static final byte IRMARCH3 = 3;  // IRMARCH2 + correlation + modname
    public static final byte IRMARCH4 = 4;  // IRMARCH3 + session tokens
    public static final byte IRMARCH5 = 5;  // IRMARCH4 + extensions

    // Communication flags (F0)
    public static final byte F0_NAK_REASON = 0x08;      // NAK with reason code
    public static final byte F0_SYNC_NAK = 0x04;        // Retain message on NAK
    public static final byte F0_SYNC_ASYNC = 0x02;      // Resume tpipe sync/async
    public static final byte F0_SYNC_ONLY = 0x01;       // Resume tpipe sync only

    // Input message type flags (F5)
    public static final byte F5_SINGLE_NO_WAIT = (byte) 0x80;  // Single message, no wait
    public static final byte F5_AUTO_FLOW = 0x40;       // Auto flow messages
    public static final byte F5_NO_AUTO_FLOW = 0x20;    // No auto flow
    public static final byte F5_XID = 0x10;             // X/Open identifier
    public static final byte F5_SINGLE_WAIT = 0x08;     // Single message, wait
    public static final byte F5_NO_TRANSLATE = 0x02;    // No message translation
    public static final byte F5_NO_OTMA = 0x01;         // No OTMA headers

    // User flags (F1)
    public static final byte F1_TXN_EXPIRY = 0x01;      // Transaction expiry
    public static final byte F1_NO_WAIT = 0x02;         // No wait for ACK
    public static final byte F1_SEND_ONLY_ACK = 0x04;   // Send-only ACK
    public static final byte F1_UNICODE_TXN = 0x08;     // Unicode transaction code
    public static final byte F1_UNICODE_MSG = 0x10;     // Unicode message
    public static final byte F1_CLIENT_ID_REQ = 0x20;   // Request client ID
    public static final byte F1_MFS_REQ = 0x40;         // Request MFS modname

    // Connection types
    public static final byte CONN_TRANSACTION = 0x00;    // Transaction socket
    public static final byte CONN_PERSISTENT = 0x10;     // Persistent socket
    public static final byte CONN_NON_PERSISTENT = 0x40; // Non-persistent socket

    // Message types (F4)
    public static final byte MSG_SEND_RECEIVE = 0x40;    // Send-receive transaction
    public static final byte MSG_ACK = (byte) 0xC1;      // ACK response
    public static final byte MSG_CANCEL_TIMER = (byte) 0xC3; // Cancel timer
    public static final byte MSG_DEALLOCATE = (byte) 0xC4;   // Deallocate conversation
    public static final byte MSG_SEND_ONLY_ACK = (byte) 0xD2; // Send-only with ACK
    public static final byte MSG_SYNC_RESP_ACK = (byte) 0xD3; // Sync response with ACK
    public static final byte MSG_SYNC_RESP = (byte) 0xD4;     // Sync response
    public static final byte MSG_NACK = (byte) 0xD5;         // NAK response
    public static final byte MSG_RESUME_TPIPE = (byte) 0xD9; // Resume tpipe
    public static final byte MSG_SEND_ONLY = (byte) 0xE2;    // Send-only message

    public IRMHeader() {
        // Initialize byte arrays
        this.irmId = new byte[8];
        this.clientId = new byte[8];
        this.transactionCode = new byte[8];
        this.destinationId = new byte[8];
        this.ltermName = new byte[8];
        this.userId = new byte[8];
        this.groupId = new byte[8];
        this.password = new byte[8];
        this.applicationName = new byte[8];
        this.rerouteName = new byte[8];
        this.tagAdapter = new byte[8];
        this.tagMap = new byte[8];
        this.modName = new byte[8];

        // Initialize with default values
        initializeDefaults();
    }

    private void initializeDefaults() {
        this.architecture = IRMARCH0;
        this.irmLength = 80; // Base length for IRMARCH0
        System.arraycopy(EbcdicConverter.asciiToEbcdic("*SAMPL1*"), 0, this.irmId, 0, 8);
        this.inputMessageType = F5_NO_TRANSLATE;
        this.connectionType = CONN_PERSISTENT;
        this.timeout = (byte) 0xE9; // Default timeout
    }

    /**
     * Parses an IRMHeader from a ByteBuf.
     *
     * @param buffer the ByteBuf containing the IRM header data
     * @return parsed IRMHeader instance
     * @throws IllegalArgumentException if buffer is too small or contains invalid data
     */
    public static IRMHeader parseFrom(ByteBuf buffer) {
        if (buffer.readableBytes() < 32) {
            throw new IllegalArgumentException("Buffer too small for IRM header fixed portion");
        }

        IRMHeader header = new IRMHeader();

        // Read fixed portion (32 bytes)
        header.totalLength = buffer.readInt();
        header.irmLength = buffer.readShort();
        header.architecture = buffer.readByte();
        header.communicationFlags = buffer.readByte();
        buffer.readBytes(header.irmId);
        header.nakReasonCode = buffer.readShort();
        header.reserved1 = buffer.readShort();
        header.inputMessageType = buffer.readByte();
        header.timeout = buffer.readByte();
        header.connectionType = buffer.readByte();
        header.encodingScheme = buffer.readByte();
        buffer.readBytes(header.clientId);

        // Read user portion based on IRM length
        int userPortionLength = header.irmLength - 28; // 32 - 4 (total length field)
        if (buffer.readableBytes() < userPortionLength) {
            throw new IllegalArgumentException("Buffer too small for IRM user portion");
        }

        if (userPortionLength >= 48) { // Minimum for basic user portion
            header.userFlags1 = buffer.readByte();
            header.commitMode = buffer.readByte();
            header.syncFlags = buffer.readByte();
            header.messageType = buffer.readByte();
            buffer.readBytes(header.transactionCode);
            buffer.readBytes(header.destinationId);
            buffer.readBytes(header.ltermName);
            buffer.readBytes(header.userId);
            buffer.readBytes(header.groupId);
            buffer.readBytes(header.password);
        }

        // Extended fields based on architecture
        if (header.architecture >= IRMARCH1 && userPortionLength >= 64) {
            buffer.readBytes(header.applicationName);
            buffer.readBytes(header.rerouteName);
        }

        if (header.architecture >= IRMARCH2 && userPortionLength >= 80) {
            buffer.readBytes(header.tagAdapter);
            buffer.readBytes(header.tagMap);
        }

        if (header.architecture >= IRMARCH3 && userPortionLength >= 88) {
            buffer.readBytes(header.modName);
        }

        return header;
    }

    /**
     * Serializes the IRMHeader to a ByteBuf.
     *
     * @return ByteBuf containing the serialized header
     */
    public ByteBuf serialize() {
        int headerSize = calculateHeaderSize();
        ByteBuf buffer = Unpooled.buffer(headerSize).order(ByteOrder.BIG_ENDIAN);

        // Write fixed portion
        buffer.writeInt(totalLength);
        buffer.writeShort(irmLength);
        buffer.writeByte(architecture);
        buffer.writeByte(communicationFlags);
        buffer.writeBytes(irmId);
        buffer.writeShort(nakReasonCode);
        buffer.writeShort(reserved1);
        buffer.writeByte(inputMessageType);
        buffer.writeByte(timeout);
        buffer.writeByte(connectionType);
        buffer.writeByte(encodingScheme);
        buffer.writeBytes(clientId);

        // Write user portion
        buffer.writeByte(userFlags1);
        buffer.writeByte(commitMode);
        buffer.writeByte(syncFlags);
        buffer.writeByte(messageType);
        buffer.writeBytes(transactionCode);
        buffer.writeBytes(destinationId);
        buffer.writeBytes(ltermName);
        buffer.writeBytes(userId);
        buffer.writeBytes(groupId);
        buffer.writeBytes(password);

        if (architecture >= IRMARCH1) {
            buffer.writeBytes(applicationName);
            buffer.writeBytes(rerouteName);
        }

        if (architecture >= IRMARCH2) {
            buffer.writeBytes(tagAdapter);
            buffer.writeBytes(tagMap);
        }

        if (architecture >= IRMARCH3) {
            buffer.writeBytes(modName);
        }

        return buffer;
    }

    private int calculateHeaderSize() {
        switch (architecture) {
            case IRMARCH0: return 80;
            case IRMARCH1: return 96;
            case IRMARCH2: return 112;
            case IRMARCH3: return 120;
            default: return 120;
        }
    }

    // Getters and setters
    public int getTotalLength() { return totalLength; }
    public void setTotalLength(int totalLength) { this.totalLength = totalLength; }

    public short getIrmLength() { return irmLength; }
    public void setIrmLength(short irmLength) { this.irmLength = irmLength; }

    public byte getArchitecture() { return architecture; }
    public void setArchitecture(byte architecture) { this.architecture = architecture; }

    public byte getCommunicationFlags() { return communicationFlags; }
    public void setCommunicationFlags(byte communicationFlags) { this.communicationFlags = communicationFlags; }

    public byte[] getIrmId() { return Arrays.copyOf(irmId, irmId.length); }
    public void setIrmId(byte[] irmId) { System.arraycopy(irmId, 0, this.irmId, 0, Math.min(8, irmId.length)); }

    public short getNakReasonCode() { return nakReasonCode; }
    public void setNakReasonCode(short nakReasonCode) { this.nakReasonCode = nakReasonCode; }

    public byte getInputMessageType() { return inputMessageType; }
    public void setInputMessageType(byte inputMessageType) { this.inputMessageType = inputMessageType; }

    public byte getTimeout() { return timeout; }
    public void setTimeout(byte timeout) { this.timeout = timeout; }

    public byte getConnectionType() { return connectionType; }
    public void setConnectionType(byte connectionType) { this.connectionType = connectionType; }

    public byte[] getClientId() { return Arrays.copyOf(clientId, clientId.length); }
    public void setClientId(byte[] clientId) { System.arraycopy(clientId, 0, this.clientId, 0, Math.min(8, clientId.length)); }

    public String getClientIdAsString() { return EbcdicConverter.fromFixedLengthEbcdic(clientId); }
    public void setClientId(String clientId) {
        this.clientId = EbcdicConverter.toFixedLengthEbcdic(clientId, 8, ' ');
    }

    public byte getUserFlags1() { return userFlags1; }
    public void setUserFlags1(byte userFlags1) { this.userFlags1 = userFlags1; }

    public byte getCommitMode() { return commitMode; }
    public void setCommitMode(byte commitMode) { this.commitMode = commitMode; }

    public byte getSyncFlags() { return syncFlags; }
    public void setSyncFlags(byte syncFlags) { this.syncFlags = syncFlags; }

    public byte getMessageType() { return messageType; }
    public void setMessageType(byte messageType) { this.messageType = messageType; }

    public byte[] getTransactionCode() { return Arrays.copyOf(transactionCode, transactionCode.length); }
    public void setTransactionCode(byte[] transactionCode) {
        System.arraycopy(transactionCode, 0, this.transactionCode, 0, Math.min(8, transactionCode.length));
    }

    public String getTransactionCodeAsString() { return EbcdicConverter.fromFixedLengthEbcdic(transactionCode); }
    public void setTransactionCode(String transactionCode) {
        this.transactionCode = EbcdicConverter.toFixedLengthEbcdic(transactionCode, 8, ' ');
    }

    public byte[] getDestinationId() { return Arrays.copyOf(destinationId, destinationId.length); }
    public void setDestinationId(byte[] destinationId) {
        System.arraycopy(destinationId, 0, this.destinationId, 0, Math.min(8, destinationId.length));
    }

    public String getDestinationIdAsString() { return EbcdicConverter.fromFixedLengthEbcdic(destinationId); }
    public void setDestinationId(String destinationId) {
        this.destinationId = EbcdicConverter.toFixedLengthEbcdic(destinationId, 8, ' ');
    }

    public byte[] getUserId() { return Arrays.copyOf(userId, userId.length); }
    public void setUserId(byte[] userId) {
        System.arraycopy(userId, 0, this.userId, 0, Math.min(8, userId.length));
    }

    public String getUserIdAsString() { return EbcdicConverter.fromFixedLengthEbcdic(userId); }
    public void setUserId(String userId) {
        this.userId = EbcdicConverter.toFixedLengthEbcdic(userId, 8, ' ');
    }

    public byte[] getGroupId() { return Arrays.copyOf(groupId, groupId.length); }
    public void setGroupId(byte[] groupId) {
        System.arraycopy(groupId, 0, this.groupId, 0, Math.min(8, groupId.length));
    }

    public String getGroupIdAsString() { return EbcdicConverter.fromFixedLengthEbcdic(groupId); }
    public void setGroupId(String groupId) {
        this.groupId = EbcdicConverter.toFixedLengthEbcdic(groupId, 8, ' ');
    }

    public byte[] getPassword() { return Arrays.copyOf(password, password.length); }
    public void setPassword(byte[] password) {
        System.arraycopy(password, 0, this.password, 0, Math.min(8, password.length));
    }

    public String getPasswordAsString() { return EbcdicConverter.fromFixedLengthEbcdic(password); }
    public void setPassword(String password) {
        this.password = EbcdicConverter.toFixedLengthEbcdic(password, 8, ' ');
    }

    @Override
    public String toString() {
        return String.format("IRMHeader{totalLength=%d, arch=%d, clientId='%s', txnCode='%s', destId='%s', msgType=0x%02X}",
                totalLength, architecture, getClientIdAsString(), getTransactionCodeAsString(),
                getDestinationIdAsString(), messageType & 0xFF);
    }
}
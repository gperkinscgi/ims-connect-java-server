package com.cgi.icbc.imsconnect.protocol;

import com.cgi.icbc.imsconnect.util.EbcdicConverter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.ByteOrder;

/**
 * Builder for IMS Connect response message segments including CSM, RSM, CID, and RMM.
 */
public class ResponseBuilder {

    // Response segment identifiers
    private static final String CSM_ID = "*CSMOKY*";      // Complete Status Message
    private static final String RSM_ID = "*REQSTS*";      // Request Status Message
    private static final String CID_ID = "*GENCID*";      // Generated Client ID
    private static final String RMM_ID = "*REQMOD*";      // Request Mod Message

    // CSM message flags
    private static final byte CSM_MSG_FLAG = 0x00;        // Message flag
    private static final byte CSM_PROTO_FLAG = 0x00;      // Protocol flag

    // RSM status flags
    private static final byte RSM_STATUS_ERROR = 0x01;    // Error status
    private static final byte RSM_RACF_RC = 0x00;         // RACF reason code

    /**
     * Creates a Complete Status Message (CSM) indicating successful completion.
     *
     * @return ByteBuf containing the CSM segment
     */
    public static ByteBuf createCompleteStatusMessage() {
        ByteBuf buffer = Unpooled.buffer(12).order(ByteOrder.BIG_ENDIAN);

        // LL - segment length (12 bytes total)
        buffer.writeShort(12);

        // Message flag
        buffer.writeByte(CSM_MSG_FLAG);

        // Protocol flag
        buffer.writeByte(CSM_PROTO_FLAG);

        // ID - "*CSMOKY*" in EBCDIC
        byte[] csmIdBytes = EbcdicConverter.toFixedLengthEbcdic(CSM_ID, 8, ' ');
        buffer.writeBytes(csmIdBytes);

        return buffer;
    }

    /**
     * Creates a Request Status Message (RSM) indicating an error.
     *
     * @param returnCode IMS return code
     * @param reasonCode IMS reason code
     * @return ByteBuf containing the RSM segment
     */
    public static ByteBuf createRequestStatusMessage(int returnCode, int reasonCode) {
        ByteBuf buffer = Unpooled.buffer(20).order(ByteOrder.BIG_ENDIAN);

        // LL - segment length (20 bytes total)
        buffer.writeShort(20);

        // Status flag
        buffer.writeByte(RSM_STATUS_ERROR);

        // RACF reason code
        buffer.writeByte(RSM_RACF_RC);

        // ID - "*REQSTS*" in EBCDIC
        byte[] rsmIdBytes = EbcdicConverter.toFixedLengthEbcdic(RSM_ID, 8, ' ');
        buffer.writeBytes(rsmIdBytes);

        // Return code (4 bytes, big-endian)
        buffer.writeInt(returnCode);

        // Reason code (4 bytes, big-endian)
        buffer.writeInt(reasonCode);

        return buffer;
    }

    /**
     * Creates a Client ID segment for returning generated client IDs.
     *
     * @param clientId the generated client ID
     * @return ByteBuf containing the CID segment
     */
    public static ByteBuf createClientIdSegment(String clientId) {
        ByteBuf buffer = Unpooled.buffer(20).order(ByteOrder.BIG_ENDIAN);

        // LL - segment length (20 bytes total)
        buffer.writeShort(20);

        // ZZ - zero field
        buffer.writeShort(0);

        // ID - "*GENCID*" in EBCDIC
        byte[] cidIdBytes = EbcdicConverter.toFixedLengthEbcdic(CID_ID, 8, ' ');
        buffer.writeBytes(cidIdBytes);

        // Client ID (8 bytes, EBCDIC, padded)
        byte[] clientIdBytes = EbcdicConverter.toFixedLengthEbcdic(clientId, 8, ' ');
        buffer.writeBytes(clientIdBytes);

        return buffer;
    }

    /**
     * Creates a Request Mod Message (RMM) segment for returning MFS mod names.
     *
     * @param modName the MFS mod name
     * @return ByteBuf containing the RMM segment
     */
    public static ByteBuf createRequestModSegment(String modName) {
        ByteBuf buffer = Unpooled.buffer(20).order(ByteOrder.BIG_ENDIAN);

        // LL - segment length (20 bytes total)
        buffer.writeShort(20);

        // ZZ - zero field
        buffer.writeShort(0);

        // ID - "*REQMOD*" in EBCDIC
        byte[] rmmIdBytes = EbcdicConverter.toFixedLengthEbcdic(RMM_ID, 8, ' ');
        buffer.writeBytes(rmmIdBytes);

        // MOD name (8 bytes, EBCDIC, padded)
        byte[] modNameBytes = EbcdicConverter.toFixedLengthEbcdic(modName, 8, ' ');
        buffer.writeBytes(modNameBytes);

        return buffer;
    }

    /**
     * Creates a data segment with LL/ZZ header from string data.
     *
     * @param data the data to include in the segment
     * @param useEbcdic whether to convert the data to EBCDIC
     * @return ByteBuf containing the data segment
     */
    public static ByteBuf createDataSegment(String data, boolean useEbcdic) {
        byte[] dataBytes;
        if (useEbcdic) {
            dataBytes = EbcdicConverter.asciiToEbcdic(data);
        } else {
            dataBytes = data.getBytes();
        }

        return createDataSegment(dataBytes);
    }

    /**
     * Creates a data segment with LL/ZZ header from byte data.
     *
     * @param data the data bytes to include in the segment
     * @return ByteBuf containing the data segment
     */
    public static ByteBuf createDataSegment(byte[] data) {
        int totalLength = data.length + 4; // +4 for LL/ZZ
        ByteBuf buffer = Unpooled.buffer(totalLength).order(ByteOrder.BIG_ENDIAN);

        // LL - segment length
        buffer.writeShort(totalLength);

        // ZZ - zero field
        buffer.writeShort(0);

        // Data
        buffer.writeBytes(data);

        return buffer;
    }

    /**
     * Creates an acknowledgment response for send-only transactions.
     *
     * @return ByteBuf containing ACK response
     */
    public static ByteBuf createAckResponse() {
        // Simple CSM with no data segments
        return createCompleteStatusMessage();
    }

    /**
     * Creates a negative acknowledgment response.
     *
     * @param reasonCode NAK reason code
     * @return ByteBuf containing NAK response
     */
    public static ByteBuf createNakResponse(int reasonCode) {
        return createRequestStatusMessage(reasonCode, reasonCode);
    }

    /**
     * Validates if a segment identifier matches expected value.
     *
     * @param segmentData the segment data to check
     * @param expectedId the expected identifier (e.g., "*CSMOKY*")
     * @return true if the segment contains the expected identifier
     */
    public static boolean hasSegmentId(byte[] segmentData, String expectedId) {
        if (segmentData.length < 12) { // Minimum size for ID check
            return false;
        }

        // Extract ID portion (bytes 4-11 for segments with LL/ZZ header)
        byte[] idBytes = new byte[8];
        System.arraycopy(segmentData, 4, idBytes, 0, 8);

        String actualId = EbcdicConverter.fromFixedLengthEbcdic(idBytes);
        return expectedId.equals(actualId);
    }
}
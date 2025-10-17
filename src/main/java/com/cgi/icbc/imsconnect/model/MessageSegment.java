package com.cgi.icbc.imsconnect.model;

import com.cgi.icbc.imsconnect.util.EbcdicConverter;

import java.util.Arrays;

/**
 * Represents a message segment in IMS Connect protocol.
 * Each segment has a length prefix (LL) and optional zero prefix (ZZ) followed by data.
 */
public class MessageSegment {

    private final byte[] data;

    public MessageSegment(byte[] data) {
        this.data = Arrays.copyOf(data, data.length);
    }

    public static MessageSegment fromString(String text) {
        return new MessageSegment(text.getBytes());
    }

    public static MessageSegment fromEbcdicString(String text) {
        return new MessageSegment(EbcdicConverter.asciiToEbcdic(text));
    }

    public byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    public String getDataAsString() {
        return new String(data);
    }

    public String getDataAsEbcdicString() {
        return EbcdicConverter.ebcdicToAscii(data);
    }

    public int getLength() {
        return data.length;
    }

    /**
     * Gets the total segment length including LL and ZZ prefixes.
     *
     * @return total length (data length + 4 bytes for LL/ZZ)
     */
    public int getTotalLength() {
        return data.length + 4;
    }

    @Override
    public String toString() {
        return String.format("MessageSegment{length=%d, data='%s'}", data.length, getDataAsString());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MessageSegment that = (MessageSegment) obj;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }
}
package com.cgi.icbc.imsconnect.model;

import com.cgi.icbc.imsconnect.protocol.IRMHeader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an IMS Connect request message containing header and data segments.
 */
public class IMSRequest {

    private final IRMHeader header;
    private final List<MessageSegment> segments;
    private final TransactionType transactionType;

    public IMSRequest(IRMHeader header, List<MessageSegment> segments, TransactionType transactionType) {
        this.header = header;
        this.segments = new ArrayList<>(segments);
        this.transactionType = transactionType;
    }

    public IRMHeader getHeader() {
        return header;
    }

    public List<MessageSegment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public String getTransactionCode() {
        return header.getTransactionCodeAsString();
    }

    public String getClientId() {
        return header.getClientIdAsString();
    }

    public String getDestinationId() {
        return header.getDestinationIdAsString();
    }

    public String getUserId() {
        return header.getUserIdAsString();
    }

    public String getGroupId() {
        return header.getGroupIdAsString();
    }

    /**
     * Gets the first data segment as a string.
     *
     * @return first segment data as string, or empty string if no segments
     */
    public String getFirstSegmentAsString() {
        if (segments.isEmpty()) {
            return "";
        }
        return segments.get(0).getDataAsString();
    }

    /**
     * Gets all segment data concatenated as a single string.
     *
     * @return concatenated segment data
     */
    public String getAllSegmentsAsString() {
        StringBuilder sb = new StringBuilder();
        for (MessageSegment segment : segments) {
            sb.append(segment.getDataAsString());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("IMSRequest{type=%s, txnCode='%s', clientId='%s', segments=%d}",
                transactionType, getTransactionCode(), getClientId(), segments.size());
    }
}
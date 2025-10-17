package com.cgi.icbc.imsconnect.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Represents an IMS Connect response message.
 */
public class IMSResponse {

    private final ResponseType responseType;
    private final List<MessageSegment> dataSegments;
    private final Optional<String> generatedClientId;
    private final Optional<String> modName;
    private final int returnCode;
    private final int reasonCode;
    private final String errorMessage;

    private IMSResponse(Builder builder) {
        this.responseType = builder.responseType;
        this.dataSegments = new ArrayList<>(builder.dataSegments);
        this.generatedClientId = builder.generatedClientId;
        this.modName = builder.modName;
        this.returnCode = builder.returnCode;
        this.reasonCode = builder.reasonCode;
        this.errorMessage = builder.errorMessage;
    }

    public static Builder success() {
        return new Builder(ResponseType.SUCCESS);
    }

    public static Builder error(int returnCode, int reasonCode, String message) {
        return new Builder(ResponseType.ERROR)
                .withReturnCode(returnCode)
                .withReasonCode(reasonCode)
                .withErrorMessage(message);
    }

    public static Builder ack() {
        return new Builder(ResponseType.ACK);
    }

    public static Builder nak(int reasonCode, String message) {
        return new Builder(ResponseType.NAK)
                .withReasonCode(reasonCode)
                .withErrorMessage(message);
    }

    public ResponseType getResponseType() {
        return responseType;
    }

    public List<MessageSegment> getDataSegments() {
        return Collections.unmodifiableList(dataSegments);
    }

    public Optional<String> getGeneratedClientId() {
        return generatedClientId;
    }

    public Optional<String> getModName() {
        return modName;
    }

    public int getReturnCode() {
        return returnCode;
    }

    public int getReasonCode() {
        return reasonCode;
    }

    public String getErrorMessage() {
        return errorMessage != null ? errorMessage : "";
    }

    public boolean isSuccess() {
        return responseType == ResponseType.SUCCESS;
    }

    public boolean isError() {
        return responseType == ResponseType.ERROR || responseType == ResponseType.NAK;
    }

    @Override
    public String toString() {
        return String.format("IMSResponse{type=%s, segments=%d, returnCode=%d, reasonCode=%d}",
                responseType, dataSegments.size(), returnCode, reasonCode);
    }

    public static class Builder {
        private final ResponseType responseType;
        private final List<MessageSegment> dataSegments = new ArrayList<>();
        private Optional<String> generatedClientId = Optional.empty();
        private Optional<String> modName = Optional.empty();
        private int returnCode = 0;
        private int reasonCode = 0;
        private String errorMessage;

        private Builder(ResponseType responseType) {
            this.responseType = responseType;
        }

        public Builder withDataSegment(MessageSegment segment) {
            this.dataSegments.add(segment);
            return this;
        }

        public Builder withDataSegment(String data) {
            this.dataSegments.add(MessageSegment.fromString(data));
            return this;
        }

        public Builder withDataSegments(List<MessageSegment> segments) {
            this.dataSegments.addAll(segments);
            return this;
        }

        public Builder withGeneratedClientId(String clientId) {
            this.generatedClientId = Optional.ofNullable(clientId);
            return this;
        }

        public Builder withModName(String modName) {
            this.modName = Optional.ofNullable(modName);
            return this;
        }

        public Builder withReturnCode(int returnCode) {
            this.returnCode = returnCode;
            return this;
        }

        public Builder withReasonCode(int reasonCode) {
            this.reasonCode = reasonCode;
            return this;
        }

        public Builder withErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public IMSResponse build() {
            return new IMSResponse(this);
        }
    }
}
package com.cgi.icbc.imsconnect.model;

/**
 * Enumeration of IMS Connect response types.
 */
public enum ResponseType {

    /**
     * Successful response with data.
     */
    SUCCESS,

    /**
     * Error response with return/reason codes.
     */
    ERROR,

    /**
     * Acknowledgment response.
     */
    ACK,

    /**
     * Negative acknowledgment response.
     */
    NAK
}
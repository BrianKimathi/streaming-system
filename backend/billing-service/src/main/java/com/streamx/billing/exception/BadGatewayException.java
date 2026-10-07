package com.streamx.billing.exception;

/**
 * An upstream provider (M-Pesa) rejected or failed the request (HTTP 502).
 */
public class BadGatewayException extends RuntimeException {
    public BadGatewayException(String message) {
        super(message);
    }
}

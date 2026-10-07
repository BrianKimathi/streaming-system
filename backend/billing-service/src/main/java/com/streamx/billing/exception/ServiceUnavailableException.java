package com.streamx.billing.exception;

/**
 * A provider or downstream service is not configured or not reachable (HTTP 503).
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}

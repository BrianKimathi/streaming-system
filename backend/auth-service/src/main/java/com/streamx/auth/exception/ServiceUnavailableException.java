package com.streamx.auth.exception;

/** A dependency (SMS provider, another service) is not configured or not reachable; mapped to HTTP 503. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

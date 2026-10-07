package com.streamx.media.exception;

public class ConflictException extends RuntimeException {

    private final transient Object details;

    public ConflictException(String message) {
        this(message, null);
    }

    public ConflictException(String message, Object details) {
        super(message);
        this.details = details;
    }

    public Object getDetails() {
        return details;
    }
}

package com.streamx.media.imports;

/** A link import failed for a reason the admin should see verbatim. */
public class ImportFailedException extends Exception {

    public ImportFailedException(String message) {
        super(message);
    }

    public ImportFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

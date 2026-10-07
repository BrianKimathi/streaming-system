package com.streamx.media.delivery;

import java.util.regex.Pattern;

/** Parses a single-range {@code Range: bytes=...} header (RFC 9110 section 14.1.2). */
public final class ByteRangeParser {

    private static final Pattern DIGITS = Pattern.compile("\\d{1,18}");

    private ByteRangeParser() {
    }

    public static final class InvalidRangeException extends Exception {
        public InvalidRangeException(String message) {
            super(message);
        }
    }

    /**
     * @return the satisfiable range, or {@code null} when the whole representation should be sent (no header, or a
     *         multi-range request which this server answers with the full body)
     * @throws InvalidRangeException for malformed or unsatisfiable ranges (answered with 416)
     */
    public static ByteRange parse(String header, long size) throws InvalidRangeException {
        if (header == null || header.isBlank()) {
            return null;
        }
        String value = header.trim();
        if (!value.regionMatches(true, 0, "bytes=", 0, 6)) {
            throw new InvalidRangeException("Unsupported range unit");
        }
        String spec = value.substring(6).trim();
        if (spec.contains(",")) {
            return null;
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            throw new InvalidRangeException("Malformed range");
        }
        String first = spec.substring(0, dash).trim();
        String last = spec.substring(dash + 1).trim();

        if (first.isEmpty()) {
            if (!DIGITS.matcher(last).matches()) {
                throw new InvalidRangeException("Malformed suffix range");
            }
            long suffix = Long.parseLong(last);
            if (suffix == 0 || size == 0) {
                throw new InvalidRangeException("Unsatisfiable range");
            }
            return new ByteRange(Math.max(0, size - suffix), size - 1);
        }

        if (!DIGITS.matcher(first).matches() || (!last.isEmpty() && !DIGITS.matcher(last).matches())) {
            throw new InvalidRangeException("Malformed range");
        }
        long start = Long.parseLong(first);
        if (start >= size) {
            throw new InvalidRangeException("Unsatisfiable range");
        }
        long end = size - 1;
        if (!last.isEmpty()) {
            long requestedEnd = Long.parseLong(last);
            if (requestedEnd < start) {
                throw new InvalidRangeException("Malformed range");
            }
            end = Math.min(requestedEnd, size - 1);
        }
        return new ByteRange(start, end);
    }
}

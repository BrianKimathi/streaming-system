package com.streamx.media.delivery;

/** Inclusive byte range of a representation. */
public record ByteRange(long start, long end) {

    public long length() {
        return end - start + 1;
    }

    public String contentRange(long totalSize) {
        return "bytes " + start + "-" + end + "/" + totalSize;
    }
}

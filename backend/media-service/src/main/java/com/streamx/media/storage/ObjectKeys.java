package com.streamx.media.storage;

import java.util.Locale;
import java.util.UUID;

/** Object key layout of the media bucket. */
public final class ObjectKeys {

    private ObjectKeys() {
    }

    public static String uploadPrefix(UUID uploadId) {
        return "uploads/" + uploadId + "/";
    }

    public static String uploadPart(UUID uploadId, int partNumber) {
        return uploadPrefix(uploadId) + String.format(Locale.ROOT, "part-%05d", partNumber);
    }

    public static String originalsPrefix(UUID contentId) {
        return "originals/" + contentId + "/";
    }

    public static String original(UUID contentId, String filename) {
        return originalsPrefix(contentId) + filename;
    }

    public static String hlsPrefix(UUID contentId) {
        return "hls/" + contentId + "/";
    }

    public static String hls(UUID contentId, String filename) {
        return hlsPrefix(contentId) + filename;
    }

    public static String file(UUID fileId, String filename) {
        return "files/" + fileId + "/" + filename;
    }
}

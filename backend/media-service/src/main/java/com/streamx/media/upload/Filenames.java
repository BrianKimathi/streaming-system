package com.streamx.media.upload;

import java.util.Locale;

public final class Filenames {

    private static final int MAX_LENGTH = 150;

    private Filenames() {
    }

    /** Reduces a client-supplied name to a safe, URL-friendly base name ({@code [A-Za-z0-9._-]}). */
    public static String sanitize(String name, String fallback) {
        String base = name == null ? "" : name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        String cleaned = base.trim().replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("\\.{2,}", ".");
        if (cleaned.isBlank() || cleaned.chars().allMatch(c -> c == '.' || c == '_')) {
            cleaned = fallback;
        } else if (cleaned.startsWith(".")) {
            cleaned = "file" + cleaned;
        }
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(cleaned.length() - MAX_LENGTH) : cleaned;
    }

    public static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        String bare = semicolon < 0 ? contentType : contentType.substring(0, semicolon);
        return bare.trim().toLowerCase(Locale.ROOT);
    }
}

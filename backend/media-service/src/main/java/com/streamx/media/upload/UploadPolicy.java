package com.streamx.media.upload;

import com.streamx.common.exception.BadRequestException;
import com.streamx.media.config.MediaProperties;
import com.streamx.media.domain.UploadPurpose;
import com.streamx.media.dto.CreateUploadRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Validation rules for upload sessions and the chunk arithmetic shared by every part request. */
@Component
public class UploadPolicy {

    public static final Set<String> VIDEO_EXTENSIONS =
            Set.of("mp4", "mov", "mkv", "webm", "avi", "m4v", "mpg", "mpeg", "ts");

    private static final Map<String, String> TRAILER_TYPES = Map.of(
            "mp4", "video/mp4", "m4v", "video/mp4", "webm", "video/webm", "mov", "video/quicktime");
    private static final Map<String, String> TRAILER_EXTENSION_BY_TYPE = Map.of(
            "video/mp4", "mp4", "video/x-m4v", "m4v", "video/webm", "webm", "video/quicktime", "mov");

    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");
    private static final Map<String, String> IMAGE_EXTENSION_BY_TYPE = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private static final Map<String, String> VIDEO_TYPES = Map.of(
            "mp4", "video/mp4", "m4v", "video/mp4", "mov", "video/quicktime", "mkv", "video/x-matroska",
            "webm", "video/webm", "avi", "video/x-msvideo", "mpg", "video/mpeg", "mpeg", "video/mpeg", "ts", "video/mp2t");

    public record ValidatedUpload(UploadPurpose purpose, UUID contentId, String filename, String contentType,
                                  long sizeBytes, long chunkSizeBytes, int totalParts) {
    }

    private final MediaProperties properties;

    public UploadPolicy(MediaProperties properties) {
        this.properties = properties;
    }

    public ValidatedUpload validate(CreateUploadRequest request) {
        if (request.purpose() == null) {
            throw new BadRequestException("purpose is required (VIDEO, TRAILER or IMAGE)");
        }
        long size = request.sizeBytes() == null ? 0 : request.sizeBytes();
        if (size <= 0) {
            throw new BadRequestException("sizeBytes must be greater than zero");
        }
        UUID contentId = parseOptionalUuid(request.contentId());
        String declaredType = Filenames.normalizeContentType(request.contentType());

        return switch (request.purpose()) {
            case VIDEO -> {
                if (contentId == null) {
                    throw new BadRequestException("contentId is required for video uploads");
                }
                String filename = Filenames.sanitize(request.filename(), "video.mp4");
                String extension = Filenames.extension(filename);
                if (!VIDEO_EXTENSIONS.contains(extension) && !declaredType.startsWith("video/")) {
                    throw new BadRequestException("Unsupported file type; upload a video file ("
                            + String.join(", ", VIDEO_EXTENSIONS.stream().sorted().toList()) + ")");
                }
                checkLimit(size, properties.maxVideoBytes(), "Videos");
                String type = declaredType.startsWith("video/") ? declaredType
                        : VIDEO_TYPES.getOrDefault(extension, "application/octet-stream");
                yield build(UploadPurpose.VIDEO, contentId, filename, type, size);
            }
            case TRAILER -> {
                String filename = withExtension(Filenames.sanitize(request.filename(), "trailer.mp4"),
                        TRAILER_TYPES, TRAILER_EXTENSION_BY_TYPE, declaredType,
                        "Trailers must be MP4, WebM or MOV files");
                checkLimit(size, properties.maxTrailerBytes(), "Trailers");
                yield build(UploadPurpose.TRAILER, contentId, filename,
                        TRAILER_TYPES.get(Filenames.extension(filename)), size);
            }
            case IMAGE -> {
                String filename = withExtension(Filenames.sanitize(request.filename(), "image.jpg"),
                        IMAGE_TYPES, IMAGE_EXTENSION_BY_TYPE, declaredType,
                        "Images must be JPEG, PNG or WebP files");
                checkLimit(size, properties.maxImageBytes(), "Images");
                yield build(UploadPurpose.IMAGE, contentId, filename,
                        IMAGE_TYPES.get(Filenames.extension(filename)), size);
            }
        };
    }

    private ValidatedUpload build(UploadPurpose purpose, UUID contentId, String filename, String contentType, long size) {
        long chunk = properties.chunkSizeBytes();
        return new ValidatedUpload(purpose, contentId, filename, contentType, size, chunk, totalParts(size, chunk));
    }

    /*
     * Served content types are derived from the extension, never taken from the client. A name without an allowed
     * extension is accepted only when the declared type is allowed, and then gets that type's extension.
     */
    private static String withExtension(String filename, Map<String, String> typesByExtension,
                                        Map<String, String> extensionByType, String declaredType, String error) {
        String extension = Filenames.extension(filename);
        if (typesByExtension.containsKey(extension)) {
            return filename;
        }
        String derived = extensionByType.get(declaredType);
        if (derived == null || !extension.isEmpty()) {
            throw new BadRequestException(error);
        }
        return filename + "." + derived;
    }

    private static void checkLimit(long size, long limit, String label) {
        if (size > limit) {
            throw new BadRequestException(label + " may be at most " + humanBytes(limit)
                    + " (this file is " + humanBytes(size) + ")");
        }
    }

    public static int totalParts(long sizeBytes, long chunkSizeBytes) {
        if (sizeBytes <= 0 || chunkSizeBytes <= 0) {
            throw new IllegalArgumentException("size and chunk size must be positive");
        }
        long parts = (sizeBytes + chunkSizeBytes - 1) / chunkSizeBytes;
        if (parts > 10_000) {
            throw new BadRequestException("File is too large for the configured chunk size");
        }
        return (int) parts;
    }

    /** Every part except the last is exactly one chunk; the last holds the remainder. */
    public static long expectedPartSize(long sizeBytes, long chunkSizeBytes, int partNumber) {
        int total = totalParts(sizeBytes, chunkSizeBytes);
        if (partNumber < 1 || partNumber > total) {
            throw new BadRequestException("partNumber must be between 1 and " + total);
        }
        return partNumber < total ? chunkSizeBytes : sizeBytes - chunkSizeBytes * (total - 1);
    }

    public static List<Integer> missingParts(int totalParts, Collection<Integer> received) {
        Set<Integer> have = new HashSet<>(received);
        List<Integer> missing = new ArrayList<>();
        for (int part = 1; part <= totalParts; part++) {
            if (!have.contains(part)) {
                missing.add(part);
            }
        }
        return missing;
    }

    private static UUID parseOptionalUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid content id: " + value);
        }
    }

    static String humanBytes(long bytes) {
        double gib = bytes / (1024.0 * 1024 * 1024);
        if (gib >= 1) {
            return trim(gib) + " GiB";
        }
        double mib = bytes / (1024.0 * 1024);
        if (mib >= 1) {
            return trim(mib) + " MiB";
        }
        return bytes + " bytes";
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}

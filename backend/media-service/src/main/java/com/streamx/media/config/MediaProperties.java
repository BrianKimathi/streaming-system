package com.streamx.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media")
public record MediaProperties(
        String ffmpegPath,
        String ffprobePath,
        int transcodeThreads,
        long transcodeTimeoutMinutes,
        String workDir,
        String publicBaseUrl,
        long chunkSizeBytes,
        long maxVideoBytes,
        long maxTrailerBytes,
        long maxImageBytes,
        int uploadSessionTtlHours,
        int importConnectTimeoutSeconds,
        int importReadTimeoutSeconds,
        Minio minio) {

    /** S3 multipart copy (used by composeObject) requires every part except the last to be at least 5 MiB. */
    public static final long MIN_CHUNK_SIZE_BYTES = 5L * 1024 * 1024;

    public MediaProperties {
        if (chunkSizeBytes < MIN_CHUNK_SIZE_BYTES) {
            throw new IllegalArgumentException("media.chunk-size-bytes must be at least 5 MiB");
        }
        if (minio == null) {
            minio = new Minio(null, null, null, "streamx-media", "us-east-1");
        }
    }

    public record Minio(String endpoint, String accessKey, String secretKey, String bucket, String region) {

        public boolean isConfigured() {
            return endpoint != null && !endpoint.isBlank()
                    && accessKey != null && !accessKey.isBlank()
                    && secretKey != null && !secretKey.isBlank();
        }
    }

    /** Prefixes an API path with MEDIA_PUBLIC_BASE_URL; stays relative when no base URL is configured. */
    public String publicUrl(String path) {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }
}

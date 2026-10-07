package com.streamx.media.storage;

import java.time.Instant;

public record ObjectStat(String key, long size, String etag, Instant lastModified) {
}

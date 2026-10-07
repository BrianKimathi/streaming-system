package com.streamx.media.storage;

/** Published once, when the bucket is reachable for the first time after startup. */
public record StorageReadyEvent(String bucket) {
}

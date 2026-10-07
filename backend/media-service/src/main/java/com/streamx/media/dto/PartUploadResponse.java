package com.streamx.media.dto;

import java.util.List;

public record PartUploadResponse(int partNumber, long sizeBytes, List<Integer> receivedParts) {
}

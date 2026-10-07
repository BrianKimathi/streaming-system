package com.streamx.media.service;

import com.streamx.media.domain.MediaProcessingStatus;
import com.streamx.media.dto.MediaAssetResponse;
import com.streamx.media.repository.MediaAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MediaAdminService {

    private final MediaAssetRepository repository;
    private final MediaService mediaService;

    public MediaAdminService(MediaAssetRepository repository, MediaService mediaService) {
        this.repository = repository;
        this.mediaService = mediaService;
    }

    @Transactional(readOnly = true)
    public List<MediaAssetResponse> listAssets() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(mediaService::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (MediaProcessingStatus status : MediaProcessingStatus.values()) {
            byStatus.put(status.name(), repository.countByStatus(status));
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalAssets", repository.count());
        stats.put("countByStatus", byStatus);
        stats.put("totalReadyDurationSeconds", repository.sumCompletedDurationSeconds());
        stats.put("totalSourceBytes", repository.sumFileSizeBytes());
        return stats;
    }
}

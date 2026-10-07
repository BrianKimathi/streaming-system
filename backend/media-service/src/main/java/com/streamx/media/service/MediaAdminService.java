package com.streamx.media.service;

import com.streamx.media.domain.MediaProcessingStatus;
import com.streamx.media.domain.UploadSessionStatus;
import com.streamx.media.dto.MediaAssetResponse;
import com.streamx.media.repository.MediaAssetRepository;
import com.streamx.media.repository.MediaFileRepository;
import com.streamx.media.repository.UploadSessionRepository;
import com.streamx.media.storage.ObjectStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MediaAdminService {

    private final MediaAssetRepository repository;
    private final MediaFileRepository fileRepository;
    private final UploadSessionRepository sessionRepository;
    private final ObjectStorage storage;
    private final MediaService mediaService;

    public MediaAdminService(MediaAssetRepository repository,
                             MediaFileRepository fileRepository,
                             UploadSessionRepository sessionRepository,
                             ObjectStorage storage,
                             MediaService mediaService) {
        this.repository = repository;
        this.fileRepository = fileRepository;
        this.sessionRepository = sessionRepository;
        this.storage = storage;
        this.mediaService = mediaService;
    }

    @Transactional(readOnly = true)
    public List<MediaAssetResponse> listAssets() {
        return mediaService.listAssets();
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
        stats.put("totalFiles", fileRepository.count());
        stats.put("totalFileBytes", fileRepository.sumSizeBytes());
        stats.put("openUploadSessions", sessionRepository.countByStatus(UploadSessionStatus.OPEN));
        stats.put("storageAvailable", storage.isAvailable());
        return stats;
    }
}

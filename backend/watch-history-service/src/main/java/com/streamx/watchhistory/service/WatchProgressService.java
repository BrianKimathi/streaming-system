package com.streamx.watchhistory.service;

import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.watchhistory.domain.WatchProgress;
import com.streamx.watchhistory.dto.RecordProgressRequest;
import com.streamx.watchhistory.dto.WatchProgressResponse;
import com.streamx.watchhistory.repository.WatchProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WatchProgressService {

    private static final Logger log = LoggerFactory.getLogger(WatchProgressService.class);

    private final WatchProgressRepository repository;

    public WatchProgressService(WatchProgressRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public WatchProgressResponse recordProgress(String accountIdStr, String profileIdStr, RecordProgressRequest request) {
        UUID accountId = UUID.fromString(accountIdStr);
        UUID profileId = UUID.fromString(profileIdStr);
        UUID contentId = UUID.fromString(request.getContentId());

        Optional<WatchProgress> existingOpt = repository.findByProfileIdAndContentId(profileId, contentId);
        WatchProgress progress;

        if (existingOpt.isPresent()) {
            progress = existingOpt.get();
            // Prevent stale event from overwriting newer position if gap is unreasonable, or update latest position
            if (request.getPositionSeconds() >= progress.getPositionSeconds() || (progress.getPositionSeconds() - request.getPositionSeconds() < 5)) {
                progress.setPositionSeconds(request.getPositionSeconds());
                progress.setDurationSeconds(request.getDurationSeconds());
            }
        } else {
            progress = new WatchProgress();
            progress.setAccountId(accountId);
            progress.setProfileId(profileId);
            progress.setContentId(contentId);
            progress.setPositionSeconds(request.getPositionSeconds());
            progress.setDurationSeconds(request.getDurationSeconds());
        }

        if (request.getEpisodeId() != null && !request.getEpisodeId().isBlank()) {
            progress.setEpisodeId(UUID.fromString(request.getEpisodeId()));
        }

        WatchProgress saved = repository.save(progress);
        log.info("Recorded watch progress for profile {} on content {} at {}s", profileIdStr, request.getContentId(), saved.getPositionSeconds());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WatchProgressResponse> getContinueWatching(String profileIdStr) {
        UUID profileId = UUID.fromString(profileIdStr);
        return repository.findByProfileIdAndCompletedFalseOrderByLastWatchedAtDesc(profileId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WatchProgressResponse getProgress(String profileIdStr, String contentIdStr) {
        UUID profileId = UUID.fromString(profileIdStr);
        UUID contentId = UUID.fromString(contentIdStr);

        WatchProgress progress = repository.findByProfileIdAndContentId(profileId, contentId)
                .orElseThrow(() -> new ResourceNotFoundException("Watch progress not found for contentId: " + contentIdStr));

        return mapToResponse(progress);
    }

    private WatchProgressResponse mapToResponse(WatchProgress progress) {
        return new WatchProgressResponse(
                progress.getId().toString(),
                progress.getProfileId().toString(),
                progress.getContentId().toString(),
                progress.getEpisodeId() != null ? progress.getEpisodeId().toString() : null,
                progress.getPositionSeconds(),
                progress.getDurationSeconds(),
                progress.getPercentage(),
                progress.isCompleted(),
                progress.getLastWatchedAt()
        );
    }
}

package com.streamx.watchhistory.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.watchhistory.client.TrendingClient;
import com.streamx.watchhistory.domain.TitleType;
import com.streamx.watchhistory.domain.WatchProgress;
import com.streamx.watchhistory.dto.RecordProgressRequest;
import com.streamx.watchhistory.dto.WatchProgressResponse;
import com.streamx.watchhistory.repository.WatchProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class WatchProgressService {

    public static final String SELECT_PROFILE = "Select a profile first";
    public static final int CONTINUE_WATCHING_LIMIT = 20;
    private static final int CONTINUE_WATCHING_SCAN = 500;

    private static final Logger log = LoggerFactory.getLogger(WatchProgressService.class);

    private final WatchProgressRepository repository;
    private final TrendingClient trendingClient;

    public WatchProgressService(WatchProgressRepository repository, TrendingClient trendingClient) {
        this.repository = repository;
        this.trendingClient = trendingClient;
    }

    @Transactional
    public WatchProgressResponse recordProgress(String accountIdStr, String profileIdStr, RecordProgressRequest request) {
        UUID profileId = requireProfile(profileIdStr);
        UUID accountId = parse(accountIdStr, "account id");
        UUID contentId = parse(request.getContentId(), "content id");
        UUID titleId = isBlank(request.getTitleId()) ? contentId : parse(request.getTitleId(), "title id");
        TitleType titleType = parseTitleType(request.getTitleType());

        WatchProgress progress = repository.findByProfileIdAndContentId(profileId, contentId).orElseGet(() -> {
            WatchProgress created = new WatchProgress();
            created.setAccountId(accountId);
            created.setProfileId(profileId);
            created.setContentId(contentId);
            return created;
        });
        boolean wasCompleted = progress.isCompleted();

        progress.setTitleId(titleId);
        progress.setTitleType(titleType);
        progress.setEpisodeId(titleType == TitleType.SERIES ? contentId : null);
        progress.applyPosition(request.getPositionSeconds(), request.getDurationSeconds());
        progress.setLastWatchedAt(LocalDateTime.now());

        WatchProgress saved = repository.save(progress);
        log.debug("Recorded watch progress for profile {} on content {} at {}s", profileId, contentId, saved.getPositionSeconds());

        if (!wasCompleted && saved.isCompleted()) {
            afterCommit(() -> trendingClient.recordCompletion(titleId));
        }
        return mapToResponse(saved);
    }

    /** One entry per title (its most recently watched unfinished item), newest first. */
    @Transactional(readOnly = true)
    public List<WatchProgressResponse> getContinueWatching(String profileIdStr) {
        UUID profileId = requireProfile(profileIdStr);
        List<WatchProgress> unfinished = repository.findByProfileIdAndCompletedFalseOrderByLastWatchedAtDesc(
                profileId, PageRequest.of(0, CONTINUE_WATCHING_SCAN));

        Set<UUID> seenTitles = new HashSet<>();
        List<WatchProgressResponse> result = new ArrayList<>();
        for (WatchProgress progress : unfinished) {
            UUID titleKey = progress.getTitleId() != null ? progress.getTitleId() : progress.getContentId();
            if (seenTitles.add(titleKey)) {
                result.add(mapToResponse(progress));
                if (result.size() == CONTINUE_WATCHING_LIMIT) {
                    break;
                }
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<WatchProgressResponse> getTitleProgress(String profileIdStr, String titleIdStr) {
        UUID profileId = requireProfile(profileIdStr);
        UUID titleId = parse(titleIdStr, "title id");
        return repository.findByProfileIdAndTitleIdOrderByLastWatchedAtDesc(profileId, titleId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WatchProgressResponse getProgress(String profileIdStr, String contentIdStr) {
        UUID profileId = requireProfile(profileIdStr);
        UUID contentId = parse(contentIdStr, "content id");
        WatchProgress progress = repository.findByProfileIdAndContentId(profileId, contentId)
                .orElseThrow(() -> new ResourceNotFoundException("Watch progress not found for contentId: " + contentIdStr));
        return mapToResponse(progress);
    }

    @Transactional
    public int removeTitle(String profileIdStr, String titleIdStr) {
        UUID profileId = requireProfile(profileIdStr);
        UUID titleId = parse(titleIdStr, "title id");
        return repository.deleteByProfileAndTitle(profileId, titleId);
    }

    private WatchProgressResponse mapToResponse(WatchProgress progress) {
        UUID titleId = progress.getTitleId() != null ? progress.getTitleId() : progress.getContentId();
        TitleType titleType = progress.getTitleType() != null ? progress.getTitleType() : TitleType.MOVIE;
        return new WatchProgressResponse(
                progress.getId().toString(),
                progress.getProfileId().toString(),
                progress.getContentId().toString(),
                titleId.toString(),
                titleType,
                progress.getPositionSeconds(),
                progress.getDurationSeconds(),
                progress.getPercentage(),
                progress.isCompleted(),
                progress.getLastWatchedAt()
        );
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static UUID requireProfile(String profileIdStr) {
        if (isBlank(profileIdStr)) {
            throw new BadRequestException(SELECT_PROFILE);
        }
        return parse(profileIdStr, "profile id");
    }

    private static TitleType parseTitleType(String value) {
        if (isBlank(value)) {
            return TitleType.MOVIE;
        }
        try {
            return TitleType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("titleType must be MOVIE or SERIES");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static UUID parse(String value, String label) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Invalid " + label);
        }
    }
}

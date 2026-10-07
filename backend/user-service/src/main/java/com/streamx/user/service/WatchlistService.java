package com.streamx.user.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.user.domain.Profile;
import com.streamx.user.domain.TitleType;
import com.streamx.user.domain.WatchlistItem;
import com.streamx.user.dto.WatchlistItemResponse;
import com.streamx.user.repository.ProfileRepository;
import com.streamx.user.repository.WatchlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** "My List" for the profile selected in the caller's profile token. */
@Service
public class WatchlistService {

    private final WatchlistItemRepository watchlistItemRepository;
    private final ProfileRepository profileRepository;

    public WatchlistService(WatchlistItemRepository watchlistItemRepository, ProfileRepository profileRepository) {
        this.watchlistItemRepository = watchlistItemRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> getWatchlist(String accountId, String profileId) {
        Profile profile = requireSelectedProfile(accountId, profileId);
        return watchlistItemRepository.findByProfileIdOrderByAddedAtDesc(profile.getId()).stream()
                .map(WatchlistService::toResponse)
                .toList();
    }

    @Transactional
    public WatchlistItemResponse addToWatchlist(String accountId, String profileId, String titleIdStr, TitleType titleType) {
        Profile profile = requireSelectedProfile(accountId, profileId);
        UUID titleId = parseTitleId(titleIdStr);
        if (titleType == null) {
            throw new BadRequestException("titleType is required (MOVIE or SERIES)");
        }

        WatchlistItem item = watchlistItemRepository.findByProfileIdAndTitleId(profile.getId(), titleId)
                .orElseGet(() -> new WatchlistItem(profile.getId(), titleId, titleType));
        item.setTitleType(titleType);
        return toResponse(watchlistItemRepository.save(item));
    }

    @Transactional
    public void removeFromWatchlist(String accountId, String profileId, String titleIdStr) {
        Profile profile = requireSelectedProfile(accountId, profileId);
        UUID titleId = parseTitleId(titleIdStr);
        watchlistItemRepository.findByProfileIdAndTitleId(profile.getId(), titleId)
                .ifPresent(watchlistItemRepository::delete);
    }

    private Profile requireSelectedProfile(String accountIdStr, String profileIdStr) {
        if (profileIdStr == null || profileIdStr.isBlank()) {
            throw new BadRequestException("Select a profile first");
        }
        UUID accountId = UUID.fromString(accountIdStr);
        UUID profileId;
        try {
            profileId = UUID.fromString(profileIdStr.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Select a profile first");
        }
        return profileRepository.findByIdAndAccountId(profileId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
    }

    private static UUID parseTitleId(String titleId) {
        try {
            return UUID.fromString(titleId);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Invalid title id");
        }
    }

    private static WatchlistItemResponse toResponse(WatchlistItem item) {
        return new WatchlistItemResponse(item.getTitleId().toString(), item.getTitleType(), item.getAddedAt());
    }
}

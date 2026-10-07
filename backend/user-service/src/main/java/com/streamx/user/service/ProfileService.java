package com.streamx.user.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.common.exception.UnauthorizedException;
import com.streamx.common.security.JwtUtils;
import com.streamx.user.domain.MaturityRating;
import com.streamx.user.domain.Profile;
import com.streamx.user.domain.ProfileType;
import com.streamx.user.dto.*;
import com.streamx.user.repository.ProfileRepository;
import com.streamx.user.repository.WatchlistItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final ProfileRepository profileRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    private static final int MAX_PROFILES_DEFAULT = 5;
    private static final int MAX_PIN_ATTEMPTS = 3;
    private static final int PIN_LOCKOUT_MINUTES = 15;
    private static final Pattern PIN_PATTERN = Pattern.compile("^\\d{4}$");

    public ProfileService(ProfileRepository profileRepository,
                          WatchlistItemRepository watchlistItemRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtils jwtUtils) {
        this.profileRepository = profileRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    @Transactional
    public ProfileResponse createProfile(String accountIdStr, CreateProfileRequest request) {
        UUID accountId = UUID.fromString(accountIdStr);

        long profileCount = profileRepository.countByAccountId(accountId);
        if (profileCount >= MAX_PROFILES_DEFAULT) {
            throw new BadRequestException("Maximum profile limit reached for this account");
        }

        if (request.getPin() != null) {
            requireValidPin(request.getPin());
        }
        String pinHash = null;
        if (request.isPinProtected()) {
            if (request.getPin() == null || request.getPin().isBlank()) {
                throw new BadRequestException("PIN code is required when PIN protection is enabled");
            }
            pinHash = passwordEncoder.encode(request.getPin());
        }

        MaturityRating maturity = request.getMaturityRating();
        if (maturity == null) {
            maturity = (request.getType() == ProfileType.KIDS) ? MaturityRating.TV_Y7 : MaturityRating.TV_MA;
        }

        Profile profile = new Profile();
        profile.setAccountId(accountId);
        profile.setName(request.getName());
        profile.setAvatarUrl(request.getAvatarUrl());
        profile.setType(request.getType() != null ? request.getType() : ProfileType.ADULT);
        profile.setMaturityRating(maturity);
        profile.setLanguage(request.getLanguage() != null ? request.getLanguage() : "en");
        profile.setPreferredAudio(request.getPreferredAudio() != null ? request.getPreferredAudio() : "en");
        profile.setPreferredSubtitle(request.getPreferredSubtitle() != null ? request.getPreferredSubtitle() : "off");
        profile.setPinProtected(request.isPinProtected());
        profile.setPinHash(pinHash);

        Profile saved = profileRepository.save(profile);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProfileResponse> getAccountProfiles(String accountIdStr) {
        UUID accountId = UUID.fromString(accountIdStr);
        return profileRepository.findByAccountIdOrderByCreatedAtAsc(accountId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String accountIdStr, String profileIdStr) {
        Profile profile = getProfileEntity(accountIdStr, profileIdStr);
        return mapToResponse(profile);
    }

    @Transactional
    public ProfileResponse updateProfile(String accountIdStr, String profileIdStr, UpdateProfileRequest request) {
        Profile profile = getProfileEntity(accountIdStr, profileIdStr);

        if (request.getName() != null && !request.getName().isBlank()) {
            profile.setName(request.getName());
        }
        if (request.getAvatarUrl() != null) {
            profile.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getMaturityRating() != null) {
            profile.setMaturityRating(request.getMaturityRating());
        }
        if (request.getLanguage() != null) {
            profile.setLanguage(request.getLanguage());
        }
        if (request.getPreferredAudio() != null) {
            profile.setPreferredAudio(request.getPreferredAudio());
        }
        if (request.getPreferredSubtitle() != null) {
            profile.setPreferredSubtitle(request.getPreferredSubtitle());
        }
        if (request.getAutoplayNext() != null) {
            profile.setAutoplayNext(request.getAutoplayNext());
        }
        boolean pinSupplied = request.getPin() != null;
        if (pinSupplied) {
            requireValidPin(request.getPin());
        }
        if (Boolean.FALSE.equals(request.getPinProtected())) {
            profile.setPinProtected(false);
            profile.setPinHash(null);
            resetPinLock(profile);
        } else if (Boolean.TRUE.equals(request.getPinProtected()) || (pinSupplied && profile.isPinProtected())) {
            if (pinSupplied) {
                profile.setPinHash(passwordEncoder.encode(request.getPin()));
                resetPinLock(profile);
            } else if (profile.getPinHash() == null) {
                throw new BadRequestException("A 4-digit PIN is required to enable PIN protection");
            }
            profile.setPinProtected(true);
        }

        Profile updated = profileRepository.save(profile);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteProfile(String accountIdStr, String profileIdStr) {
        Profile profile = getProfileEntity(accountIdStr, profileIdStr);
        watchlistItemRepository.deleteByProfileId(profile.getId());
        profileRepository.delete(profile);
    }

    @Transactional(readOnly = true)
    public ProfileOwnerResponse getProfileOwner(String profileIdStr) {
        UUID profileId;
        try {
            profileId = UUID.fromString(profileIdStr);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Profile not found");
        }
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        return new ProfileOwnerResponse(profile.getAccountId().toString());
    }

    @Transactional
    public boolean verifyPin(String accountIdStr, String profileIdStr, String pin) {
        Profile profile = getProfileEntity(accountIdStr, profileIdStr);

        if (!profile.isPinProtected() || profile.getPinHash() == null) {
            return true;
        }

        if (profile.getPinLockedUntil() != null && profile.getPinLockedUntil().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Profile PIN is locked due to too many failed attempts. Try again later.");
        }

        if (!passwordEncoder.matches(pin, profile.getPinHash())) {
            int attempts = profile.getFailedPinAttempts() + 1;
            profile.setFailedPinAttempts(attempts);
            if (attempts >= MAX_PIN_ATTEMPTS) {
                profile.setPinLockedUntil(LocalDateTime.now().plusMinutes(PIN_LOCKOUT_MINUTES));
                profile.setFailedPinAttempts(0);
            }
            profileRepository.save(profile);
            throw new UnauthorizedException("Incorrect PIN code.");
        }

        profile.setFailedPinAttempts(0);
        profile.setPinLockedUntil(null);
        profileRepository.save(profile);
        return true;
    }

    @Transactional
    public SelectProfileResponse selectProfile(String accountIdStr, String userEmail, List<String> roles, String profileIdStr, String pin) {
        Profile profile = getProfileEntity(accountIdStr, profileIdStr);

        if (profile.isPinProtected()) {
            if (pin == null || pin.isBlank()) {
                throw new BadRequestException("PIN required to select this profile");
            }
            verifyPin(accountIdStr, profileIdStr, pin);
        }

        String profileAccessToken = jwtUtils.generateProfileAccessToken(
                accountIdStr,
                userEmail,
                roles,
                profile.getId().toString()
        );

        return new SelectProfileResponse(profileAccessToken, mapToResponse(profile));
    }

    private Profile getProfileEntity(String accountIdStr, String profileIdStr) {
        UUID accountId = UUID.fromString(accountIdStr);
        UUID profileId;
        try {
            profileId = UUID.fromString(profileIdStr);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Profile not found");
        }
        return profileRepository.findByIdAndAccountId(profileId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
    }

    private static void requireValidPin(String pin) {
        if (pin == null || !PIN_PATTERN.matcher(pin).matches()) {
            throw new BadRequestException("PIN must be exactly 4 digits");
        }
    }

    private static void resetPinLock(Profile profile) {
        profile.setFailedPinAttempts(0);
        profile.setPinLockedUntil(null);
    }

    private ProfileResponse mapToResponse(Profile profile) {
        ProfileResponse response = new ProfileResponse();
        response.setId(profile.getId().toString());
        response.setAccountId(profile.getAccountId().toString());
        response.setName(profile.getName());
        response.setAvatarUrl(profile.getAvatarUrl());
        response.setType(profile.getType());
        response.setMaturityRating(profile.getMaturityRating());
        response.setLanguage(profile.getLanguage());
        response.setPreferredAudio(profile.getPreferredAudio());
        response.setPreferredSubtitle(profile.getPreferredSubtitle());
        response.setAutoplayNext(profile.isAutoplayNext());
        response.setPinProtected(profile.isPinProtected());
        response.setCreatedAt(profile.getCreatedAt());
        return response;
    }
}

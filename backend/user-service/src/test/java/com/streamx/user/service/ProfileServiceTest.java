package com.streamx.user.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.common.exception.UnauthorizedException;
import com.streamx.common.security.JwtUtils;
import com.streamx.user.domain.MaturityRating;
import com.streamx.user.domain.ProfileType;
import com.streamx.user.domain.TitleType;
import com.streamx.user.dto.CreateProfileRequest;
import com.streamx.user.dto.ProfileResponse;
import com.streamx.user.dto.SelectProfileResponse;
import com.streamx.user.dto.UpdateProfileRequest;
import com.streamx.user.repository.WatchlistItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileServiceTest {

    @Autowired
    private ProfileService profileService;

    @Autowired
    private WatchlistService watchlistService;

    @Autowired
    private WatchlistItemRepository watchlistItemRepository;

    @Autowired
    private JwtUtils jwtUtils;

    private ProfileResponse createProfile(String accountId, String name) {
        CreateProfileRequest request = new CreateProfileRequest();
        request.setName(name);
        return profileService.createProfile(accountId, request);
    }

    @Test
    void testCreateProfileSuccess() {
        String accountId = UUID.randomUUID().toString();
        CreateProfileRequest request = new CreateProfileRequest();
        request.setName("Alice");
        request.setType(ProfileType.ADULT);
        request.setMaturityRating(MaturityRating.TV_MA);

        ProfileResponse response = profileService.createProfile(accountId, request);

        assertNotNull(response.getId());
        assertEquals("Alice", response.getName());
        assertEquals(ProfileType.ADULT, response.getType());
        assertFalse(response.isPinProtected());
    }

    @Test
    void testCreatePinProtectedProfileSuccess() {
        String accountId = UUID.randomUUID().toString();
        CreateProfileRequest request = new CreateProfileRequest();
        request.setName("Bob Protected");
        request.setPinProtected(true);
        request.setPin("1234");

        ProfileResponse response = profileService.createProfile(accountId, request);

        assertNotNull(response.getId());
        assertTrue(response.isPinProtected());
    }

    @Test
    void testCreateProfileRejectsInvalidPin() {
        String accountId = UUID.randomUUID().toString();
        for (String badPin : List.of("123", "12345", "12a4", "")) {
            CreateProfileRequest request = new CreateProfileRequest();
            request.setName("Bad Pin");
            request.setPinProtected(true);
            request.setPin(badPin);

            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> profileService.createProfile(accountId, request), "PIN '" + badPin + "' should be rejected");
            assertEquals("PIN must be exactly 4 digits", ex.getMessage());
        }
    }

    @Test
    void testCreateProtectedProfileWithoutPinFails() {
        CreateProfileRequest request = new CreateProfileRequest();
        request.setName("No Pin");
        request.setPinProtected(true);

        assertThrows(BadRequestException.class,
                () -> profileService.createProfile(UUID.randomUUID().toString(), request));
    }

    @Test
    void testMaxFiveProfilesPerAccount() {
        String accountId = UUID.randomUUID().toString();
        for (int i = 1; i <= 5; i++) {
            createProfile(accountId, "Profile " + i);
        }

        assertThrows(BadRequestException.class, () -> createProfile(accountId, "Profile 6"));
    }

    @Test
    void testEnablingPinProtectionRequiresValidPin() {
        String accountId = UUID.randomUUID().toString();
        ProfileResponse profile = createProfile(accountId, "Enable Pin");

        UpdateProfileRequest withoutPin = new UpdateProfileRequest();
        withoutPin.setPinProtected(true);
        assertThrows(BadRequestException.class,
                () -> profileService.updateProfile(accountId, profile.getId(), withoutPin));

        UpdateProfileRequest badPin = new UpdateProfileRequest();
        badPin.setPinProtected(true);
        badPin.setPin("99");
        assertThrows(BadRequestException.class,
                () -> profileService.updateProfile(accountId, profile.getId(), badPin));

        UpdateProfileRequest goodPin = new UpdateProfileRequest();
        goodPin.setPinProtected(true);
        goodPin.setPin("2468");
        assertTrue(profileService.updateProfile(accountId, profile.getId(), goodPin).isPinProtected());
        assertTrue(profileService.verifyPin(accountId, profile.getId(), "2468"));

        UpdateProfileRequest disable = new UpdateProfileRequest();
        disable.setPinProtected(false);
        assertFalse(profileService.updateProfile(accountId, profile.getId(), disable).isPinProtected());
    }

    @Test
    void testPinVerificationAndLockout() {
        String accountId = UUID.randomUUID().toString();
        CreateProfileRequest request = new CreateProfileRequest();
        request.setName("Secure User");
        request.setPinProtected(true);
        request.setPin("4321");

        ProfileResponse response = profileService.createProfile(accountId, request);

        assertTrue(profileService.verifyPin(accountId, response.getId(), "4321"));

        UnauthorizedException wrong = assertThrows(UnauthorizedException.class,
                () -> profileService.verifyPin(accountId, response.getId(), "0000"));
        assertEquals("Incorrect PIN code.", wrong.getMessage());
        assertThrows(UnauthorizedException.class, () -> profileService.verifyPin(accountId, response.getId(), "0000"));
        assertThrows(UnauthorizedException.class, () -> profileService.verifyPin(accountId, response.getId(), "0000"));

        // Locked after three failures, even with the right PIN
        assertThrows(BadRequestException.class, () -> profileService.verifyPin(accountId, response.getId(), "4321"));
    }

    @Test
    void testSelectProfileGeneratesScopedTokenWithCallerIdentity() {
        String accountId = UUID.randomUUID().toString();
        ProfileResponse profileRes = createProfile(accountId, "Profile Token Test");

        SelectProfileResponse selectRes = profileService.selectProfile(
                accountId, "viewer@example.com", List.of("ROLE_USER", "ROLE_ANALYST"), profileRes.getId(), null);

        String token = selectRes.getProfileAccessToken();
        assertEquals(profileRes.getId(), selectRes.getProfile().getId());
        assertEquals(profileRes.getId(), jwtUtils.getProfileIdFromToken(token));
        assertEquals(accountId, jwtUtils.getAccountIdFromToken(token));
        assertEquals("viewer@example.com", jwtUtils.getEmailFromToken(token));
        assertEquals(List.of("ROLE_USER", "ROLE_ANALYST"), jwtUtils.getRolesFromToken(token));
    }

    @Test
    void testSelectPinProtectedProfileRequiresPin() {
        String accountId = UUID.randomUUID().toString();
        CreateProfileRequest request = new CreateProfileRequest();
        request.setName("Locked");
        request.setPinProtected(true);
        request.setPin("1357");
        ProfileResponse profile = profileService.createProfile(accountId, request);

        assertThrows(BadRequestException.class, () -> profileService.selectProfile(
                accountId, "a@example.com", List.of("ROLE_USER"), profile.getId(), null));
        assertThrows(UnauthorizedException.class, () -> profileService.selectProfile(
                accountId, "a@example.com", List.of("ROLE_USER"), profile.getId(), "0000"));
        assertNotNull(profileService.selectProfile(
                accountId, "a@example.com", List.of("ROLE_USER"), profile.getId(), "1357").getProfileAccessToken());
    }

    @Test
    void testSelectProfileOfAnotherAccountIsNotFound() {
        ProfileResponse profile = createProfile(UUID.randomUUID().toString(), "Someone Else");

        assertThrows(ResourceNotFoundException.class, () -> profileService.selectProfile(
                UUID.randomUUID().toString(), "a@example.com", List.of("ROLE_USER"), profile.getId(), null));
    }

    @Test
    void testGetProfileOwner() {
        String accountId = UUID.randomUUID().toString();
        ProfileResponse profile = createProfile(accountId, "Owned");

        assertEquals(accountId, profileService.getProfileOwner(profile.getId()).accountId());
        assertThrows(ResourceNotFoundException.class, () -> profileService.getProfileOwner(UUID.randomUUID().toString()));
        assertThrows(ResourceNotFoundException.class, () -> profileService.getProfileOwner("not-a-uuid"));
    }

    @Test
    void testDeletingProfileDeletesItsWatchlist() {
        String accountId = UUID.randomUUID().toString();
        ProfileResponse profile = createProfile(accountId, "To Delete");
        watchlistService.addToWatchlist(accountId, profile.getId(), UUID.randomUUID().toString(), TitleType.MOVIE);
        watchlistService.addToWatchlist(accountId, profile.getId(), UUID.randomUUID().toString(), TitleType.SERIES);

        profileService.deleteProfile(accountId, profile.getId());

        assertTrue(watchlistItemRepository.findByProfileIdOrderByAddedAtDesc(UUID.fromString(profile.getId())).isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> profileService.getProfile(accountId, profile.getId()));
    }
}

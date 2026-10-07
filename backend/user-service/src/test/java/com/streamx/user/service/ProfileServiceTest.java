package com.streamx.user.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.UnauthorizedException;
import com.streamx.user.domain.MaturityRating;
import com.streamx.user.domain.ProfileType;
import com.streamx.user.dto.CreateProfileRequest;
import com.streamx.user.dto.ProfileResponse;
import com.streamx.user.dto.SelectProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileServiceTest {

    @Autowired
    private ProfileService profileService;

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
    void testPinVerificationAndLockout() {
        String accountId = UUID.randomUUID().toString();
        CreateProfileRequest request = new CreateProfileRequest();
        request.setName("Secure User");
        request.setPinProtected(true);
        request.setPin("4321");

        ProfileResponse response = profileService.createProfile(accountId, request);

        // Verify correct PIN
        assertTrue(profileService.verifyPin(accountId, response.getId(), "4321"));

        // Verify incorrect PIN attempts
        assertThrows(UnauthorizedException.class, () -> profileService.verifyPin(accountId, response.getId(), "0000"));
        assertThrows(UnauthorizedException.class, () -> profileService.verifyPin(accountId, response.getId(), "0000"));
        assertThrows(UnauthorizedException.class, () -> profileService.verifyPin(accountId, response.getId(), "0000"));

        // 4th attempt should throw locked exception
        assertThrows(BadRequestException.class, () -> profileService.verifyPin(accountId, response.getId(), "4321"));
    }

    @Test
    void testSelectProfileGeneratesScopedToken() {
        String accountId = UUID.randomUUID().toString();
        CreateProfileRequest request = new CreateProfileRequest();
        request.setName("Profile Token Test");

        ProfileResponse profileRes = profileService.createProfile(accountId, request);

        SelectProfileResponse selectRes = profileService.selectProfile(
                accountId, "test@streamx.com", Collections.singletonList("ROLE_USER"), profileRes.getId(), null
        );

        assertNotNull(selectRes.getProfileAccessToken());
        assertEquals(profileRes.getId(), selectRes.getProfile().getId());
    }
}

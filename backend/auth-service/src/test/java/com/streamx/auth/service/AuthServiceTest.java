package com.streamx.auth.service;

import com.streamx.auth.client.ProfileDirectoryClient;
import com.streamx.auth.dto.*;
import com.streamx.auth.exception.ServiceUnavailableException;
import com.streamx.auth.repository.AccountRepository;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.common.exception.UnauthorizedException;
import com.streamx.common.security.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @MockitoBean
    private ProfileDirectoryClient profileDirectoryClient;

    private AuthResponse register(String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        return authService.register(request);
    }

    @Test
    void testRegisterSuccess() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("john.doe@example.com");
        request.setPassword("Password123!");
        request.setPhoneNumber("+1234567890");

        AuthResponse response = authService.register(request);

        assertNotNull(response.getAccountId());
        assertEquals("john.doe@example.com", response.getEmail());
        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertTrue(accountRepository.existsByEmail("john.doe@example.com"));
    }

    @Test
    void testRegisterNormalisesEmail() {
        AuthResponse response = register("  Mixed.Case@Example.COM ", "Password123!");

        assertEquals("mixed.case@example.com", response.getEmail());
        assertTrue(accountRepository.existsByEmail("mixed.case@example.com"));
    }

    @Test
    void testRegisterDuplicateEmailThrowsException() {
        register("duplicate@example.com", "Password123!");

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> register("duplicate@example.com", "AnotherPassword123!"));
        assertEquals("Email address is already in use", ex.getMessage());
    }

    @Test
    void testRegisterDuplicateEmailDifferentCaseThrowsException() {
        register("case.dup@example.com", "Password123!");

        assertThrows(BadRequestException.class, () -> register(" CASE.Dup@Example.com", "Password123!"));
    }

    @Test
    void testLoginSuccess() {
        register("user.login@example.com", "SecurePass123!");

        AuthResponse response = authService.login(new LoginRequest("user.login@example.com", "SecurePass123!"));

        assertNotNull(response.getAccessToken());
        assertEquals("user.login@example.com", response.getEmail());
    }

    @Test
    void testLoginIsCaseInsensitiveForEmail() {
        register("case.login@example.com", "SecurePass123!");

        AuthResponse response = authService.login(new LoginRequest("  CASE.Login@EXAMPLE.com ", "SecurePass123!"));

        assertEquals("case.login@example.com", response.getEmail());
    }

    @Test
    void testLoginInvalidPasswordThrowsUnauthorized() {
        register("user.invalid@example.com", "CorrectPassword123!");

        assertThrows(UnauthorizedException.class,
                () -> authService.login(new LoginRequest("user.invalid@example.com", "WrongPassword123!")));
    }

    @Test
    void testRefreshWithoutProfileReturnsAccountToken() {
        AuthResponse registered = register("refresh.plain@example.com", "Password123!");

        AuthResponse refreshed = authService.refreshToken(new RefreshTokenRequest(registered.getRefreshToken()));

        assertNull(jwtUtils.getProfileIdFromToken(refreshed.getAccessToken()));
        assertEquals(registered.getRefreshToken(), refreshed.getRefreshToken());
        verifyNoInteractions(profileDirectoryClient);
    }

    @Test
    void testRefreshWithOwnedProfileReturnsProfileToken() {
        AuthResponse registered = register("refresh.profile@example.com", "Password123!");
        UUID profileId = UUID.randomUUID();
        when(profileDirectoryClient.findOwnerAccountId(profileId))
                .thenReturn(Optional.of(UUID.fromString(registered.getAccountId())));

        AuthResponse refreshed = authService.refreshToken(
                new RefreshTokenRequest(registered.getRefreshToken(), profileId.toString()));

        assertEquals(profileId.toString(), jwtUtils.getProfileIdFromToken(refreshed.getAccessToken()));
        assertEquals(registered.getAccountId(), jwtUtils.getAccountIdFromToken(refreshed.getAccessToken()));
        assertEquals("refresh.profile@example.com", jwtUtils.getEmailFromToken(refreshed.getAccessToken()));
    }

    @Test
    void testRefreshWithProfileOfAnotherAccountIsNotFound() {
        AuthResponse registered = register("refresh.other@example.com", "Password123!");
        UUID profileId = UUID.randomUUID();
        when(profileDirectoryClient.findOwnerAccountId(profileId)).thenReturn(Optional.of(UUID.randomUUID()));

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> authService.refreshToken(
                new RefreshTokenRequest(registered.getRefreshToken(), profileId.toString())));
        assertEquals("Profile not found", ex.getMessage());
    }

    @Test
    void testRefreshWithMissingProfileIsNotFound() {
        AuthResponse registered = register("refresh.missing@example.com", "Password123!");
        UUID profileId = UUID.randomUUID();
        when(profileDirectoryClient.findOwnerAccountId(profileId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.refreshToken(
                new RefreshTokenRequest(registered.getRefreshToken(), profileId.toString())));
    }

    @Test
    void testRefreshWithProfileWhenUserServiceDownIsUnavailable() {
        AuthResponse registered = register("refresh.down@example.com", "Password123!");
        UUID profileId = UUID.randomUUID();
        when(profileDirectoryClient.findOwnerAccountId(profileId))
                .thenThrow(new ServiceUnavailableException("Profile service is unavailable"));

        assertThrows(ServiceUnavailableException.class, () -> authService.refreshToken(
                new RefreshTokenRequest(registered.getRefreshToken(), profileId.toString())));
    }

    @Test
    void testLogoutWithTokenRevokesOnlyThatToken() {
        AuthResponse first = register("logout.single@example.com", "Password123!");
        AuthResponse second = authService.login(new LoginRequest("logout.single@example.com", "Password123!"));

        authService.logout(first.getAccountId(), first.getRefreshToken());

        assertThrows(UnauthorizedException.class,
                () -> authService.refreshToken(new RefreshTokenRequest(first.getRefreshToken())));
        assertNotNull(authService.refreshToken(new RefreshTokenRequest(second.getRefreshToken())).getAccessToken());
    }

    @Test
    void testLogoutIgnoresTokenOfAnotherAccount() {
        AuthResponse victim = register("logout.victim@example.com", "Password123!");
        AuthResponse attacker = register("logout.attacker@example.com", "Password123!");

        authService.logout(attacker.getAccountId(), victim.getRefreshToken());

        assertNotNull(authService.refreshToken(new RefreshTokenRequest(victim.getRefreshToken())).getAccessToken());
    }

    @Test
    void testLogoutWithoutTokenRevokesAllTokens() {
        AuthResponse first = register("logout.all@example.com", "Password123!");
        AuthResponse second = authService.login(new LoginRequest("logout.all@example.com", "Password123!"));

        authService.logout(first.getAccountId(), null);

        assertThrows(UnauthorizedException.class,
                () -> authService.refreshToken(new RefreshTokenRequest(first.getRefreshToken())));
        assertThrows(UnauthorizedException.class,
                () -> authService.refreshToken(new RefreshTokenRequest(second.getRefreshToken())));
    }

    @Test
    void testGetCurrentAccount() {
        AuthResponse registered = register("me@example.com", "Password123!");

        AccountResponse me = authService.getCurrentAccount(registered.getAccountId());

        assertEquals(registered.getAccountId(), me.getAccountId());
        assertEquals("me@example.com", me.getEmail());
        assertEquals("ACTIVE", me.getStatus());
        assertTrue(me.getRoles().contains("ROLE_USER"));
        assertNotNull(me.getCreatedAt());
    }

    @Test
    void testChangePasswordRevokesTokensAndIssuesNewOnes() {
        AuthResponse registered = register("change.pw@example.com", "OldPassword1!");

        AuthResponse changed = authService.changePassword(registered.getAccountId(),
                new ChangePasswordRequest("OldPassword1!", "NewPassword1!"));

        assertNotNull(changed.getAccessToken());
        assertNotEquals(registered.getRefreshToken(), changed.getRefreshToken());
        assertThrows(UnauthorizedException.class,
                () -> authService.refreshToken(new RefreshTokenRequest(registered.getRefreshToken())));
        assertNotNull(authService.refreshToken(new RefreshTokenRequest(changed.getRefreshToken())).getAccessToken());
        assertThrows(UnauthorizedException.class,
                () -> authService.login(new LoginRequest("change.pw@example.com", "OldPassword1!")));
        assertNotNull(authService.login(new LoginRequest("change.pw@example.com", "NewPassword1!")).getAccessToken());
    }

    @Test
    void testChangePasswordWithWrongCurrentPasswordFails() {
        AuthResponse registered = register("change.wrong@example.com", "OldPassword1!");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.changePassword(
                registered.getAccountId(), new ChangePasswordRequest("NotMyPassword", "NewPassword1!")));
        assertEquals("Current password is incorrect", ex.getMessage());
        assertNotNull(authService.refreshToken(new RefreshTokenRequest(registered.getRefreshToken())).getAccessToken());
    }
}

package com.streamx.auth.service;

import com.streamx.auth.dto.AuthResponse;
import com.streamx.auth.dto.LoginRequest;
import com.streamx.auth.dto.RegisterRequest;
import com.streamx.auth.repository.AccountRepository;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountRepository accountRepository;

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
    void testRegisterDuplicateEmailThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("duplicate@example.com");
        request.setPassword("Password123!");
        authService.register(request);

        RegisterRequest duplicateRequest = new RegisterRequest();
        duplicateRequest.setEmail("duplicate@example.com");
        duplicateRequest.setPassword("AnotherPassword123!");

        assertThrows(BadRequestException.class, () -> authService.register(duplicateRequest));
    }

    @Test
    void testLoginSuccess() {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("user.login@example.com");
        registerRequest.setPassword("SecurePass123!");
        authService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsernameOrEmail("user.login@example.com");
        loginRequest.setPassword("SecurePass123!");

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response.getAccessToken());
        assertEquals("user.login@example.com", response.getEmail());
    }

    @Test
    void testLoginInvalidPasswordThrowsUnauthorized() {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("user.invalid@example.com");
        registerRequest.setPassword("CorrectPassword123!");
        authService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsernameOrEmail("user.invalid@example.com");
        loginRequest.setPassword("WrongPassword123!");

        assertThrows(UnauthorizedException.class, () -> authService.login(loginRequest));
    }
}

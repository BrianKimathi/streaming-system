package com.streamx.auth.service;

import com.streamx.auth.client.ProfileDirectoryClient;
import com.streamx.auth.domain.Account;
import com.streamx.auth.domain.RefreshToken;
import com.streamx.auth.dto.*;
import com.streamx.auth.repository.AccountRepository;
import com.streamx.auth.repository.RefreshTokenRepository;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ForbiddenException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.common.exception.UnauthorizedException;
import com.streamx.common.security.JwtUtils;
import com.streamx.common.security.SecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final ProfileDirectoryClient profileDirectoryClient;

    @Value("${jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    public AuthService(AccountRepository accountRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtils jwtUtils,
                       ProfileDirectoryClient profileDirectoryClient) {
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.profileDirectoryClient = profileDirectoryClient;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (email.isEmpty()) {
            throw new BadRequestException("Email is required");
        }
        if (accountRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new BadRequestException("Email address is already in use");
        }

        String phoneNumber = request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()
                ? null
                : request.getPhoneNumber().trim();
        if (phoneNumber != null && accountRepository.existsByPhoneNumber(phoneNumber)) {
            throw new BadRequestException("Phone number is already in use");
        }

        Account account = new Account();
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        account.setPhoneNumber(phoneNumber);
        account.setStatus(Account.AccountStatus.ACTIVE);

        Account saved = accountRepository.save(account);
        log.info("Registered new account: {}", saved.getId());

        return generateAuthTokens(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getUsernameOrEmail() == null ? "" : request.getUsernameOrEmail().trim();
        Account account = accountRepository.findByEmailIgnoreCase(identifier)
                .or(() -> accountRepository.findByPhoneNumber(identifier))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new UnauthorizedException("Account is " + account.getStatus());
        }

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        return generateAuthTokens(account);
    }

    @Transactional
    public AuthResponse adminLogin(LoginRequest request) {
        Account account = accountRepository.findByEmailIgnoreCase(request.getUsernameOrEmail().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        if (!SecurityConstants.hasAdminRole(account.getRoles())) {
            log.warn("Non-admin account {} attempted admin console login", account.getId());
            throw new ForbiddenException("This account does not have administrator access");
        }
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new UnauthorizedException("Account is " + account.getStatus());
        }

        return generateAuthTokens(account);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken token = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (token.isRevoked() || token.getExpiryDate().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        Account account = accountRepository.findById(token.getAccountId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new UnauthorizedException("Account is " + account.getStatus());
        }

        String accountId = account.getId().toString();
        String accessToken;
        if (request.getProfileId() != null && !request.getProfileId().isBlank()) {
            UUID profileId = requireOwnedProfile(account.getId(), request.getProfileId().trim());
            accessToken = jwtUtils.generateProfileAccessToken(
                    accountId, account.getEmail(), new ArrayList<>(account.getRoles()), profileId.toString());
        } else {
            accessToken = jwtUtils.generateAccessToken(accountId, account.getEmail(), new ArrayList<>(account.getRoles()));
        }

        return buildResponse(account, accessToken, token.getToken());
    }

    /**
     * Revokes the given refresh token if it belongs to the caller; without a token, revokes every session of the account.
     * Tokens that are unknown or owned by another account are left untouched so logout stays idempotent.
     */
    @Transactional
    public void logout(String accountIdStr, String refreshToken) {
        UUID accountId = parseAccountId(accountIdStr);
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshTokenRepository.deleteByAccountId(accountId);
            return;
        }
        refreshTokenRepository.findByToken(refreshToken.trim()).ifPresent(token -> {
            if (token.getAccountId().equals(accountId)) {
                refreshTokenRepository.delete(token);
            } else {
                log.warn("Account {} attempted to revoke a refresh token it does not own", accountId);
            }
        });
    }

    @Transactional(readOnly = true)
    public AccountResponse getCurrentAccount(String accountIdStr) {
        return AccountResponse.from(findAccount(accountIdStr));
    }

    @Transactional
    public AuthResponse changePassword(String accountIdStr, ChangePasswordRequest request) {
        Account account = findAccount(accountIdStr);
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new UnauthorizedException("Account is " + account.getStatus());
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters long");
        }
        if (request.getCurrentPassword() == null
                || !passwordEncoder.matches(request.getCurrentPassword(), account.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        account.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        Account saved = accountRepository.save(account);
        refreshTokenRepository.deleteByAccountId(saved.getId());
        log.info("Password changed for account {}; all refresh tokens revoked", saved.getId());

        return generateAuthTokens(saved);
    }

    private UUID requireOwnedProfile(UUID accountId, String profileIdStr) {
        UUID profileId;
        try {
            profileId = UUID.fromString(profileIdStr);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Profile not found");
        }
        UUID owner = profileDirectoryClient.findOwnerAccountId(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        if (!owner.equals(accountId)) {
            log.warn("Account {} attempted to refresh into profile {} owned by another account", accountId, profileId);
            throw new ResourceNotFoundException("Profile not found");
        }
        return profileId;
    }

    private Account findAccount(String accountIdStr) {
        return accountRepository.findById(parseAccountId(accountIdStr))
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private UUID parseAccountId(String accountIdStr) {
        if (accountIdStr == null || accountIdStr.isBlank()) {
            throw new UnauthorizedException("Authentication required");
        }
        try {
            return UUID.fromString(accountIdStr.trim());
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Authentication required");
        }
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse generateAuthTokens(Account account) {
        String accessToken = jwtUtils.generateAccessToken(
                account.getId().toString(),
                account.getEmail(),
                new ArrayList<>(account.getRoles())
        );

        String refreshTokenStr = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setAccountId(account.getId());
        refreshToken.setToken(refreshTokenStr);
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenExpirationMs));

        refreshTokenRepository.save(refreshToken);

        return buildResponse(account, accessToken, refreshTokenStr);
    }

    private AuthResponse buildResponse(Account account, String accessToken, String refreshToken) {
        AuthResponse response = new AuthResponse();
        response.setAccountId(account.getId().toString());
        response.setEmail(account.getEmail());
        response.setPhoneNumber(account.getPhoneNumber());
        response.setEmailVerified(account.isEmailVerified());
        response.setPhoneVerified(account.isPhoneVerified());
        response.setRoles(new ArrayList<>(account.getRoles()));
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        return response;
    }
}

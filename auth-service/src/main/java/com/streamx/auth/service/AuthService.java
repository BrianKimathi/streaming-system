package com.streamx.auth.service;

import com.streamx.auth.domain.Account;
import com.streamx.auth.domain.RefreshToken;
import com.streamx.auth.dto.*;
import com.streamx.auth.repository.AccountRepository;
import com.streamx.auth.repository.RefreshTokenRepository;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.common.exception.UnauthorizedException;
import com.streamx.common.security.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Value("${jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    public AuthService(AccountRepository accountRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtils jwtUtils) {
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email address is already in use");
        }

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            if (accountRepository.existsByPhoneNumber(request.getPhoneNumber())) {
                throw new BadRequestException("Phone number is already in use");
            }
        }

        Account account = new Account();
        account.setEmail(request.getEmail());
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        account.setPhoneNumber(request.getPhoneNumber());
        account.setStatus(Account.AccountStatus.ACTIVE);

        Account saved = accountRepository.save(account);
        log.info("Registered new account: {}", saved.getId());

        return generateAuthTokens(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Account account = accountRepository.findByEmail(request.getUsernameOrEmail())
                .or(() -> accountRepository.findByPhoneNumber(request.getUsernameOrEmail()))
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
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken token = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (token.isRevoked() || token.getExpiryDate().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        Account account = accountRepository.findById(token.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        String newAccessToken = jwtUtils.generateAccessToken(
                account.getId().toString(),
                account.getEmail(),
                new ArrayList<>(account.getRoles())
        );

        AuthResponse response = new AuthResponse();
        response.setAccountId(account.getId().toString());
        response.setEmail(account.getEmail());
        response.setPhoneNumber(account.getPhoneNumber());
        response.setEmailVerified(account.isEmailVerified());
        response.setPhoneVerified(account.isPhoneVerified());
        response.setRoles(new ArrayList<>(account.getRoles()));
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(token.getToken());
        return response;
    }

    @Transactional
    public void logout(String accountId) {
        refreshTokenRepository.deleteByAccountId(UUID.fromString(accountId));
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

        AuthResponse response = new AuthResponse();
        response.setAccountId(account.getId().toString());
        response.setEmail(account.getEmail());
        response.setPhoneNumber(account.getPhoneNumber());
        response.setEmailVerified(account.isEmailVerified());
        response.setPhoneVerified(account.isPhoneVerified());
        response.setRoles(new ArrayList<>(account.getRoles()));
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshTokenStr);
        return response;
    }
}

package com.relay.auth.service;

import com.relay.auth.config.JwtProperties;
import com.relay.auth.domain.RefreshToken;
import com.relay.auth.domain.User;
import com.relay.auth.dto.AuthResponse;
import com.relay.auth.dto.LoginRequest;
import com.relay.auth.dto.RefreshRequest;
import com.relay.auth.dto.RegisterRequest;
import com.relay.auth.exception.ApiException;
import com.relay.auth.repository.RefreshTokenRepository;
import com.relay.auth.repository.UserRepository;
import com.relay.auth.security.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final JwtProperties properties;
    private final Clock clock;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, TokenService tokenService,
                       JwtProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (users.existsByUsername(request.username())) {
            throw ApiException.conflict("username already taken");
        }
        if (users.existsByEmail(request.email())) {
            throw ApiException.conflict("email already registered");
        }
        User user = new User(UUID.randomUUID(), request.username(), request.email(),
                passwordEncoder.encode(request.password()), clock.instant());
        users.save(user);
        return issueTokens(user, UUID.randomUUID(), null);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findByUsername(request.username())
                .orElseThrow(() -> ApiException.unauthorized("invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("invalid credentials");
        }
        return issueTokens(user, UUID.randomUUID(), null);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHash(tokenService.hash(request.refreshToken()))
                .orElseThrow(() -> ApiException.unauthorized("invalid refresh token"));

        if (token.isRevoked()) {
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            throw ApiException.unauthorized("refresh token reuse detected");
        }
        if (token.isExpired(now)) {
            throw ApiException.unauthorized("refresh token expired");
        }

        token.revoke(now);
        refreshTokens.save(token);

        User user = users.findById(token.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("invalid refresh token"));
        return issueTokens(user, token.getFamilyId(), token.getId());
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokens.findByTokenHash(tokenService.hash(request.refreshToken()))
                .ifPresent(token -> {
                    token.revoke(clock.instant());
                    refreshTokens.save(token);
                });
    }

    private AuthResponse issueTokens(User user, UUID familyId, UUID rotatedFrom) {
        String accessToken = tokenService.issueAccessToken(user.getId(), user.getUsername());
        String refreshToken = tokenService.generateRefreshToken();
        RefreshToken entity = new RefreshToken(
                UUID.randomUUID(), user.getId(), tokenService.hash(refreshToken),
                familyId, clock.instant().plus(properties.refreshTokenTtl()), rotatedFrom);
        refreshTokens.save(entity);
        return AuthResponse.bearer(accessToken, refreshToken, tokenService.accessTtlSeconds(),
                user.getId(), user.getUsername());
    }
}

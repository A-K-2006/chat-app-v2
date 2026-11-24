package com.relay.auth.service;

import com.relay.auth.config.JwtProperties;
import com.relay.auth.domain.RefreshToken;
import com.relay.auth.dto.LoginRequest;
import com.relay.auth.dto.RefreshRequest;
import com.relay.auth.dto.RegisterRequest;
import com.relay.auth.exception.ApiException;
import com.relay.auth.repository.RefreshTokenRepository;
import com.relay.auth.repository.UserRepository;
import com.relay.auth.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    @Mock
    private UserRepository users;
    @Mock
    private RefreshTokenRepository refreshTokens;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties("secret", Duration.ofMinutes(15), Duration.ofDays(14), "relay-auth");
        authService = new AuthService(users, refreshTokens, passwordEncoder, tokenService,
                props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(users.existsByUsername("alice")).thenReturn(true);
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void loginRejectsUnknownUser() {
        when(users.findByUsername("alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "password123")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void refreshReuseRevokesWholeFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshToken revoked = new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "h",
                familyId, NOW.plus(Duration.ofDays(14)), null);
        revoked.revoke(NOW.minusSeconds(60));

        when(tokenService.hash("presented")).thenReturn("h");
        when(refreshTokens.findByTokenHash("h")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("presented")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(refreshTokens).revokeFamily(eq(familyId), any(Instant.class));
    }

    @Test
    void refreshRejectsExpiredToken() {
        RefreshToken expired = new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "h",
                UUID.randomUUID(), NOW.minusSeconds(1), null);

        when(tokenService.hash("presented")).thenReturn("h");
        when(refreshTokens.findByTokenHash("h")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("presented")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}

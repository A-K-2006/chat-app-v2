package com.relay.auth.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.relay.auth.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-0123456789";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private TokenService tokenService;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtProperties props = new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(14), "relay-auth");
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        tokenService = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), props, clock);
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Test
    void issuesVerifiableAccessTokenWithExpectedClaims() {
        UUID userId = UUID.randomUUID();
        String token = tokenService.issueAccessToken(userId, "alice");

        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("username")).isEqualTo("alice");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("relay-auth");
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void hashIsDeterministicAndSha256Hex() {
        String hash = tokenService.hash("some-refresh-token");
        assertThat(hash).isEqualTo(tokenService.hash("some-refresh-token")).hasSize(64);
        assertThat(hash).isNotEqualTo(tokenService.hash("different"));
    }

    @Test
    void refreshTokenIsUrlSafeAndUnique() {
        String a = tokenService.generateRefreshToken();
        String b = tokenService.generateRefreshToken();
        assertThat(a).isNotBlank().doesNotContain("+", "/", "=");
        assertThat(a).isNotEqualTo(b);
    }
}

package com.relay.auth;

import com.relay.auth.dto.AuthResponse;
import com.relay.auth.dto.LoginRequest;
import com.relay.auth.dto.RefreshRequest;
import com.relay.auth.dto.RegisterRequest;
import com.relay.auth.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "relay.jwt.secret=integration-test-secret-integration-test-0123456789"
})
@Testcontainers
class AuthFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate rest;

    @Test
    void fullAuthLifecycleThroughHttp() {
        RegisterRequest register = new RegisterRequest("alice", "alice@example.com", "password123");

        ResponseEntity<AuthResponse> registered = rest.postForEntity("/auth/register", register, AuthResponse.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AuthResponse tokens = registered.getBody();
        assertThat(tokens).isNotNull();
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.tokenType()).isEqualTo("Bearer");

        // duplicate username -> 409
        ResponseEntity<String> dup = rest.postForEntity("/auth/register", register, String.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // /users/me with the access token
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        ResponseEntity<UserResponse> me = rest.exchange("/users/me", HttpMethod.GET,
                new HttpEntity<>(headers), UserResponse.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().username()).isEqualTo("alice");

        // /users/me without a token -> 401
        ResponseEntity<String> noToken = rest.getForEntity("/users/me", String.class);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // login works
        ResponseEntity<AuthResponse> login = rest.postForEntity("/auth/login",
                new LoginRequest("alice", "password123"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        // wrong password -> 401
        ResponseEntity<String> badLogin = rest.postForEntity("/auth/login",
                new LoginRequest("alice", "wrong-password"), String.class);
        assertThat(badLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // rotate refresh token
        ResponseEntity<AuthResponse> refreshed = rest.postForEntity("/auth/refresh",
                new RefreshRequest(tokens.refreshToken()), AuthResponse.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotated = refreshed.getBody().refreshToken();
        assertThat(rotated).isNotEqualTo(tokens.refreshToken());

        // reuse of the old (now revoked) token -> 401 + family revoked
        ResponseEntity<String> reuse = rest.postForEntity("/auth/refresh",
                new RefreshRequest(tokens.refreshToken()), String.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // the rotated token is now also revoked (family-wide)
        ResponseEntity<String> familyRevoked = rest.postForEntity("/auth/refresh",
                new RefreshRequest(rotated), String.class);
        assertThat(familyRevoked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesRefreshToken() {
        ResponseEntity<AuthResponse> registered = rest.postForEntity("/auth/register",
                new RegisterRequest("bob", "bob@example.com", "password123"), AuthResponse.class);
        String refreshToken = registered.getBody().refreshToken();

        ResponseEntity<Void> logout = rest.postForEntity("/auth/logout",
                new RefreshRequest(refreshToken), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> afterLogout = rest.postForEntity("/auth/refresh",
                new RefreshRequest(refreshToken), String.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void internalUserLookup() {
        ResponseEntity<AuthResponse> registered = rest.postForEntity("/auth/register",
                new RegisterRequest("carol", "carol@example.com", "password123"), AuthResponse.class);
        var userId = registered.getBody().userId();

        ResponseEntity<UserResponse> found = rest.getForEntity("/internal/users/" + userId, UserResponse.class);
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().username()).isEqualTo("carol");

        ResponseEntity<String> missing = rest.getForEntity(
                "/internal/users/" + java.util.UUID.randomUUID(), String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

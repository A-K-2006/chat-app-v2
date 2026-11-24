package com.relay.chat.service;

import com.relay.chat.exception.ApiException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class AuthClient {

    private final RestClient authRestClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public AuthClient(RestClient authRestClient, CircuitBreaker authClientCircuitBreaker, Retry authClientRetry) {
        this.authRestClient = authRestClient;
        this.circuitBreaker = authClientCircuitBreaker;
        this.retry = authClientRetry;
    }

    public boolean userExists(UUID userId) {
        try {
            return retry.executeSupplier(() -> circuitBreaker.executeSupplier(() -> authRestClient.get()
                    .uri("/internal/users/{id}", userId)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .is2xxSuccessful()));
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (CallNotPermittedException e) {
            throw ApiException.serviceUnavailable("auth service unavailable");
        } catch (Exception e) {
            throw ApiException.serviceUnavailable("auth service unavailable");
        }
    }
}

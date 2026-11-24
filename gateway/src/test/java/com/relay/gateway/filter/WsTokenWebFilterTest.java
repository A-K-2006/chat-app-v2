package com.relay.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WsTokenWebFilterTest {

    private final WsTokenWebFilter filter = new WsTokenWebFilter();

    private ServerWebExchange run(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        WebFilterChain chain = ex -> {
            captured.set(ex);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return captured.get();
    }

    @Test
    void promotesQueryParamToBearerHeaderForWs() {
        ServerWebExchange result = run(MockServerHttpRequest.get("/ws?access_token=abc123").build());
        assertThat(result.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer abc123");
    }

    @Test
    void leavesNonWsRequestsUntouched() {
        ServerWebExchange result = run(MockServerHttpRequest.get("/chat?access_token=abc123").build());
        assertThat(result.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void doesNotOverrideExistingAuthorizationHeader() {
        ServerWebExchange result = run(MockServerHttpRequest.get("/ws?access_token=abc123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer existing").build());
        assertThat(result.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer existing");
    }
}

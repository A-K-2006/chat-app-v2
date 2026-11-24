package com.relay.gateway.filter;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Browsers cannot set Authorization on a WebSocket upgrade, so the client passes
 * ?access_token=...; promote it to a Bearer header before security validates the handshake.
 */
@Component
@Order(-200)
public class WsTokenWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (request.getPath().value().startsWith("/ws")
                && request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
            String token = request.getQueryParams().getFirst("access_token");
            if (token != null && !token.isBlank()) {
                ServerHttpRequest mutated = request.mutate()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build();
                return chain.filter(exchange.mutate().request(mutated).build());
            }
        }
        return chain.filter(exchange);
    }
}

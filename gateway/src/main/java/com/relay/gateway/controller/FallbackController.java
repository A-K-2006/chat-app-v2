package com.relay.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FallbackController {

    @RequestMapping("/fallback")
    ResponseEntity<Map<String, Object>> fallback() {
        return ResponseEntity.status(503)
                .body(Map.of("status", 503, "message", "service temporarily unavailable"));
    }
}

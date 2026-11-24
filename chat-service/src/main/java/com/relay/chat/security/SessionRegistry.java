package com.relay.chat.security;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {

    private final ConcurrentHashMap<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionUser = new ConcurrentHashMap<>();

    public void register(String userId, String sessionId) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        sessionUser.put(sessionId, userId);
    }

    public String unregister(String sessionId) {
        String userId = sessionUser.remove(sessionId);
        if (userId != null) {
            userSessions.computeIfPresent(userId, (k, sessions) -> {
                sessions.remove(sessionId);
                return sessions.isEmpty() ? null : sessions;
            });
        }
        return userId;
    }

    public boolean hasLocalSession(String userId) {
        Set<String> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public Set<String> localUsers() {
        return Set.copyOf(userSessions.keySet());
    }

    public int activeConnections() {
        return sessionUser.size();
    }
}

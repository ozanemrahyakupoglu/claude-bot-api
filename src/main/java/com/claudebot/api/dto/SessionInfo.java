package com.claudebot.api.dto;

import java.time.Instant;

public class SessionInfo {

    private final String sessionId;
    private final String cwd;
    private final Instant createdAt;
    private Instant lastUsedAt;

    public SessionInfo(String sessionId, String cwd) {
        this.sessionId = sessionId;
        this.cwd = cwd;
        this.createdAt = Instant.now();
        this.lastUsedAt = this.createdAt;
    }

    public void touch() {
        this.lastUsedAt = Instant.now();
    }

    public String getSessionId() { return sessionId; }
    public String getCwd() { return cwd; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
}

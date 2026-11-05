package com.claudebot.api.dto;

import java.util.List;

public record AgentReloadResponse(
        String previousCommit,
        String commit,
        boolean changed,
        int closedSessions,
        List<String> skills,
        long durationMs) {
}

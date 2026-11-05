package com.claudebot.api.dto;

import java.time.Instant;
import java.util.List;

public record RunSummary(
        String runId,
        String sessionId,
        String prompt,
        RunStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String result,
        String error,
        Integer numTurns,
        Double costUsd,
        List<String> skillsUsed) {
}

package com.claudebot.api.dto;

import java.util.List;

public class MessageResponse {

    private String sessionId;
    private String content;
    private String duration;
    private int numTurns;
    private Double costUsd;
    private List<String> skillsUsed;

    public MessageResponse(String sessionId, ClaudeResult result, long durationMs) {
        this.sessionId = sessionId;
        this.content = result.content();
        this.duration = formatDuration(durationMs);
        this.numTurns = result.numTurns();
        this.costUsd = result.costUsd();
        this.skillsUsed = result.skillsUsed();
    }

    private String formatDuration(long ms) {
        long hours = ms / 3_600_000;
        long minutes = (ms % 3_600_000) / 60_000;
        long seconds = (ms % 60_000) / 1_000;
        long millis = ms % 1_000;
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }

    public String getSessionId() { return sessionId; }
    public String getContent() { return content; }
    public String getDuration() { return duration; }
    public int getNumTurns() { return numTurns; }
    public Double getCostUsd() { return costUsd; }
    public List<String> getSkillsUsed() { return skillsUsed; }
}

package com.claudebot.api.dto;

public class MessageResponse {

    private String sessionId;
    private String content;
    private String duration;

    public MessageResponse(String sessionId, String content, long durationMs) {
        this.sessionId = sessionId;
        this.content = content;
        this.duration = formatDuration(durationMs);
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
}

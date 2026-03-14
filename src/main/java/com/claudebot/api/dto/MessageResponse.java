package com.claudebot.api.dto;

public class MessageResponse {

    private String sessionId;
    private String content;
    private long durationMs;

    public MessageResponse(String sessionId, String content, long durationMs) {
        this.sessionId = sessionId;
        this.content = content;
        this.durationMs = durationMs;
    }

    public String getSessionId() { return sessionId; }
    public String getContent() { return content; }
    public long getDurationMs() { return durationMs; }
}

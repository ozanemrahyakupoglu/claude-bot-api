package com.claudebot.api.dto;

import jakarta.validation.constraints.NotBlank;

public class StartRunRequest {

    @NotBlank(message = "content must not be blank")
    private String content;

    /** Optional; without it the prompt runs single-shot. */
    private String sessionId;

    /** Optional; only for single-shot runs, sessions use their own cwd. */
    private String cwd;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getCwd() { return cwd; }
    public void setCwd(String cwd) { this.cwd = cwd; }
}

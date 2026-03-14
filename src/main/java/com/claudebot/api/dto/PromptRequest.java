package com.claudebot.api.dto;

import jakarta.validation.constraints.NotBlank;

public class PromptRequest {

    @NotBlank(message = "content must not be blank")
    private String content;

    private String cwd;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCwd() { return cwd; }
    public void setCwd(String cwd) { this.cwd = cwd; }
}

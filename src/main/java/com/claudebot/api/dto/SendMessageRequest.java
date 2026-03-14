package com.claudebot.api.dto;

import jakarta.validation.constraints.NotBlank;

public class SendMessageRequest {

    @NotBlank(message = "content must not be blank")
    private String content;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}

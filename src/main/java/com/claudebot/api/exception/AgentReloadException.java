package com.claudebot.api.exception;

public class AgentReloadException extends RuntimeException {

    public AgentReloadException(String message) {
        super(message);
    }

    public AgentReloadException(String message, Throwable cause) {
        super(message, cause);
    }
}

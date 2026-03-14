package com.claudebot.api.exception;

public class ClaudeExecutionException extends RuntimeException {

    public ClaudeExecutionException(String message) {
        super(message);
    }

    public ClaudeExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

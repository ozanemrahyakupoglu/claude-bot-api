package com.claudebot.api.exception;

public class RunNotFoundException extends RuntimeException {

    public RunNotFoundException(String runId) {
        super("Run not found: " + runId);
    }
}

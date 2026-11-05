package com.claudebot.api.exception;

public class SessionBusyException extends RuntimeException {

    public SessionBusyException(String sessionId) {
        super("Session is busy with another message: " + sessionId);
    }
}

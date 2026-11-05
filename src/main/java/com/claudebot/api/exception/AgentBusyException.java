package com.claudebot.api.exception;

public class AgentBusyException extends RuntimeException {

    public AgentBusyException(int activeRuns) {
        super("Agent is busy with " + activeRuns + " running message(s); retry the reload when they finish");
    }
}

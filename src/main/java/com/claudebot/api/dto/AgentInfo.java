package com.claudebot.api.dto;

public record AgentInfo(String name, String commit, int activeRuns, boolean authEnabled) {
}

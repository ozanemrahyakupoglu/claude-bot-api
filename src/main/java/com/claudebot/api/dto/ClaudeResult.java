package com.claudebot.api.dto;

import java.util.List;

/**
 * Outcome of a single Claude CLI run, parsed from its stream-json output.
 */
public record ClaudeResult(String content, int numTurns, Double costUsd, List<String> skillsUsed) {
}

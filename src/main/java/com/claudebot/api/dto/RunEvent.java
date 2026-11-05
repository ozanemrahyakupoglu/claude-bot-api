package com.claudebot.api.dto;

import java.time.Instant;

/**
 * One item of the live event stream.
 * kind: "cli" (a stream-json event from Claude, data is the JSON), "log" (a non-JSON output line, data is the text),
 * "run_started" / "run_finished" (data is the run summary; seq is 0 and these are not stored with the run).
 */
public record RunEvent(String runId, String sessionId, long seq, Instant time, String kind, Object data) {
}

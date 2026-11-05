package com.claudebot.api.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Receives what a Claude CLI execution produces while it runs.
 */
public interface RunListener {

    RunListener NONE = new RunListener() {
    };

    default void onProcessStarted(Process process) {
    }

    /**
     * @param event the parsed stream-json event, or null if the line is not JSON
     */
    default void onLine(String line, JsonNode event) {
    }
}

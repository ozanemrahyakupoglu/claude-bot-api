package com.claudebot.api.service;

import com.claudebot.api.config.ClaudeProperties;
import com.claudebot.api.exception.ClaudeExecutionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ClaudeCliService {

    private final ClaudeProperties properties;
    private final ObjectMapper objectMapper;

    public ClaudeCliService(ClaudeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs a prompt without a session (single-shot).
     */
    public String runPrompt(String content, String cwd) {
        return execute(content, null, cwd);
    }

    /**
     * Runs a prompt within an existing session.
     */
    public String runWithSession(String content, String sessionId, String cwd) {
        return execute(content, sessionId, cwd);
    }

    private String execute(String content, String sessionId, String cwd) {
        List<String> command = buildCommand(content, sessionId);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        if (cwd != null && !cwd.isBlank()) {
            pb.directory(new File(cwd));
        }

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new ClaudeExecutionException("Failed to start Claude CLI process: " + e.getMessage(), e);
        }

        try {
            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());

            boolean finished = process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ClaudeExecutionException("Claude CLI timed out after " + properties.getTimeoutSeconds() + "s");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new ClaudeExecutionException(
                        "Claude CLI exited with code " + exitCode + ": " + stderr.trim()
                );
            }

            return parseOutput(stdout);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClaudeExecutionException("Claude CLI execution was interrupted", e);
        } catch (IOException e) {
            throw new ClaudeExecutionException("Failed to read Claude CLI output: " + e.getMessage(), e);
        }
    }

    private List<String> buildCommand(String content, String sessionId) {
        List<String> cmd = new ArrayList<>();
        cmd.add(properties.getPath());
        cmd.add("-p");
        cmd.add(content);
        cmd.add("--output-format");
        cmd.add("json");

        if (sessionId != null && !sessionId.isBlank()) {
            cmd.add("--session-id");
            cmd.add(sessionId);
        }

        return cmd;
    }

    private String parseOutput(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            // Claude JSON output: { "result": "...", "session_id": "...", ... }
            if (root.has("result")) {
                return root.get("result").asText();
            }
            // fallback: return raw text
            return raw.trim();
        } catch (Exception e) {
            // not JSON — return as-is
            return raw.trim();
        }
    }
}

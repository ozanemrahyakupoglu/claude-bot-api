package com.claudebot.api.service;

import com.claudebot.api.config.ClaudeProperties;
import com.claudebot.api.exception.ClaudeExecutionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ClaudeCliService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCliService.class);

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
        return execute(content, null, cwd, false);
    }

    /**
     * Runs a prompt within an existing session.
     * @param resume if true, resumes an existing session with --resume; otherwise starts a new one with --session-id
     */
    public String runWithSession(String content, String sessionId, String cwd, boolean resume) {
        return execute(content, sessionId, cwd, resume);
    }

    private String execute(String content, String sessionId, String cwd, boolean resume) {
        List<String> command = buildCommand(content, sessionId, resume);
        log.info("Executing command: {}", command);
        log.info("Working directory: {}", cwd != null ? cwd : "default");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.environment().remove("CLAUDECODE");

        if (cwd != null && !cwd.isBlank()) {
            pb.directory(new File(cwd));
        }

        Process process;
        try {
            process = pb.start();
            process.getOutputStream().close(); // stdin kapalı — claude input beklemesin
            log.info("Process started with PID: {}", process.pid());
        } catch (IOException e) {
            log.error("Failed to start Claude CLI process", e);
            throw new ClaudeExecutionException("Failed to start Claude CLI process: " + e.getMessage(), e);
        }

        try {
            String output = new String(process.getInputStream().readAllBytes());

            boolean finished = process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Claude CLI timed out after {}s", properties.getTimeoutSeconds());
                throw new ClaudeExecutionException("Claude CLI timed out after " + properties.getTimeoutSeconds() + "s");
            }

            int exitCode = process.exitValue();
            log.info("Process exited with code: {}", exitCode);

            if (exitCode != 0) {
                log.error("Claude CLI failed, output: {}", output.trim());
                throw new ClaudeExecutionException(
                        "Claude CLI exited with code " + exitCode + ": " + output.trim()
                );
            }

            log.debug("output: {}", output);
            return parseOutput(output);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Claude CLI execution interrupted", e);
            throw new ClaudeExecutionException("Claude CLI execution was interrupted", e);
        } catch (IOException e) {
            log.error("Failed to read Claude CLI output", e);
            throw new ClaudeExecutionException("Failed to read Claude CLI output: " + e.getMessage(), e);
        }
    }

    private List<String> buildCommand(String content, String sessionId, boolean resume) {
        List<String> cmd = new ArrayList<>();
        cmd.add(properties.getPath());
        cmd.add("-p");
        cmd.add(content);
        cmd.add("--output-format");
        cmd.add("json");
        cmd.add("--dangerously-skip-permissions");

        if (sessionId != null && !sessionId.isBlank()) {
            if (resume) {
                cmd.add("--resume");
            } else {
                cmd.add("--session-id");
            }
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

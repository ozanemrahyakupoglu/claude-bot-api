package com.claudebot.api.service;

import com.claudebot.api.config.ClaudeProperties;
import com.claudebot.api.dto.ClaudeResult;
import com.claudebot.api.dto.SessionInfo;
import com.claudebot.api.exception.ClaudeExecutionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class ClaudeCliService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCliService.class);

    private static final int ERROR_TAIL_LINES = 20;
    private static final long READER_DRAIN_SECONDS = 10;

    private final ClaudeProperties properties;
    private final ObjectMapper objectMapper;
    private final AgentRunGuard runGuard;

    public ClaudeCliService(ClaudeProperties properties, ObjectMapper objectMapper, AgentRunGuard runGuard) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.runGuard = runGuard;
    }

    /**
     * Runs a prompt. With a session it starts it with --session-id the first time and resumes it with --resume
     * afterwards; the session is marked started as soon as the CLI reports it, so a failed first run is still resumed.
     */
    public ClaudeResult execute(String content, SessionInfo session, String cwd, RunListener listener) {
        // Reload sürüyorsa bitmesini bekle; çalışma boyunca agent dosyaları değişmesin
        runGuard.acquireRun();
        try {
            return run(content, session, cwd, listener);
        } finally {
            runGuard.releaseRun();
        }
    }

    private ClaudeResult run(String content, SessionInfo session, String cwd, RunListener listener) {
        List<String> command = buildCommand(content, session);
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
            listener.onProcessStarted(process);
        } catch (IOException e) {
            log.error("Failed to start Claude CLI process", e);
            throw new ClaudeExecutionException("Failed to start Claude CLI process: " + e.getMessage(), e);
        }

        // Çıktı ayrı thread'de okunur; böylece timeout, süreç çıktı üretmeye devam etse de işler
        StreamState state = new StreamState(session, listener);
        Thread reader = Thread.ofVirtual().name("claude-reader-" + process.pid())
                .start(() -> readOutput(process, state));

        try {
            boolean finished = process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                destroyTree(process);
                reader.join(TimeUnit.SECONDS.toMillis(READER_DRAIN_SECONDS));
                log.error("Claude CLI timed out after {}s", properties.getTimeoutSeconds());
                throw new ClaudeExecutionException("Claude CLI timed out after " + properties.getTimeoutSeconds() + "s");
            }

            // Alt süreçler (MCP server'lar) pipe'ı açık tutarsa sonsuza kadar beklemeyelim
            reader.join(TimeUnit.SECONDS.toMillis(READER_DRAIN_SECONDS));

            int exitCode = process.exitValue();
            log.info("Process exited with code: {}", exitCode);

            JsonNode result = state.result;
            if (result == null) {
                throw new ClaudeExecutionException(
                        "Claude CLI exited with code " + exitCode + " without a result: " + state.tail());
            }
            if (result.path("is_error").asBoolean(false) || exitCode != 0) {
                String subtype = result.path("subtype").asText("unknown");
                String message = result.path("result").asText(state.tail());
                log.error("Claude CLI returned an error: exitCode={}, subtype={}, message={}", exitCode, subtype, message);
                throw new ClaudeExecutionException("Claude CLI failed (" + subtype + "): " + message);
            }

            JsonNode cost = result.path("total_cost_usd");
            return new ClaudeResult(
                    result.path("result").asText(""),
                    result.path("num_turns").asInt(0),
                    cost.isNumber() ? cost.asDouble() : null,
                    List.copyOf(state.skillsUsed));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroyTree(process);
            log.error("Claude CLI execution interrupted", e);
            throw new ClaudeExecutionException("Claude CLI execution was interrupted", e);
        }
    }

    private void readOutput(Process process, StreamState state) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[claude] {}", line);
                state.accept(line);
            }
        } catch (IOException e) {
            log.warn("Stopped reading Claude CLI output: {}", e.getMessage());
        }
    }

    private void destroyTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private List<String> buildCommand(String content, SessionInfo session) {
        List<String> cmd = new ArrayList<>();
        cmd.add(properties.getPath());
        cmd.add("-p");
        cmd.add(content);
        cmd.add("--output-format");
        cmd.add("stream-json");
        cmd.add("--verbose");
        cmd.add("--dangerously-skip-permissions");

        if (properties.getModel() != null && !properties.getModel().isBlank()) {
            cmd.add("--model");
            cmd.add(properties.getModel());
        }
        if (properties.getMaxTurns() != null) {
            cmd.add("--max-turns");
            cmd.add(properties.getMaxTurns().toString());
        }

        if (session != null) {
            cmd.add(session.isStarted() ? "--resume" : "--session-id");
            cmd.add(session.getSessionId());
        }

        return cmd;
    }

    /**
     * Collects what the API needs from the stream-json events: the result event, the skills Claude invoked,
     * and the last raw lines for error messages.
     */
    private class StreamState {

        private final SessionInfo session;
        private final RunListener listener;
        private final Set<String> skillsUsed = new LinkedHashSet<>();
        private final Deque<String> lastLines = new ArrayDeque<>();
        private volatile JsonNode result;

        StreamState(SessionInfo session, RunListener listener) {
            this.session = session;
            this.listener = listener;
        }

        void accept(String line) {
            synchronized (lastLines) {
                lastLines.addLast(line);
                if (lastLines.size() > ERROR_TAIL_LINES) {
                    lastLines.removeFirst();
                }
            }
            if (line.isBlank()) {
                return;
            }

            JsonNode node;
            try {
                node = objectMapper.readTree(line);
            } catch (IOException e) {
                node = null;
            }
            if (node != null && !node.isObject()) {
                node = null; // JSON olmayan satır (ör. CLI uyarısı)
            }
            listener.onLine(line, node);
            if (node == null) {
                return;
            }

            switch (node.path("type").asText()) {
                case "system" -> {
                    if (session != null && "init".equals(node.path("subtype").asText())) {
                        session.markStarted();
                    }
                }
                case "assistant" -> collectSkills(node);
                case "result" -> result = node;
                default -> { }
            }
        }

        private void collectSkills(JsonNode event) {
            for (JsonNode block : event.path("message").path("content")) {
                if ("tool_use".equals(block.path("type").asText()) && "Skill".equals(block.path("name").asText())) {
                    JsonNode input = block.path("input");
                    String skill = input.path("skill").asText(input.path("command").asText(""));
                    if (!skill.isBlank()) {
                        synchronized (skillsUsed) {
                            skillsUsed.add(skill);
                        }
                        log.info("Skill invoked: {}", skill);
                    }
                }
            }
        }

        String tail() {
            synchronized (lastLines) {
                return String.join("\n", lastLines).trim();
            }
        }
    }
}

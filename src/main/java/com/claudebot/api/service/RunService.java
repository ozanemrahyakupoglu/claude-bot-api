package com.claudebot.api.service;

import com.claudebot.api.config.ClaudeProperties;
import com.claudebot.api.config.RunProperties;
import com.claudebot.api.dto.ClaudeResult;
import com.claudebot.api.dto.Run;
import com.claudebot.api.dto.SessionInfo;
import com.claudebot.api.exception.BadRequestException;
import com.claudebot.api.exception.ClaudeExecutionException;
import com.claudebot.api.exception.RunNotFoundException;
import com.claudebot.api.exception.SessionBusyException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs prompts as tracked runs: their events are kept for the UI and pushed to the live event stream.
 * Runs can be awaited (the original synchronous endpoints) or started in the background (the UI).
 */
@Service
public class RunService {

    private static final Logger log = LoggerFactory.getLogger(RunService.class);

    private final ClaudeCliService claudeCliService;
    private final RunStore runStore;
    private final EventStreamService eventStream;
    private final ClaudeProperties claudeProperties;
    private final RunProperties runProperties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public RunService(ClaudeCliService claudeCliService, RunStore runStore, EventStreamService eventStream,
                      ClaudeProperties claudeProperties, RunProperties runProperties) {
        this.claudeCliService = claudeCliService;
        this.runStore = runStore;
        this.eventStream = eventStream;
        this.claudeProperties = claudeProperties;
        this.runProperties = runProperties;
    }

    /**
     * Runs the prompt on the calling thread and returns when it finishes.
     * Throws the CLI error, as the synchronous endpoints always did.
     */
    public ClaudeResult runAndWait(String content, SessionInfo session, String cwd) {
        Run run = begin(content, session, cwd);
        execute(run, content, session, effectiveCwd(session, cwd), true);
        return run.getResult();
    }

    /**
     * Starts the prompt in the background and returns the run right away.
     */
    public Run start(String content, SessionInfo session, String cwd) {
        Run run = begin(content, session, cwd);
        String effectiveCwd = effectiveCwd(session, cwd);
        executor.execute(() -> execute(run, content, session, effectiveCwd, false));
        return run;
    }

    public Run get(String runId) {
        Run run = runStore.get(runId);
        if (run == null) {
            throw new RunNotFoundException(runId);
        }
        return run;
    }

    public boolean cancel(String runId) {
        Run run = get(runId);
        boolean cancelled = run.cancel();
        log.info("Cancel requested: runId={}, wasRunning={}", runId, cancelled);
        return cancelled;
    }

    /**
     * Rejects a cwd that does not exist, before anything is started.
     */
    public void validateCwd(String cwd) {
        if (cwd != null && !cwd.isBlank() && !Files.isDirectory(Path.of(cwd))) {
            throw new BadRequestException("cwd is not a directory: " + cwd);
        }
    }

    private Run begin(String content, SessionInfo session, String cwd) {
        if (session == null) {
            validateCwd(cwd);
        } else if (!session.tryAcquire()) {
            log.warn("Session busy: id={}", session.getSessionId());
            throw new SessionBusyException(session.getSessionId());
        }

        Run run = new Run(UUID.randomUUID().toString(), session != null ? session.getSessionId() : null, content);
        if (session != null) {
            session.recordMessage(content);
            session.touch();
        }
        runStore.add(run);
        log.info("Run started: runId={}, sessionId={}", run.getRunId(), run.getSessionId());
        eventStream.publish(run.lifecycleEvent("run_started"));
        return run;
    }

    private void execute(Run run, String content, SessionInfo session, String cwd, boolean rethrow) {
        RunListener listener = new RunListener() {
            @Override
            public void onProcessStarted(Process process) {
                run.attachProcess(process);
            }

            @Override
            public void onLine(String line, JsonNode event) {
                int maxEvents = runProperties.getMaxEventsPerRun();
                int maxText = runProperties.getMaxEventTextLength();
                eventStream.publish(event != null
                        ? run.addEvent("cli", truncate(event.deepCopy(), maxText), maxEvents)
                        : run.addEvent("log", truncate(line, maxText), maxEvents));
            }
        };

        try {
            ClaudeResult result = claudeCliService.execute(content, session, cwd, listener);
            run.succeed(result);
        } catch (RuntimeException e) {
            run.fail(run.isCancelRequested() ? "Cancelled" : e.getMessage());
            log.warn("Run failed: runId={}, status={}, error={}", run.getRunId(), run.getStatus(), e.getMessage());
            if (rethrow) {
                throw run.isCancelRequested() ? new ClaudeExecutionException("Run was cancelled") : e;
            }
        } finally {
            if (session != null) {
                session.touch();
                session.release();
            }
            log.info("Run finished: runId={}, status={}", run.getRunId(), run.getStatus());
            eventStream.publish(run.lifecycleEvent("run_finished"));
        }
    }

    private String effectiveCwd(SessionInfo session, String cwd) {
        if (session != null) {
            return session.getCwd();
        }
        return cwd != null && !cwd.isBlank() ? cwd : claudeProperties.getDefaultCwd();
    }

    // Tarayıcı snapshot'ları gibi dev tool çıktıları UI'ı ve belleği doldurmasın
    private static JsonNode truncate(JsonNode node, int max) {
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                field.setValue(truncate(field.getValue(), max));
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                array.set(i, truncate(array.get(i), max));
            }
        } else if (node instanceof TextNode text && text.textValue().length() > max) {
            return TextNode.valueOf(truncate(text.textValue(), max));
        }
        return node;
    }

    private static String truncate(String text, int max) {
        return text.length() > max
                ? text.substring(0, max) + "… [" + (text.length() - max) + " more chars]"
                : text;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}

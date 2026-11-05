package com.claudebot.api.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A single Claude CLI execution and what it produced, kept in memory for the UI.
 */
public class Run {

    private final String runId;
    private final String sessionId;
    private final String prompt;
    private final Instant startedAt = Instant.now();
    private final Deque<RunEvent> events = new ArrayDeque<>();
    private long nextSeq = 1;
    private int droppedEvents;

    private volatile RunStatus status = RunStatus.RUNNING;
    private volatile Instant finishedAt;
    private volatile ClaudeResult result;
    private volatile String error;
    private volatile Process process;
    private volatile boolean cancelRequested;

    public Run(String runId, String sessionId, String prompt) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.prompt = prompt;
    }

    public synchronized RunEvent addEvent(String kind, Object data, int maxEvents) {
        RunEvent event = new RunEvent(runId, sessionId, nextSeq++, Instant.now(), kind, data);
        events.addLast(event);
        while (events.size() > maxEvents) {
            events.removeFirst();
            droppedEvents++;
        }
        return event;
    }

    public RunEvent lifecycleEvent(String kind) {
        return new RunEvent(runId, sessionId, 0, Instant.now(), kind, toSummary());
    }

    public void succeed(ClaudeResult result) {
        this.result = result;
        finish(RunStatus.SUCCEEDED);
    }

    public void fail(String error) {
        this.error = error;
        finish(cancelRequested ? RunStatus.CANCELLED : RunStatus.FAILED);
    }

    private void finish(RunStatus status) {
        this.finishedAt = Instant.now();
        this.status = status;
        this.process = null;
    }

    /**
     * Kills the CLI process and everything it started; the run then finishes as CANCELLED.
     */
    public boolean cancel() {
        Process p = process;
        if (status != RunStatus.RUNNING) {
            return false;
        }
        cancelRequested = true;
        if (p != null) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
        }
        return true;
    }

    public void attachProcess(Process process) {
        this.process = process;
        if (cancelRequested) {
            cancel();
        }
    }

    public boolean isCancelRequested() { return cancelRequested; }
    public String getRunId() { return runId; }
    public String getSessionId() { return sessionId; }
    public RunStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public ClaudeResult getResult() { return result; }

    public RunSummary toSummary() {
        ClaudeResult r = result;
        Instant end = finishedAt;
        return new RunSummary(runId, sessionId, prompt, status, startedAt, end,
                Duration.between(startedAt, end != null ? end : Instant.now()).toMillis(),
                r != null ? r.content() : null,
                error,
                r != null ? r.numTurns() : null,
                r != null ? r.costUsd() : null,
                r != null ? r.skillsUsed() : List.of());
    }

    public synchronized RunDetail toDetail() {
        return new RunDetail(toSummary(), List.copyOf(events), droppedEvents);
    }
}

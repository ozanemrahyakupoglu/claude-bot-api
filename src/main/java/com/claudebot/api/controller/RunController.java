package com.claudebot.api.controller;

import com.claudebot.api.dto.Run;
import com.claudebot.api.dto.RunDetail;
import com.claudebot.api.dto.RunSummary;
import com.claudebot.api.dto.SessionInfo;
import com.claudebot.api.dto.StartRunRequest;
import com.claudebot.api.exception.SessionNotFoundException;
import com.claudebot.api.service.RunService;
import com.claudebot.api.service.RunStore;
import com.claudebot.api.service.SessionStore;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Background runs for the UI: start a prompt and follow it over GET /api/events instead of waiting on the request.
 */
@RestController
@RequestMapping("/api/runs")
public class RunController {

    private static final Logger log = LoggerFactory.getLogger(RunController.class);

    private final RunService runService;
    private final RunStore runStore;
    private final SessionStore sessionStore;

    public RunController(RunService runService, RunStore runStore, SessionStore sessionStore) {
        this.runService = runService;
        this.runStore = runStore;
        this.sessionStore = sessionStore;
    }

    @PostMapping
    public ResponseEntity<RunSummary> startRun(@Valid @RequestBody StartRunRequest request) {
        SessionInfo session = null;
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            session = sessionStore.get(request.getSessionId());
            if (session == null) {
                throw new SessionNotFoundException(request.getSessionId());
            }
        }
        Run run = runService.start(request.getContent(), session, request.getCwd());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(run.toSummary());
    }

    /**
     * Newest first. sessionId=none lists single-shot runs.
     */
    @GetMapping
    public List<RunSummary> listRuns(@RequestParam(name = "sessionId", required = false) String sessionId,
                                     @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return runStore.list(sessionId, Math.max(1, Math.min(limit, 500))).stream().map(Run::toSummary).toList();
    }

    @GetMapping("/{runId}")
    public RunDetail getRun(@PathVariable("runId") String runId) {
        return runService.get(runId).toDetail();
    }

    @PostMapping("/{runId}/cancel")
    public Map<String, Object> cancelRun(@PathVariable("runId") String runId) {
        log.info("Cancelling run: id={}", runId);
        return Map.of("runId", runId, "cancelled", runService.cancel(runId));
    }
}

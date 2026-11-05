package com.claudebot.api.controller;

import com.claudebot.api.config.ClaudeProperties;
import com.claudebot.api.dto.*;
import com.claudebot.api.exception.SessionBusyException;
import com.claudebot.api.exception.SessionNotFoundException;
import com.claudebot.api.service.RunService;
import com.claudebot.api.service.RunStore;
import com.claudebot.api.service.SessionStore;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final SessionStore sessionStore;
    private final RunService runService;
    private final RunStore runStore;
    private final ClaudeProperties claudeProperties;

    public SessionController(SessionStore sessionStore, RunService runService, RunStore runStore,
                             ClaudeProperties claudeProperties) {
        this.sessionStore = sessionStore;
        this.runService = runService;
        this.runStore = runStore;
        this.claudeProperties = claudeProperties;
    }

    @PostMapping
    public ResponseEntity<SessionInfo> createSession(@RequestBody(required = false) CreateSessionRequest request) {
        String sessionId = UUID.randomUUID().toString();
        String cwd = (request != null && request.getCwd() != null) ? request.getCwd() : claudeProperties.getDefaultCwd();
        runService.validateCwd(cwd);
        log.info("Creating session: id={}, cwd={}", sessionId, cwd);
        SessionInfo session = sessionStore.put(new SessionInfo(sessionId, cwd));
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @GetMapping
    public Collection<SessionInfo> listSessions() {
        Collection<SessionInfo> sessions = sessionStore.getAll();
        log.info("Listing sessions: count={}", sessions.size());
        return sessions;
    }

    @GetMapping("/current")
    public ResponseEntity<SessionInfo> getCurrentSession() {
        SessionInfo session = sessionStore.getCurrent();
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        log.info("Current session: id={}", session.getSessionId());
        return ResponseEntity.ok(session);
    }

    @GetMapping("/{sessionId}")
    public SessionInfo getSession(@PathVariable("sessionId") String sessionId) {
        return requireSession(sessionId);
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody SendMessageRequest request) {

        log.info("Sending message to session: id={}", sessionId);
        SessionInfo session = requireSession(sessionId);

        long start = System.currentTimeMillis();
        ClaudeResult result = runService.runAndWait(request.getContent(), session, null);
        long duration = System.currentTimeMillis() - start;
        log.info("Message completed: sessionId={}, durationMs={}, skillsUsed={}", sessionId, duration, result.skillsUsed());

        return ResponseEntity.ok(new MessageResponse(sessionId, result, duration));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable("sessionId") String sessionId) {
        log.info("Deleting session: id={}", sessionId);
        SessionInfo session = requireSession(sessionId);
        if (session.isBusy()) {
            log.warn("Session busy, not deleting: id={}", sessionId);
            throw new SessionBusyException(sessionId);
        }
        sessionStore.remove(sessionId);
        runStore.removeBySession(sessionId);
        return ResponseEntity.noContent().build();
    }

    private SessionInfo requireSession(String sessionId) {
        SessionInfo session = sessionStore.get(sessionId);
        if (session == null) {
            log.warn("Session not found: id={}", sessionId);
            throw new SessionNotFoundException(sessionId);
        }
        return session;
    }
}

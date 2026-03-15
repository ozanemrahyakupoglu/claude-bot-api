package com.claudebot.api.controller;

import com.claudebot.api.dto.*;
import com.claudebot.api.exception.SessionNotFoundException;
import com.claudebot.api.service.ClaudeCliService;
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
    private final ClaudeCliService claudeCliService;

    public SessionController(SessionStore sessionStore, ClaudeCliService claudeCliService) {
        this.sessionStore = sessionStore;
        this.claudeCliService = claudeCliService;
    }

    @PostMapping
    public ResponseEntity<SessionInfo> createSession(@RequestBody(required = false) CreateSessionRequest request) {
        String sessionId = UUID.randomUUID().toString();
        String cwd = (request != null) ? request.getCwd() : null;
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

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody SendMessageRequest request) {

        log.info("Sending message to session: id={}", sessionId);
        SessionInfo session = sessionStore.get(sessionId);
        if (session == null) {
            log.warn("Session not found: id={}", sessionId);
            throw new SessionNotFoundException(sessionId);
        }

        long start = System.currentTimeMillis();
        String result = claudeCliService.runWithSession(request.getContent(), sessionId, session.getCwd(), session.isStarted());
        long duration = System.currentTimeMillis() - start;

        session.markStarted();
        session.touch();
        log.info("Message completed: sessionId={}, durationMs={}", sessionId, duration);

        return ResponseEntity.ok(new MessageResponse(sessionId, result, duration));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable("sessionId") String sessionId) {
        log.info("Deleting session: id={}", sessionId);
        if (!sessionStore.exists(sessionId)) {
            log.warn("Session not found for deletion: id={}", sessionId);
            throw new SessionNotFoundException(sessionId);
        }
        sessionStore.remove(sessionId);
        return ResponseEntity.noContent().build();
    }
}

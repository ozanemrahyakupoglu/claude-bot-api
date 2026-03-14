package com.claudebot.api.controller;

import com.claudebot.api.dto.*;
import com.claudebot.api.exception.SessionNotFoundException;
import com.claudebot.api.service.ClaudeCliService;
import com.claudebot.api.service.SessionStore;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

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
        SessionInfo session = sessionStore.put(new SessionInfo(sessionId, cwd));
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @GetMapping
    public Collection<SessionInfo> listSessions() {
        return sessionStore.getAll();
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody SendMessageRequest request) {

        SessionInfo session = sessionStore.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException(sessionId);
        }

        long start = System.currentTimeMillis();
        String result = claudeCliService.runWithSession(request.getContent(), sessionId, session.getCwd());
        long duration = System.currentTimeMillis() - start;

        session.touch();

        return ResponseEntity.ok(new MessageResponse(sessionId, result, duration));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        if (!sessionStore.exists(sessionId)) {
            throw new SessionNotFoundException(sessionId);
        }
        sessionStore.remove(sessionId);
        return ResponseEntity.noContent().build();
    }
}

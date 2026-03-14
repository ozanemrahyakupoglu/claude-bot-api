package com.claudebot.api.controller;

import com.claudebot.api.dto.MessageResponse;
import com.claudebot.api.dto.PromptRequest;
import com.claudebot.api.service.ClaudeCliService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prompts")
public class PromptController {

    private final ClaudeCliService claudeCliService;

    public PromptController(ClaudeCliService claudeCliService) {
        this.claudeCliService = claudeCliService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> runPrompt(@Valid @RequestBody PromptRequest request) {
        long start = System.currentTimeMillis();
        String result = claudeCliService.runPrompt(request.getContent(), request.getCwd());
        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.ok(new MessageResponse(null, result, duration));
    }
}

package com.claudebot.api.controller;

import com.claudebot.api.dto.MessageResponse;
import com.claudebot.api.dto.PromptRequest;
import com.claudebot.api.service.ClaudeCliService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prompts")
public class PromptController {

    private static final Logger log = LoggerFactory.getLogger(PromptController.class);

    private final ClaudeCliService claudeCliService;

    public PromptController(ClaudeCliService claudeCliService) {
        this.claudeCliService = claudeCliService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> runPrompt(@Valid @RequestBody PromptRequest request) {
        log.info("Running single-shot prompt, cwd={}", request.getCwd());
        long start = System.currentTimeMillis();
        String result = claudeCliService.runPrompt(request.getContent(), request.getCwd());
        long duration = System.currentTimeMillis() - start;

        log.info("Prompt completed, durationMs={}", duration);
        return ResponseEntity.ok(new MessageResponse(null, result, duration));
    }
}

package com.claudebot.api.controller;

import com.claudebot.api.dto.ClaudeResult;
import com.claudebot.api.dto.MessageResponse;
import com.claudebot.api.dto.PromptRequest;
import com.claudebot.api.service.RunService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prompts")
public class PromptController {

    private static final Logger log = LoggerFactory.getLogger(PromptController.class);

    private final RunService runService;

    public PromptController(RunService runService) {
        this.runService = runService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> runPrompt(@Valid @RequestBody PromptRequest request) {
        log.info("Running single-shot prompt, cwd={}", request.getCwd());
        long start = System.currentTimeMillis();
        ClaudeResult result = runService.runAndWait(request.getContent(), null, request.getCwd());
        long duration = System.currentTimeMillis() - start;

        log.info("Prompt completed, durationMs={}, skillsUsed={}", duration, result.skillsUsed());
        return ResponseEntity.ok(new MessageResponse(null, result, duration));
    }
}

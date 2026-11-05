package com.claudebot.api.controller;

import com.claudebot.api.dto.AgentInfo;
import com.claudebot.api.dto.AgentReloadResponse;
import com.claudebot.api.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    public AgentInfo getAgent() {
        return agentService.getInfo();
    }

    @PostMapping("/reload")
    public AgentReloadResponse reload() {
        log.info("Reloading agent from git");
        return agentService.reload();
    }
}

package com.claudebot.api.controller;

import com.claudebot.api.dto.SkillInfo;
import com.claudebot.api.service.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private static final Logger log = LoggerFactory.getLogger(SkillController.class);

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public List<SkillInfo> listSkills() {
        List<SkillInfo> skills = skillService.listSkills();
        log.info("Listing skills: count={}", skills.size());
        return skills;
    }
}

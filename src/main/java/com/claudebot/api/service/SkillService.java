package com.claudebot.api.service;

import com.claudebot.api.config.SkillProperties;
import com.claudebot.api.dto.SkillInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Lists the skills Claude Code will discover, read from each skill's SKILL.md frontmatter.
 */
@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private final SkillProperties properties;

    public SkillService(SkillProperties properties) {
        this.properties = properties;
    }

    public List<SkillInfo> listSkills() {
        List<SkillInfo> skills = new ArrayList<>();
        for (String dir : properties.getDirs()) {
            Path root = Path.of(dir);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> children = Files.list(root)) {
                children.map(child -> child.resolve("SKILL.md"))
                        .filter(Files::isRegularFile)
                        .map(this::readSkill)
                        .forEach(skills::add);
            } catch (IOException e) {
                log.warn("Failed to list skills in {}: {}", root, e.getMessage());
            }
        }
        skills.sort(Comparator.comparing(SkillInfo::name));
        return skills;
    }

    private SkillInfo readSkill(Path skillMd) {
        String dirName = skillMd.getParent().getFileName().toString();
        Map<?, ?> frontmatter = Map.of();
        try {
            frontmatter = parseFrontmatter(Files.readString(skillMd, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to read skill frontmatter {}: {}", skillMd, e.getMessage());
        }
        Object name = frontmatter.get("name");
        Object description = frontmatter.get("description");
        return new SkillInfo(
                name != null ? name.toString() : dirName,
                description != null ? description.toString() : null,
                skillMd.getParent().toString());
    }

    private Map<?, ?> parseFrontmatter(String content) {
        String text = content.replace("\r\n", "\n");
        if (!text.startsWith("---\n")) {
            return Map.of();
        }
        int end = text.indexOf("\n---", 4);
        if (end < 0) {
            return Map.of();
        }
        Object parsed = new Yaml().load(text.substring(4, end));
        return parsed instanceof Map<?, ?> map ? map : Map.of();
    }
}

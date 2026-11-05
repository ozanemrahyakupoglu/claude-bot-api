package com.claudebot.api.service;

import com.claudebot.api.config.AgentProperties;
import com.claudebot.api.config.AuthProperties;
import com.claudebot.api.dto.AgentInfo;
import com.claudebot.api.dto.AgentReloadResponse;
import com.claudebot.api.dto.SessionInfo;
import com.claudebot.api.dto.SkillInfo;
import com.claudebot.api.exception.AgentBusyException;
import com.claudebot.api.exception.AgentReloadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Pulls the agent repository again and re-runs the setup script, so the next Claude run
 * reads the updated manifest, prompts, skills and MCP config without a container restart.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final int ERROR_TAIL_LINES = 20;

    private final AgentProperties properties;
    private final AgentRunGuard runGuard;
    private final SessionStore sessionStore;
    private final SkillService skillService;
    private final AuthProperties authProperties;
    private final RunStore runStore;

    public AgentService(AgentProperties properties, AgentRunGuard runGuard, SessionStore sessionStore,
                        SkillService skillService, AuthProperties authProperties, RunStore runStore) {
        this.authProperties = authProperties;
        this.runStore = runStore;
        this.properties = properties;
        this.runGuard = runGuard;
        this.sessionStore = sessionStore;
        this.skillService = skillService;
    }

    public AgentInfo getInfo() {
        return new AgentInfo(agentName(), readCommit(), runGuard.activeRuns(), authProperties.isEnabled());
    }

    // AI_AGENT_NAME (entrypoint belirler), rol klasörü (eski yapı) ya da repo adı; URL'deki token dışarı çıkmaz
    private String agentName() {
        String agentName = System.getenv("AI_AGENT_NAME");
        if (agentName != null && !agentName.isBlank()) {
            return agentName;
        }
        String role = System.getenv("AI_AGENT_ROLE");
        if (role != null && !role.isBlank()) {
            return role;
        }
        String url = System.getenv("AI_AGENT_GIT_URL");
        if (url == null || url.isBlank()) {
            url = System.getenv("AI_AGENT_CONFIGS_GIT_URL");
        }
        if (url == null || url.isBlank()) {
            return "claude-bot";
        }
        String name = url.replaceAll("/+$", "");
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.endsWith(".git") ? name.substring(0, name.length() - 4) : name;
    }

    public AgentReloadResponse reload() {
        if (!runGuard.tryAcquireReload()) {
            int activeRuns = runGuard.activeRuns();
            log.warn("Reload rejected, agent busy: activeRuns={}", activeRuns);
            throw new AgentBusyException(activeRuns);
        }
        try {
            long start = System.currentTimeMillis();
            String previousCommit = readCommit();
            runSetupScript();
            String commit = readCommit();

            // Açık oturumlar eski talimatları context'inde taşır; yeni dosyalarla baştan başlasınlar
            List<String> sessionIds = sessionStore.getAll().stream().map(SessionInfo::getSessionId).toList();
            sessionIds.forEach(id -> {
                sessionStore.remove(id);
                runStore.removeBySession(id);
            });
            int closedSessions = sessionIds.size();

            List<String> skills = skillService.listSkills().stream().map(SkillInfo::name).toList();
            long duration = System.currentTimeMillis() - start;
            log.info("Agent reloaded: {} -> {}, closedSessions={}, skills={}, durationMs={}",
                    previousCommit, commit, closedSessions, skills, duration);
            return new AgentReloadResponse(previousCommit, commit,
                    commit != null && !commit.equals(previousCommit), closedSessions, skills, duration);
        } finally {
            runGuard.releaseReload();
        }
    }

    private void runSetupScript() {
        Path output;
        try {
            output = Files.createTempFile("agent-setup", ".log");
        } catch (IOException e) {
            throw new AgentReloadException("Failed to create setup log file: " + e.getMessage(), e);
        }
        try {
            Process process = new ProcessBuilder("bash", properties.getSetupScript())
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            process.getOutputStream().close();

            boolean finished = process.waitFor(properties.getSetupTimeoutSeconds(), TimeUnit.SECONDS);
            List<String> lines = Files.readAllLines(output, StandardCharsets.UTF_8);
            lines.forEach(line -> log.info("[setup] {}", line));

            if (!finished) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new AgentReloadException("Agent setup timed out after " + properties.getSetupTimeoutSeconds() + "s");
            }
            if (process.exitValue() != 0) {
                String tail = String.join("\n", lines.subList(Math.max(0, lines.size() - ERROR_TAIL_LINES), lines.size()));
                throw new AgentReloadException("Agent setup failed with exit code " + process.exitValue() + ": " + tail);
            }
        } catch (IOException e) {
            throw new AgentReloadException("Failed to run agent setup: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentReloadException("Agent setup was interrupted", e);
        } finally {
            try {
                Files.deleteIfExists(output);
            } catch (IOException ignored) {
            }
        }
    }

    private String readCommit() {
        try {
            return Files.readString(Path.of(properties.getCommitFile()), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }
}

package com.claudebot.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "claude.cli")
public class ClaudeProperties {

    private String path = "claude";
    private int timeoutSeconds = 120;
    private String defaultCwd = "/app";

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getDefaultCwd() {
        return defaultCwd;
    }

    public void setDefaultCwd(String defaultCwd) {
        this.defaultCwd = defaultCwd;
    }
}

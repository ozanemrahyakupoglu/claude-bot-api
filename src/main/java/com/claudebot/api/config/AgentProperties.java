package com.claudebot.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "claude.agent")
public class AgentProperties {

    private String setupScript = "/app/setup-agent.sh";
    private String commitFile = "/app/.agent-commit";
    private int setupTimeoutSeconds = 300;

    public String getSetupScript() {
        return setupScript;
    }

    public void setSetupScript(String setupScript) {
        this.setupScript = setupScript;
    }

    public String getCommitFile() {
        return commitFile;
    }

    public void setCommitFile(String commitFile) {
        this.commitFile = commitFile;
    }

    public int getSetupTimeoutSeconds() {
        return setupTimeoutSeconds;
    }

    public void setSetupTimeoutSeconds(int setupTimeoutSeconds) {
        this.setupTimeoutSeconds = setupTimeoutSeconds;
    }
}

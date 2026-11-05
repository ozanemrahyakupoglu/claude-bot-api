package com.claudebot.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "claude.runs")
public class RunProperties {

    /** Finished runs kept in memory; the oldest are dropped first. */
    private int maxRuns = 100;
    /** CLI events kept per run for the UI; older ones are dropped. */
    private int maxEventsPerRun = 1000;
    /** Longer text values in events are cut to this length. */
    private int maxEventTextLength = 2000;

    public int getMaxRuns() {
        return maxRuns;
    }

    public void setMaxRuns(int maxRuns) {
        this.maxRuns = maxRuns;
    }

    public int getMaxEventsPerRun() {
        return maxEventsPerRun;
    }

    public void setMaxEventsPerRun(int maxEventsPerRun) {
        this.maxEventsPerRun = maxEventsPerRun;
    }

    public int getMaxEventTextLength() {
        return maxEventTextLength;
    }

    public void setMaxEventTextLength(int maxEventTextLength) {
        this.maxEventTextLength = maxEventTextLength;
    }
}

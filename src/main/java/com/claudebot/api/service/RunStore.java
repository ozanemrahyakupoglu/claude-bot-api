package com.claudebot.api.service;

import com.claudebot.api.config.RunProperties;
import com.claudebot.api.dto.Run;
import com.claudebot.api.dto.RunStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recent runs, oldest first. Running runs are never evicted.
 */
@Component
public class RunStore {

    private final RunProperties properties;
    private final Map<String, Run> runs = new LinkedHashMap<>();

    public RunStore(RunProperties properties) {
        this.properties = properties;
    }

    public synchronized void add(Run run) {
        runs.put(run.getRunId(), run);
        Iterator<Run> it = runs.values().iterator();
        int excess = runs.size() - properties.getMaxRuns();
        while (excess > 0 && it.hasNext()) {
            if (it.next().getStatus() != RunStatus.RUNNING) {
                it.remove();
                excess--;
            }
        }
    }

    public synchronized Run get(String runId) {
        return runs.get(runId);
    }

    /**
     * Newest first. sessionId "none" selects single-shot runs, null selects all.
     */
    public synchronized List<Run> list(String sessionId, int limit) {
        List<Run> result = new ArrayList<>();
        List<Run> all = new ArrayList<>(runs.values());
        for (int i = all.size() - 1; i >= 0 && result.size() < limit; i--) {
            Run run = all.get(i);
            if (sessionId == null
                    || ("none".equals(sessionId) && run.getSessionId() == null)
                    || sessionId.equals(run.getSessionId())) {
                result.add(run);
            }
        }
        return result;
    }

    public synchronized void removeBySession(String sessionId) {
        runs.values().removeIf(r -> sessionId.equals(r.getSessionId()) && r.getStatus() != RunStatus.RUNNING);
    }
}

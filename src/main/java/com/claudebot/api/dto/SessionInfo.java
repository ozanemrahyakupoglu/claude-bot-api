package com.claudebot.api.dto;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

public class SessionInfo {

    private static final int TITLE_LENGTH = 80;

    private final String sessionId;
    private final String cwd;
    private final Instant createdAt;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile Instant lastUsedAt;
    private volatile boolean started = false;
    private volatile String title;
    private volatile int messageCount;

    public SessionInfo(String sessionId, String cwd) {
        this.sessionId = sessionId;
        this.cwd = cwd;
        this.createdAt = Instant.now();
        this.lastUsedAt = this.createdAt;
    }

    public void touch() {
        this.lastUsedAt = Instant.now();
    }

    public void markStarted() {
        this.started = true;
    }

    /**
     * Records a message sent to the session; the first one becomes its title.
     */
    public synchronized void recordMessage(String content) {
        if (title == null) {
            String line = content.strip().replaceAll("\\s+", " ");
            title = line.length() > TITLE_LENGTH ? line.substring(0, TITLE_LENGTH) + "…" : line;
        }
        messageCount++;
    }

    /**
     * Claims the session for one CLI run; false if another message is still running on it.
     */
    public boolean tryAcquire() {
        return busy.compareAndSet(false, true);
    }

    public void release() {
        busy.set(false);
    }

    public boolean isBusy() { return busy.get(); }
    public boolean isStarted() { return started; }
    public String getSessionId() { return sessionId; }
    public String getCwd() { return cwd; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public String getTitle() { return title; }
    public int getMessageCount() { return messageCount; }
}

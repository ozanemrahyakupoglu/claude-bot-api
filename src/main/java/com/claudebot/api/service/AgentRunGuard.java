package com.claudebot.api.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Keeps agent reloads and Claude runs apart: any number of runs can share the agent files,
 * a reload needs them to itself.
 */
@Component
public class AgentRunGuard {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** Blocks while a reload is in progress. */
    public void acquireRun() {
        lock.readLock().lock();
    }

    public void releaseRun() {
        lock.readLock().unlock();
    }

    /** False if a Claude run is in progress. */
    public boolean tryAcquireReload() {
        return lock.writeLock().tryLock();
    }

    public void releaseReload() {
        lock.writeLock().unlock();
    }

    public int activeRuns() {
        return lock.getReadLockCount();
    }
}

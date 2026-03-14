package com.claudebot.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SessionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupService.class);

    private final SessionStore sessionStore;

    public SessionCleanupService(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Scheduled(fixedRate = 3_600_000) // her saat çalışır
    public void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);

        List<String> expired = sessionStore.getAll().stream()
                .filter(s -> s.getLastUsedAt().isBefore(cutoff))
                .map(s -> s.getSessionId())
                .toList();

        if (expired.isEmpty()) {
            return;
        }

        expired.forEach(sessionStore::remove);
        log.info("Cleaned up {} expired session(s) (idle > 24h)", expired.size());
    }
}

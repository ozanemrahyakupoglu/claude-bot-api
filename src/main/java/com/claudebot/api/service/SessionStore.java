package com.claudebot.api.service;

import com.claudebot.api.dto.SessionInfo;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStore {

    private final ConcurrentHashMap<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public SessionInfo put(SessionInfo session) {
        sessions.put(session.getSessionId(), session);
        return session;
    }

    public SessionInfo get(String sessionId) {
        return sessions.get(sessionId);
    }

    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public Collection<SessionInfo> getAll() {
        return Collections.unmodifiableCollection(sessions.values());
    }
}

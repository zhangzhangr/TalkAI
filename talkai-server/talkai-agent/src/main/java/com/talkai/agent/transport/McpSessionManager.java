package com.talkai.agent.transport;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class McpSessionManager {

    private final ConcurrentHashMap<String, McpSession> sessions = new ConcurrentHashMap<>();

    public McpSession createSession() {
        McpSession session = new McpSession();
        sessions.put(session.getSessionId(), session);
        log.info("MCP session created: {}", session.getSessionId());
        return session;
    }

    public McpSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void removeSession(String sessionId) {
        McpSession removed = sessions.remove(sessionId);
        if (removed != null) {
            removed.complete();
            log.info("MCP session removed: {}", sessionId);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(5));
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().getCreatedAt().isBefore(cutoff)) {
                entry.getValue().complete();
                log.info("MCP session expired: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }
}

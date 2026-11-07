package com.budgettracker.backend.mcp;

import com.budgettracker.backend.exception.ForbiddenActionException;
import com.budgettracker.backend.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class McpSessionSecurityRegistry {

    private final ConcurrentMap<String, Long> sessionUserMap = new ConcurrentHashMap<>();

    /**
     * Binds an MCP session to an authenticated user ID.
     */
    public void registerSession(String sessionId, Long userId) {
        if (sessionId == null || userId == null) return;
        sessionUserMap.put(sessionId, userId);
        log.debug("Bound MCP session {} to user {}", sessionId, userId);
    }

    /**
     * Verifies that the given session belongs to the provided user.
     * Throws ForbiddenActionException if there is a cross-user mismatch.
     */
    public void validateSessionOwner(String sessionId, User user) {
        if (sessionId == null || user == null) return;
        Long boundUserId = sessionUserMap.get(sessionId);
        if (boundUserId != null && !boundUserId.equals(user.getId())) {
            log.warn("Cross-user session violation attempt: session {} is bound to user {}, but user {} attempted access",
                    sessionId, boundUserId, user.getId());
            throw new ForbiddenActionException("Cross-user session access denied: session does not belong to authenticated user");
        }
    }

    public void removeSession(String sessionId) {
        if (sessionId != null) {
            sessionUserMap.remove(sessionId);
        }
    }
}

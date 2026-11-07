package com.budgettracker.backend.mcp;

import com.budgettracker.backend.exception.ForbiddenActionException;
import com.budgettracker.backend.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class McpSecurityContextHelper {

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User)) {
            throw new ForbiddenActionException("User is not authenticated for MCP tool execution");
        }
        return (User) auth.getPrincipal();
    }
}

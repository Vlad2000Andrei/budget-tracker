package com.budgettracker.backend.mcp;

import com.budgettracker.backend.dto.AccountDto;
import com.budgettracker.backend.dto.UserDto;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.service.AccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class AccountMcpTools {

    private final AccountService accountService;
    private final McpSecurityContextHelper securityHelper;

    @Autowired
    public AccountMcpTools(AccountService accountService,
                           McpSecurityContextHelper securityHelper) {
        this.accountService = accountService;
        this.securityHelper = securityHelper;
    }

    @Tool(name = "list_accounts", description = "List all financial accounts (Checking, Savings, Credit Card, etc.) belonging to the user with their IDs, names, balances, and currencies.")
    public List<AccountDto> listAccounts() {
        User user = securityHelper.getCurrentUser();
        return accountService.getAccounts(user);
    }

    @Tool(name = "get_user_profile", description = "Get the current user's profile information, including display name, email, and default currency.")
    public UserDto getUserProfile() {
        User user = securityHelper.getCurrentUser();
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .defaultCurrency(user.getDefaultCurrency())
                .displayName(user.getDisplayName())
                .isOnboarded(user.isOnboarded())
                .build();
    }
}

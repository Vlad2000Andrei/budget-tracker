package com.budgettracker.backend.config;

import com.budgettracker.backend.mcp.*;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfiguration {

    @Bean
    public ToolCallbackProvider budgetTrackerMcpToolCallbackProvider(
            TransactionMcpTools transactionMcpTools,
            CategoryMcpTools categoryMcpTools,
            StatsMcpTools statsMcpTools,
            SavingsGoalMcpTools savingsGoalMcpTools,
            AccountMcpTools accountMcpTools) {
        return () -> ToolCallbacks.from(
                transactionMcpTools,
                categoryMcpTools,
                statsMcpTools,
                savingsGoalMcpTools,
                accountMcpTools
        );
    }
}

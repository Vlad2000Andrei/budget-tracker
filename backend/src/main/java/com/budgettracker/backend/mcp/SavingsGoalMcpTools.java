package com.budgettracker.backend.mcp;

import com.budgettracker.backend.dto.SavingsGoalDto;
import com.budgettracker.backend.dto.SavingsTransactionDto;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.service.SavingsGoalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class SavingsGoalMcpTools {

    private final SavingsGoalService savingsGoalService;
    private final McpSecurityContextHelper securityHelper;

    @Autowired
    public SavingsGoalMcpTools(SavingsGoalService savingsGoalService,
                               McpSecurityContextHelper securityHelper) {
        this.savingsGoalService = savingsGoalService;
        this.securityHelper = securityHelper;
    }

    @Tool(name = "list_savings_goals", description = "List all savings goals for the user, showing target amount, current amount saved, target date, and category.")
    public List<SavingsGoalDto> listSavingsGoals() {
        User user = securityHelper.getCurrentUser();
        return savingsGoalService.getSavingsGoals(user);
    }

    @Tool(name = "get_savings_goal", description = "Get detailed progress for a specific savings goal by its ID.")
    public SavingsGoalDto getSavingsGoal(
            @ToolParam(description = "Savings goal ID", required = true) Long goalId) {
        User user = securityHelper.getCurrentUser();
        return savingsGoalService.getSavingsGoal(goalId, user);
    }

    @Tool(name = "list_savings_transactions", description = "List historical deposits and withdrawals for a specific savings goal.")
    public List<SavingsTransactionDto> listSavingsTransactions(
            @ToolParam(description = "Savings goal ID", required = true) Long goalId) {
        User user = securityHelper.getCurrentUser();
        return savingsGoalService.getSavingsTransactions(goalId, user);
    }
}

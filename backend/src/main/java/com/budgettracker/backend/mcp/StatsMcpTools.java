package com.budgettracker.backend.mcp;

import com.budgettracker.backend.dto.DashboardSummaryDto;
import com.budgettracker.backend.dto.NetCashFlowDto;
import com.budgettracker.backend.dto.SpendingBreakdownDto;
import com.budgettracker.backend.dto.TransactionDto;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.CategoryRepository;
import com.budgettracker.backend.service.DashboardService;
import com.budgettracker.backend.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Component
public class StatsMcpTools {

    private final DashboardService dashboardService;
    private final TransactionService transactionService;
    private final CategoryRepository categoryRepository;
    private final McpSecurityContextHelper securityHelper;

    @Autowired
    public StatsMcpTools(DashboardService dashboardService,
                         TransactionService transactionService,
                         CategoryRepository categoryRepository,
                         McpSecurityContextHelper securityHelper) {
        this.dashboardService = dashboardService;
        this.transactionService = transactionService;
        this.categoryRepository = categoryRepository;
        this.securityHelper = securityHelper;
    }

    @Tool(name = "get_dashboard_summary", description = "Get full high-level financial dashboard summary: total balance across accounts, month income and expenses (split by recurring vs one-off), active budgets vs spending, savings goals progress, and account summaries.")
    public DashboardSummaryDto getDashboardSummary() {
        User user = securityHelper.getCurrentUser();
        return dashboardService.getDashboardSummary(user);
    }

    @Tool(name = "get_spending_breakdown", description = "Get breakdown of spending grouped by category for a specific date range in the user's default currency.")
    public SpendingBreakdownDto getSpendingBreakdown(
            @ToolParam(description = "Start date in YYYY-MM-DD format (defaults to start of current month)", required = false) String startDate,
            @ToolParam(description = "End date in YYYY-MM-DD format (defaults to today)", required = false) String endDate,
            @ToolParam(description = "Optional account ID to filter by", required = false) Long accountId) {

        User user = securityHelper.getCurrentUser();
        LocalDate start = (startDate != null && !startDate.isBlank())
                ? LocalDate.parse(startDate.trim())
                : LocalDate.now().withDayOfMonth(1);
        LocalDate end = (endDate != null && !endDate.isBlank())
                ? LocalDate.parse(endDate.trim())
                : LocalDate.now();

        List<TransactionDto> txs = transactionService.getTransactions(user, accountId, null, start, end, "EXPENSE");

        Map<Long, BigDecimal> categorySums = new HashMap<>();
        BigDecimal totalExpense = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        for (TransactionDto tx : txs) {
            if ("EXPENSE".equalsIgnoreCase(tx.getType())) {
                BigDecimal amt = tx.getConvertedAmount() != null ? tx.getConvertedAmount() : tx.getAmount();
                categorySums.put(tx.getCategoryId(), categorySums.getOrDefault(tx.getCategoryId(), BigDecimal.ZERO).add(amt));
                totalExpense = totalExpense.add(amt);
            }
        }

        List<SpendingBreakdownDto.CategorySpendingItem> items = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : categorySums.entrySet()) {
            Long catId = entry.getKey();
            BigDecimal sum = entry.getValue();
            Optional<Category> catOpt = categoryRepository.findById(catId);

            String catName = catOpt.map(Category::getName).orElse("Category #" + catId);
            String icon = catOpt.map(Category::getIcon).orElse(null);
            String color = catOpt.map(Category::getColor).orElse(null);

            double pct = totalExpense.compareTo(BigDecimal.ZERO) > 0
                    ? sum.multiply(BigDecimal.valueOf(100)).divide(totalExpense, 2, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;

            items.add(SpendingBreakdownDto.CategorySpendingItem.builder()
                    .categoryId(catId)
                    .categoryName(catName)
                    .icon(icon)
                    .color(color)
                    .amount(sum)
                    .percentage(pct)
                    .build());
        }

        items.sort((a, b) -> b.getAmount().compareTo(a.getAmount()));

        return SpendingBreakdownDto.builder()
                .currency(user.getDefaultCurrency() != null ? user.getDefaultCurrency() : "USD")
                .totalExpense(totalExpense)
                .categories(items)
                .build();
    }

    @Tool(name = "get_net_cash_flow", description = "Get net cash flow (total income vs total expenses, net savings, and savings rate) for a date range.")
    public NetCashFlowDto getNetCashFlow(
            @ToolParam(description = "Start date in YYYY-MM-DD format (defaults to start of current month)", required = false) String startDate,
            @ToolParam(description = "End date in YYYY-MM-DD format (defaults to today)", required = false) String endDate) {

        User user = securityHelper.getCurrentUser();
        LocalDate start = (startDate != null && !startDate.isBlank())
                ? LocalDate.parse(startDate.trim())
                : LocalDate.now().withDayOfMonth(1);
        LocalDate end = (endDate != null && !endDate.isBlank())
                ? LocalDate.parse(endDate.trim())
                : LocalDate.now();

        List<TransactionDto> txs = transactionService.getTransactions(user, null, null, start, end, null);

        BigDecimal totalIncome = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalExpenses = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        for (TransactionDto tx : txs) {
            BigDecimal amt = tx.getConvertedAmount() != null ? tx.getConvertedAmount() : tx.getAmount();
            if ("INCOME".equalsIgnoreCase(tx.getType())) {
                totalIncome = totalIncome.add(amt);
            } else if ("EXPENSE".equalsIgnoreCase(tx.getType())) {
                totalExpenses = totalExpenses.add(amt);
            }
        }

        BigDecimal netCashFlow = totalIncome.subtract(totalExpenses);
        double savingsRate = 0.0;
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = netCashFlow.multiply(BigDecimal.valueOf(100))
                    .divide(totalIncome, 2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        return NetCashFlowDto.builder()
                .currency(user.getDefaultCurrency() != null ? user.getDefaultCurrency() : "USD")
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .netCashFlow(netCashFlow)
                .savingsRate(savingsRate)
                .build();
    }
}

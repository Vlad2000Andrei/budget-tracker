package com.budgettracker.backend.service;

import com.budgettracker.backend.dto.DashboardSummaryDto;
import com.budgettracker.backend.dto.AccountDto;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.model.Budget;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.SavingsGoal;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.BudgetRepository;
import com.budgettracker.backend.repository.CategoryRepository;
import com.budgettracker.backend.repository.SavingsGoalRepository;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.budgettracker.backend.jooq.Tables.TRANSACTIONS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.sum;

@Service
public class DashboardService {

    private final AccountService accountService;
    private final BudgetRepository budgetRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final CategoryRepository categoryRepository;
    private final CurrencyExchangeService currencyExchangeService;
    private final DSLContext dsl;

    @Autowired
    public DashboardService(AccountService accountService,
                            BudgetRepository budgetRepository,
                            SavingsGoalRepository savingsGoalRepository,
                            CategoryRepository categoryRepository,
                            CurrencyExchangeService currencyExchangeService,
                            DSLContext dsl) {
        this.accountService = accountService;
        this.budgetRepository = budgetRepository;
        this.savingsGoalRepository = savingsGoalRepository;
        this.categoryRepository = categoryRepository;
        this.currencyExchangeService = currencyExchangeService;
        this.dsl = dsl;
    }

    public DashboardSummaryDto getDashboardSummary(User user) {
        // 1. Calculate Total Balance across all accounts in user's default currency
        List<AccountDto> accounts = accountService.getAccounts(user);
        BigDecimal totalBalance = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        for (AccountDto account : accounts) {
            BigDecimal converted = currencyExchangeService.convert(
                    account.getBalance(), 
                    account.getCurrency(), 
                    user.getDefaultCurrency()
            );
            totalBalance = totalBalance.add(converted);
        }

        // 2. Calculate Monthly Cash Flow (Income, Expenses) for the current month (in user's default currency)
        LocalDate today = LocalDate.now();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfNextMonth = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();

        List<org.jooq.Record3<BigDecimal, String, CategoryType>> monthlyTxs = dsl.select(
                TRANSACTIONS.CONVERTED_AMOUNT,
                TRANSACTIONS.CONVERTED_CURRENCY,
                TRANSACTIONS.TYPE
        )
        .from(TRANSACTIONS)
        .where(TRANSACTIONS.USER_ID.eq(user.getId())
                .and(TRANSACTIONS.DATE.ge(startOfMonth))
                .and(TRANSACTIONS.DATE.lt(startOfNextMonth)))
        .fetch();

        BigDecimal monthIncome = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal monthExpenses = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        for (var rec : monthlyTxs) {
            BigDecimal amt = rec.value1();
            String curr = rec.value2();
            CategoryType type = rec.value3();

            BigDecimal converted = currencyExchangeService.convert(amt, curr, user.getDefaultCurrency());
            if (type == CategoryType.INCOME) {
                monthIncome = monthIncome.add(converted);
            } else if (type == CategoryType.EXPENSE) {
                monthExpenses = monthExpenses.add(converted);
            }
        }

        // 3. Budgets Progress: Fetch active budgets and compute spent vs limit
        List<Budget> budgets = budgetRepository.findByUserId(user.getId());
        List<DashboardSummaryDto.BudgetSummaryDto> budgetSummaries = new ArrayList<>();
        
        for (Budget budget : budgets) {
            // Check if active (current date falls within startDate and endDate)
            if (!today.isBefore(budget.getStartDate()) && !today.isAfter(budget.getEndDate())) {
                Category category = categoryRepository.findById(budget.getCategoryId()).orElse(null);
                
                // Get descendant category IDs to sum up spent
                List<Long> descendants = categoryRepository.getDescendantCategoryIds(budget.getCategoryId());
                
                List<org.jooq.Record2<BigDecimal, String>> budgetTxs = dsl.select(
                        TRANSACTIONS.CONVERTED_AMOUNT,
                        TRANSACTIONS.CONVERTED_CURRENCY
                )
                .from(TRANSACTIONS)
                .where(TRANSACTIONS.USER_ID.eq(user.getId())
                        .and(TRANSACTIONS.CATEGORY_ID.in(descendants))
                        .and(TRANSACTIONS.TYPE.eq(CategoryType.EXPENSE))
                        .and(TRANSACTIONS.DATE.ge(budget.getStartDate().atStartOfDay()))
                        .and(TRANSACTIONS.DATE.lt(budget.getEndDate().plusDays(1).atStartOfDay())))
                .fetch();

                BigDecimal spent = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
                for (var rec : budgetTxs) {
                    BigDecimal amt = rec.value1();
                    String curr = rec.value2();
                    BigDecimal converted = currencyExchangeService.convert(amt, curr, user.getDefaultCurrency());
                    spent = spent.add(converted);
                }

                int pct = 0;
                if (budget.getAmountLimit().compareTo(BigDecimal.ZERO) > 0) {
                    pct = spent.multiply(new BigDecimal("100"))
                            .divide(budget.getAmountLimit(), 0, RoundingMode.HALF_UP)
                            .intValue();
                }

                budgetSummaries.add(DashboardSummaryDto.BudgetSummaryDto.builder()
                        .id(budget.getId())
                        .categoryId(budget.getCategoryId())
                        .categoryName(category != null ? category.getName() : "Unknown")
                        .categoryIcon(category != null ? category.getIcon() : "")
                        .categoryColor(category != null ? category.getColor() : "")
                        .spent(spent)
                        .limit(budget.getAmountLimit())
                        .pct(pct)
                        .build());
            }
        }

        // 4. Savings Goals Progress: Fetch savings goals and map them
        List<SavingsGoal> savingsGoals = savingsGoalRepository.findByUserId(user.getId());
        List<DashboardSummaryDto.SavingsGoalSummaryDto> savingsGoalSummaries = new ArrayList<>();

        for (SavingsGoal goal : savingsGoals) {
            Category category = categoryRepository.findById(goal.getCategoryId()).orElse(null);
            
            int pct = 0;
            if (goal.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
                pct = goal.getCurrentAmount().multiply(new BigDecimal("100"))
                        .divide(goal.getTargetAmount(), 0, RoundingMode.HALF_UP)
                        .intValue();
            }

            savingsGoalSummaries.add(DashboardSummaryDto.SavingsGoalSummaryDto.builder()
                    .id(goal.getId())
                    .categoryId(goal.getCategoryId())
                    .categoryName(category != null ? category.getName() : "Unknown")
                    .categoryIcon(category != null ? category.getIcon() : "")
                    .categoryColor(category != null ? category.getColor() : "")
                    .current(goal.getCurrentAmount())
                    .target(goal.getTargetAmount())
                    .pct(pct)
                    .build());
        }

        return DashboardSummaryDto.builder()
                .totalBalance(totalBalance)
                .balanceCurrency(user.getDefaultCurrency())
                .monthIncome(monthIncome)
                .monthExpenses(monthExpenses)
                .budgets(budgetSummaries)
                .savingsGoals(savingsGoalSummaries)
                .build();
    }
}

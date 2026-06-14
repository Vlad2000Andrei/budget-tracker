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
                List<DashboardSummaryDto.AccountSummaryDto> accountSummaries = new ArrayList<>();

                for (AccountDto account : accounts) {
                        BigDecimal converted = currencyExchangeService.convert(
                                        account.getBalance(),
                                        account.getCurrency(),
                                        user.getDefaultCurrency());
                        totalBalance = totalBalance.add(converted);
                }

                for (AccountDto account : accounts) {
                        BigDecimal converted = currencyExchangeService.convert(
                                        account.getBalance(),
                                        account.getCurrency(),
                                        user.getDefaultCurrency());

                        int percentage = 0;
                        if (totalBalance.compareTo(BigDecimal.ZERO) > 0) {
                                percentage = converted.multiply(new BigDecimal("100"))
                                                .divide(totalBalance, 0, RoundingMode.HALF_UP)
                                                .intValue();
                        }

                        accountSummaries.add(DashboardSummaryDto.AccountSummaryDto.builder()
                                        .id(account.getId())
                                        .name(account.getName())
                                        .balance(account.getBalance())
                                        .currency(account.getCurrency())
                                        .convertedBalance(converted)
                                        .percentage(percentage)
                                        .build());
                }

                // 2. Calculate Monthly Cash Flow — split by recurring vs one-off
                LocalDate today = LocalDate.now();
                LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
                LocalDateTime startOfNextMonth = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();

                BigDecimal recurringIncome = sumMonthlyTxs(user, startOfMonth, startOfNextMonth, CategoryType.INCOME,
                                false);
                BigDecimal oneOffIncome = sumMonthlyTxs(user, startOfMonth, startOfNextMonth, CategoryType.INCOME,
                                true);
                BigDecimal recurringExpenses = sumMonthlyTxs(user, startOfMonth, startOfNextMonth, CategoryType.EXPENSE,
                                false);
                BigDecimal oneOffExpenses = sumMonthlyTxs(user, startOfMonth, startOfNextMonth, CategoryType.EXPENSE,
                                true);

                BigDecimal monthIncome = recurringIncome.add(oneOffIncome);
                BigDecimal monthExpenses = recurringExpenses.add(oneOffExpenses);

                // 3. Budgets Progress: Fetch active budgets and compute spent vs limit
                List<Budget> budgets = budgetRepository.findByUserId(user.getId());
                List<DashboardSummaryDto.BudgetSummaryDto> budgetSummaries = new ArrayList<>();

                for (Budget budget : budgets) {
                        // Check if active (current date falls within startDate and endDate, if endDate is specified)
                        boolean isActive = !today.isBefore(budget.getStartDate()) && 
                                           (budget.getEndDate() == null || !today.isAfter(budget.getEndDate()));
                        if (isActive) {
                                Category category = categoryRepository.findById(budget.getCategoryId()).orElse(null);

                                // Get descendant category IDs to sum up spent
                                List<Long> descendants = categoryRepository
                                                .getDescendantCategoryIds(budget.getCategoryId());

                                LocalDateTime startQuery;
                                LocalDateTime endQuery;
                                if (budget.getEndDate() != null) {
                                        startQuery = budget.getStartDate().atStartOfDay();
                                        endQuery = budget.getEndDate().plusDays(1).atStartOfDay();
                                } else {
                                        LocalDate startOfMonthDate = today.withDayOfMonth(1);
                                        LocalDate actualStartDate = budget.getStartDate().isAfter(startOfMonthDate) ? budget.getStartDate() : startOfMonthDate;
                                        startQuery = actualStartDate.atStartOfDay();
                                        endQuery = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
                                }

                                List<org.jooq.Record2<BigDecimal, String>> budgetTxs = dsl.select(
                                                TRANSACTIONS.CONVERTED_AMOUNT,
                                                TRANSACTIONS.CONVERTED_CURRENCY)
                                                .from(TRANSACTIONS)
                                                .where(TRANSACTIONS.USER_ID.eq(user.getId())
                                                                .and(TRANSACTIONS.CATEGORY_ID.in(descendants))
                                                                .and(TRANSACTIONS.TYPE.eq(CategoryType.EXPENSE))
                                                                .and(TRANSACTIONS.DATE.ge(startQuery))
                                                                .and(TRANSACTIONS.DATE.lt(endQuery)))
                                                .fetch();

                                BigDecimal spent = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
                                for (var rec : budgetTxs) {
                                        BigDecimal amt = rec.value1();
                                        String curr = rec.value2();
                                        BigDecimal converted = currencyExchangeService.convert(amt, curr,
                                                        user.getDefaultCurrency());
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
                                .recurringIncome(recurringIncome)
                                .oneOffIncome(oneOffIncome)
                                .recurringExpenses(recurringExpenses)
                                .oneOffExpenses(oneOffExpenses)
                                .budgets(budgetSummaries)
                                .savingsGoals(savingsGoalSummaries)
                                .accounts(accountSummaries)
                                .build();
        }

        /**
         * Sums converted_amount for transactions in the given month window,
         * filtered by type and whether they are one-off (recurrence_rule_id IS NULL)
         * or recurring (recurrence_rule_id IS NOT NULL).
         *
         * @param oneOff true → only transactions WITHOUT a recurrence rule
         *               false → only transactions WITH a recurrence rule
         */
        private BigDecimal sumMonthlyTxs(User user, LocalDateTime start, LocalDateTime end,
                        CategoryType type, boolean oneOff) {
                org.jooq.Condition condition = TRANSACTIONS.USER_ID.eq(user.getId())
                                .and(TRANSACTIONS.TYPE.eq(type))
                                .and(TRANSACTIONS.DATE.ge(start))
                                .and(TRANSACTIONS.DATE.lt(end))
                                .and(oneOff
                                                ? TRANSACTIONS.RECURRENCE_RULE_ID.isNull()
                                                : TRANSACTIONS.RECURRENCE_RULE_ID.isNotNull());

                List<org.jooq.Record2<BigDecimal, String>> rows = dsl
                                .select(TRANSACTIONS.CONVERTED_AMOUNT, TRANSACTIONS.CONVERTED_CURRENCY)
                                .from(TRANSACTIONS)
                                .where(condition)
                                .fetch();

                BigDecimal total = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
                for (var rec : rows) {
                        total = total.add(currencyExchangeService.convert(
                                        rec.value1(), rec.value2(), user.getDefaultCurrency()));
                }
                return total;
        }
}

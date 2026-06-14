package com.budgettracker.backend.service;

import com.budgettracker.backend.dto.CreateSavingsGoalRequest;
import com.budgettracker.backend.dto.CreateSavingsTransactionRequest;
import com.budgettracker.backend.dto.SavingsGoalDto;
import com.budgettracker.backend.dto.SavingsTransactionDto;
import com.budgettracker.backend.dto.UpdateSavingsGoalRequest;
import com.budgettracker.backend.exception.ForbiddenActionException;
import com.budgettracker.backend.exception.ResourceNotFoundException;
import com.budgettracker.backend.jooq.enums.AccountType;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.enums.SavingsGoalType;
import com.budgettracker.backend.jooq.enums.SavingsTransactionType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.budgettracker.backend.model.Account;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.SavingsGoal;
import com.budgettracker.backend.model.SavingsGoalTransaction;
import com.budgettracker.backend.model.Transaction;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.AccountRepository;
import com.budgettracker.backend.repository.CategoryRepository;
import com.budgettracker.backend.repository.SavingsGoalRepository;
import com.budgettracker.backend.repository.SavingsGoalTransactionRepository;
import com.budgettracker.backend.repository.TransactionRepository;
import com.budgettracker.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SavingsGoalService {

    private final SavingsGoalRepository savingsGoalRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CurrencyExchangeService currencyExchangeService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final SavingsGoalTransactionRepository savingsGoalTransactionRepository;

    @Autowired
    public SavingsGoalService(SavingsGoalRepository savingsGoalRepository,
                              CategoryRepository categoryRepository,
                              UserRepository userRepository,
                              CurrencyExchangeService currencyExchangeService,
                              AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              SavingsGoalTransactionRepository savingsGoalTransactionRepository) {
        this.savingsGoalRepository = savingsGoalRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.currencyExchangeService = currencyExchangeService;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.savingsGoalTransactionRepository = savingsGoalTransactionRepository;
    }

    public List<SavingsGoalDto> getSavingsGoals(User user) {
        return savingsGoalRepository.findByUserId(user.getId())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SavingsGoalDto createSavingsGoal(CreateSavingsGoalRequest request, User user) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));

        // Check ownership of the category
        if (category.getUserId() != null && !category.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have access to the specified category");
        }

        // Validate type is SAVINGS
        if (category.getType() != CategoryType.SAVINGS) {
            throw new IllegalArgumentException("Savings goals can only be set on categories of type SAVINGS");
        }

        // Calculate initial accumulated amount
        List<Long> descendants = categoryRepository.getDescendantCategoryIds(request.getCategoryId());
        BigDecimal initialAccumulated = BigDecimal.ZERO;
        if (request.getGoalType() == SavingsGoalType.MONTHLY) {
            LocalDate today = LocalDate.now();
            LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
            LocalDateTime startOfNextMonth = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
            initialAccumulated = calculateAccumulatedSavingsInPeriod(user, descendants, startOfMonth, startOfNextMonth);
        } else {
            initialAccumulated = calculateAccumulatedSavings(user, descendants);
        }

        SavingsGoal goal = SavingsGoal.builder()
                .userId(user.getId())
                .categoryId(request.getCategoryId())
                .goalType(request.getGoalType())
                .targetAmount(request.getTargetAmount())
                .currentAmount(initialAccumulated)
                .targetDate(request.getTargetDate())
                .build();

        SavingsGoal saved = savingsGoalRepository.save(goal);
        return mapToDto(saved);
    }

    @Transactional
    public SavingsGoalDto updateSavingsGoal(Long goalId, UpdateSavingsGoalRequest request, User user) {
        SavingsGoal existing = savingsGoalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Savings goal not found with ID: " + goalId));

        // Ownership check
        if (!existing.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have permission to modify this savings goal");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));

        // Check ownership of the category
        if (category.getUserId() != null && !category.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have access to the specified category");
        }

        // Validate type is SAVINGS
        if (category.getType() != CategoryType.SAVINGS) {
            throw new IllegalArgumentException("Savings goals can only be set on categories of type SAVINGS");
        }

        // Recalculate accumulated amount in case category changed
        List<Long> descendants = categoryRepository.getDescendantCategoryIds(request.getCategoryId());
        BigDecimal accumulated = BigDecimal.ZERO;
        if (request.getGoalType() == SavingsGoalType.MONTHLY) {
            LocalDate today = LocalDate.now();
            LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
            LocalDateTime startOfNextMonth = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
            accumulated = calculateAccumulatedSavingsInPeriod(user, descendants, startOfMonth, startOfNextMonth);
        } else {
            accumulated = calculateAccumulatedSavings(user, descendants);
        }

        existing.setCategoryId(request.getCategoryId());
        existing.setGoalType(request.getGoalType());
        existing.setTargetAmount(request.getTargetAmount());
        existing.setCurrentAmount(accumulated);
        existing.setTargetDate(request.getTargetDate());

        SavingsGoal updated = savingsGoalRepository.save(existing);
        return mapToDto(updated);
    }

    @Transactional
    public void deleteSavingsGoal(Long goalId, User user) {
        SavingsGoal existing = savingsGoalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Savings goal not found with ID: " + goalId));

        // Ownership check
        if (!existing.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have permission to delete this savings goal");
        }

        savingsGoalRepository.deleteById(goalId);
    }

    /**
     * Records a cash move between two accounts that is linked to a savings goal.
     * <p>
     * DEPOSIT: fromAccount loses funds, toAccount gains funds, goal currentAmount increases.<br>
     * WITHDRAWAL: fromAccount loses funds, toAccount gains funds, goal currentAmount decreases.
     * <p>
     * The caller determines direction via the {@code type} field and by choosing which account
     * is the source and which is the destination — no account-type restrictions are enforced.
     */
    @Transactional
    public SavingsTransactionDto createSavingsTransaction(Long goalId,
                                                          CreateSavingsTransactionRequest request,
                                                          User user) {
        // 1. Get or resolve category and goal
        SavingsGoal goal = null;
        Long resolvedGoalId = goalId;
        Long resolvedCategoryId = request.getCategoryId();

        if (goalId != null) {
            goal = savingsGoalRepository.findById(goalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Savings goal not found with ID: " + goalId));
            if (!goal.getUserId().equals(user.getId())) {
                throw new ForbiddenActionException("You do not have permission to add transactions to this savings goal");
            }
            resolvedCategoryId = goal.getCategoryId();
            resolvedGoalId = goalId;
        } else {
            if (resolvedCategoryId == null) {
                throw new IllegalArgumentException("Category ID is required when savings goal is not specified");
            }
            final Long catIdToFind = resolvedCategoryId;
            Category category = categoryRepository.findById(catIdToFind)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + catIdToFind));
            if (category.getUserId() != null && !category.getUserId().equals(user.getId())) {
                throw new ForbiddenActionException("You do not have access to the specified category");
            }
            if (category.getType() != CategoryType.SAVINGS) {
                throw new IllegalArgumentException("Savings transactions can only be set on categories of type SAVINGS");
            }

            // Check if there is an existing savings goal associated with this category
            final Long lookupCatId = resolvedCategoryId;
            List<SavingsGoal> existingGoals = savingsGoalRepository.findByCategoryId(lookupCatId)
                    .stream()
                    .filter(g -> g.getUserId().equals(user.getId()))
                    .collect(Collectors.toList());
            if (!existingGoals.isEmpty()) {
                goal = existingGoals.get(0);
                resolvedGoalId = goal.getId();
            } else {
                resolvedGoalId = null;
            }
        }

        // 2. Validate both accounts exist and belong to this user
        Account fromAccount = accountRepository.findById(request.getFromAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found with ID: " + request.getFromAccountId()));
        if (!fromAccount.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have access to the source account");
        }

        Account toAccount = accountRepository.findById(request.getToAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found with ID: " + request.getToAccountId()));
        if (!toAccount.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have access to the destination account");
        }

        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }

        boolean isDeposit = request.getType() == SavingsTransactionType.DEPOSIT;
        if (isDeposit) {
            if (fromAccount.getType() == AccountType.SAVINGS) {
                throw new IllegalArgumentException("Source account for a deposit must be a non-savings account");
            }
            if (toAccount.getType() != AccountType.SAVINGS) {
                throw new IllegalArgumentException("Destination account for a deposit must be a savings account");
            }
        } else {
            if (fromAccount.getType() != AccountType.SAVINGS) {
                throw new IllegalArgumentException("Source account for a withdrawal must be a savings account");
            }
            if (toAccount.getType() == AccountType.SAVINGS) {
                throw new IllegalArgumentException("Destination account for a withdrawal must be a non-savings account");
            }
        }

        BigDecimal amount = request.getAmount().setScale(4, RoundingMode.HALF_UP);

        // 3. Convert amount to user's default currency for goal reconciliation
        BigDecimal convertedAmount = currencyExchangeService.convert(amount, request.getCurrency(), user.getDefaultCurrency());

        // 4. Balance guards
        // fromAccount must have sufficient funds (in fromAccount's native currency)
        BigDecimal requiredInFromCurrency = currencyExchangeService.convert(amount, request.getCurrency(), fromAccount.getCurrency());
        if (fromAccount.getBalance().compareTo(requiredInFromCurrency) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient balance in source account '" + fromAccount.getName() + "'. Available: "
                    + fromAccount.getBalance() + " " + fromAccount.getCurrency());
        }

        if (!isDeposit) {
            // Withdrawal: goal must have enough accumulated savings to cover this (if goal is present)
            if (goal != null && goal.getCurrentAmount().compareTo(convertedAmount) < 0) {
                throw new IllegalArgumentException(
                        "Withdrawal exceeds current savings goal balance. Available: "
                        + goal.getCurrentAmount() + " " + user.getDefaultCurrency());
            }
        }

        // 5. Create underlying SAVINGS transaction (linked to the category).
        // Withdrawals are stored with a NEGATIVE amount so the SUM-based reconciliation
        // (calculateAccumulatedSavings) correctly nets them against deposits.
        BigDecimal signedAmount = isDeposit ? amount : amount.negate();
        BigDecimal signedConverted = isDeposit ? convertedAmount : convertedAmount.negate();
        BigDecimal exchangeRate = amount.compareTo(BigDecimal.ZERO) > 0
                ? convertedAmount.divide(amount, 6, RoundingMode.HALF_UP)
                : java.math.BigDecimal.ONE;

        // Use fromAccountId on the transaction record so the transaction list
        // shows which account initiated the move
        Transaction tx = Transaction.builder()
                .userId(user.getId())
                .categoryId(resolvedCategoryId)
                .accountId(fromAccount.getId())
                .amount(signedAmount)
                .currency(request.getCurrency().toUpperCase())
                .convertedAmount(signedConverted)
                .convertedCurrency(user.getDefaultCurrency())
                .exchangeRate(exchangeRate)
                .type(CategoryType.SAVINGS)
                .notes(request.getNotes())
                .date(request.getDate())
                .build();
        Transaction savedTx = transactionRepository.save(tx);

        // 6. Apply the cash move: fromAccount loses funds, toAccount gains funds.
        BigDecimal debitInFromCurrency = currencyExchangeService.convert(amount, request.getCurrency(), fromAccount.getCurrency());
        BigDecimal creditInToCurrency  = currencyExchangeService.convert(amount, request.getCurrency(), toAccount.getCurrency());
        accountRepository.updateBalance(fromAccount.getId(), debitInFromCurrency.negate());
        accountRepository.updateBalance(toAccount.getId(), creditInToCurrency);

        // 7. Link to the savings goal (if exists)
        SavingsGoalTransaction sgt = SavingsGoalTransaction.builder()
                .savingsGoalId(resolvedGoalId)
                .transactionId(savedTx.getId())
                .fromAccountId(fromAccount.getId())
                .toAccountId(toAccount.getId())
                .type(request.getType())
                .build();
        SavingsGoalTransaction savedSgt = savingsGoalTransactionRepository.save(sgt);

        // 8. Reconcile goal currentAmount
        if (resolvedCategoryId != null) {
            reconcileAllGoalsForCategory(user.getId(), resolvedCategoryId);
        }

        // Re-fetch accounts for fresh balances
        Account updatedFrom = accountRepository.findById(fromAccount.getId()).orElse(fromAccount);
        Account updatedTo   = accountRepository.findById(toAccount.getId()).orElse(toAccount);

        return SavingsTransactionDto.builder()
                .id(savedSgt.getId())
                .savingsGoalId(resolvedGoalId)
                .transactionId(savedTx.getId())
                .fromAccountId(fromAccount.getId())
                .fromAccountName(updatedFrom.getName())
                .toAccountId(toAccount.getId())
                .toAccountName(updatedTo.getName())
                .type(request.getType())
                .amount(amount)                    // always positive in the DTO
                .currency(request.getCurrency().toUpperCase())
                .convertedAmount(convertedAmount)  // always positive in the DTO
                .convertedCurrency(user.getDefaultCurrency())
                .date(savedTx.getDate())
                .notes(savedTx.getNotes())
                .createdAt(savedSgt.getCreatedAt())
                .build();
    }

    /**
     * Returns all savings transactions for a given goal, with both from/to account names.
     */
    public List<SavingsTransactionDto> getSavingsTransactions(Long goalId, User user) {
        SavingsGoal goal = savingsGoalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Savings goal not found with ID: " + goalId));
        if (!goal.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have permission to view transactions for this savings goal");
        }

        List<Long> descendants = categoryRepository.getDescendantCategoryIds(goal.getCategoryId());
        return transactionRepository.findByCategoryIds(user.getId(), descendants).stream()
                .map(tx -> {
                    SavingsGoalTransaction sgt = savingsGoalTransactionRepository.findByTransactionId(tx.getId()).orElse(null);
                    if (sgt == null) {
                        return null;
                    }
                    Account fromAcc     = accountRepository.findById(sgt.getFromAccountId()).orElse(null);
                    Account toAcc       = sgt.getToAccountId() != null
                            ? accountRepository.findById(sgt.getToAccountId()).orElse(null) : null;
                    BigDecimal rawAmt   = tx.getAmount();
                    BigDecimal rawConv  = tx.getConvertedAmount();
                    return SavingsTransactionDto.builder()
                            .id(sgt.getId())
                            .savingsGoalId(goalId)
                            .transactionId(tx.getId())
                            .fromAccountId(sgt.getFromAccountId())
                            .fromAccountName(fromAcc != null ? fromAcc.getName() : "Unknown")
                            .toAccountId(sgt.getToAccountId())
                            .toAccountName(toAcc != null ? toAcc.getName() : null)
                            .type(sgt.getType())
                            .amount(rawAmt != null ? rawAmt.abs() : null)
                            .currency(tx.getCurrency())
                            .convertedAmount(rawConv != null ? rawConv.abs() : null)
                            .convertedCurrency(tx.getConvertedCurrency())
                            .date(tx.getDate())
                            .notes(tx.getNotes())
                            .createdAt(sgt.getCreatedAt())
                            .build();
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    @Transactional
    public void reconcileGoalAmount(Long userId, Long categoryId) {
        List<SavingsGoal> goals = savingsGoalRepository.findByCategoryId(categoryId)
                .stream()
                .filter(g -> g.getUserId().equals(userId))
                .collect(Collectors.toList());

        if (goals.isEmpty()) {
            return;
        }

        User user = userRepository.findById(userId).orElse(null);

        for (SavingsGoal goal : goals) {
            BigDecimal sum = BigDecimal.ZERO;
            if (user != null) {
                List<Long> descendants = categoryRepository.getDescendantCategoryIds(categoryId);
                if (goal.getGoalType() == SavingsGoalType.MONTHLY) {
                    LocalDate today = LocalDate.now();
                    LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
                    LocalDateTime startOfNextMonth = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
                    sum = calculateAccumulatedSavingsInPeriod(user, descendants, startOfMonth, startOfNextMonth);
                } else {
                    sum = calculateAccumulatedSavings(user, descendants);
                }
            }
            goal.setCurrentAmount(sum);
            savingsGoalRepository.save(goal);
        }
    }

    @Transactional
    public void reconcileAllGoalsForCategory(Long userId, Long categoryId) {
        Long currentCategoryId = categoryId;
        int depth = 0;
        while (currentCategoryId != null && depth < 100) {
            reconcileGoalAmount(userId, currentCategoryId);
            Category cat = categoryRepository.findById(currentCategoryId).orElse(null);
            currentCategoryId = (cat != null) ? cat.getParentId() : null;
            depth++;
        }
    }

    private BigDecimal calculateAccumulatedSavings(User user, List<Long> categoryIds) {
        List<org.jooq.Record2<BigDecimal, String>> records = savingsGoalRepository.findSavingsTransactions(user.getId(), categoryIds);
        BigDecimal sum = BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP);
        for (var rec : records) {
            BigDecimal converted = currencyExchangeService.convert(rec.value1(), rec.value2(), user.getDefaultCurrency());
            sum = sum.add(converted);
        }
        return sum;
    }

    private BigDecimal calculateAccumulatedSavingsInPeriod(User user, List<Long> categoryIds, LocalDateTime start, LocalDateTime end) {
        List<org.jooq.Record2<BigDecimal, String>> records = savingsGoalRepository.findSavingsTransactionsInPeriod(user.getId(), categoryIds, start, end);
        BigDecimal sum = BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP);
        for (var rec : records) {
            BigDecimal converted = currencyExchangeService.convert(rec.value1(), rec.value2(), user.getDefaultCurrency());
            sum = sum.add(converted);
        }
        return sum;
    }

    private SavingsGoalDto mapToDto(SavingsGoal goal) {
        if (goal == null) {
            return null;
        }
        return SavingsGoalDto.builder()
                .id(goal.getId())
                .userId(goal.getUserId())
                .categoryId(goal.getCategoryId())
                .goalType(goal.getGoalType())
                .targetAmount(goal.getTargetAmount())
                .currentAmount(goal.getCurrentAmount())
                .targetDate(goal.getTargetDate())
                .createdAt(goal.getCreatedAt())
                .updatedAt(goal.getUpdatedAt())
                .build();
    }
}

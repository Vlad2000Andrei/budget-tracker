package com.budgettracker.backend.service;

import com.budgettracker.backend.dto.CreateRecurrenceRuleRequest;
import com.budgettracker.backend.dto.CreateTransactionRequest;
import com.budgettracker.backend.dto.CreateTransferRequest;
import com.budgettracker.backend.dto.RecurrenceRuleDto;
import com.budgettracker.backend.dto.TransactionDto;
import com.budgettracker.backend.dto.UpdateTransactionRequest;
import com.budgettracker.backend.exception.ForbiddenActionException;
import com.budgettracker.backend.exception.ResourceNotFoundException;
import com.budgettracker.backend.jooq.enums.AccountType;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.model.Account;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.RecurrenceRule;
import com.budgettracker.backend.model.Transaction;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.AccountRepository;
import com.budgettracker.backend.repository.CategoryRepository;
import com.budgettracker.backend.repository.RecurrenceRuleRepository;
import com.budgettracker.backend.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final CurrencyExchangeService currencyExchangeService;
    private final SavingsGoalService savingsGoalService;
    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final RecurringTransactionEngine recurringTransactionEngine;
    private final CategoryService categoryService;

    @Autowired
    public TransactionService(TransactionRepository transactionRepository,
                              CategoryRepository categoryRepository,
                              AccountRepository accountRepository,
                              CurrencyExchangeService currencyExchangeService,
                              SavingsGoalService savingsGoalService,
                              RecurrenceRuleRepository recurrenceRuleRepository,
                              RecurringTransactionEngine recurringTransactionEngine,
                              CategoryService categoryService) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.currencyExchangeService = currencyExchangeService;
        this.savingsGoalService = savingsGoalService;
        this.recurrenceRuleRepository = recurrenceRuleRepository;
        this.recurringTransactionEngine = recurringTransactionEngine;
        this.categoryService = categoryService;
    }

    public List<TransactionDto> getTransactions(User user, Long accountId, Long categoryId,
                                                LocalDate startDate, LocalDate endDate, String type) {
        // If accountId is provided, verify ownership
        if (accountId != null) {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));
            if (!account.getUserId().equals(user.getId())) {
                throw new ForbiddenActionException("You do not have access to the specified account");
            }
        }

        // If categoryId is provided, verify ownership/visibility
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));
            if (category.getUserId() != null && !category.getUserId().equals(user.getId())) {
                throw new ForbiddenActionException("You do not have access to the specified category");
            }
        }

        List<Transaction> list = transactionRepository.findAll(user.getId(), accountId, categoryId, startDate, endDate, type);
        java.util.List<TransactionDto> result = new java.util.ArrayList<>();
        java.util.Set<Long> processedIds = new java.util.HashSet<>();

        for (Transaction tx : list) {
            if (processedIds.contains(tx.getId())) {
                continue;
            }
            if (tx.getLinkedTransactionId() != null) {
                processedIds.add(tx.getId());
                processedIds.add(tx.getLinkedTransactionId());
            }
            result.add(mapToDto(tx));
        }

        return result;
    }

    @Transactional
    public TransactionDto createTransaction(CreateTransactionRequest request, User user) {
        // Validate Category
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));
        if (category.getUserId() != null && !category.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have access to the specified category");
        }
        if (category.getType() != request.getType()) {
            throw new IllegalArgumentException("Transaction type must match category type");
        }

        // Validate Amount based on Type
        if (request.getType() == CategoryType.SAVINGS) {
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalArgumentException("Savings transaction amount cannot be zero");
            }
        } else {
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Transaction amount must be greater than zero");
            }
        }

        // Validate Account if specified
        Account account = null;
        if (request.getAccountId() != null) {
            account = accountRepository.findById(request.getAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + request.getAccountId()));
            if (!account.getUserId().equals(user.getId())) {
                throw new ForbiddenActionException("You do not have access to the specified account");
            }
        }

        // Currency conversions
        BigDecimal exchangeRate = currencyExchangeService.getExchangeRate(request.getCurrency(), user.getDefaultCurrency());
        BigDecimal convertedAmount = request.getAmount().multiply(exchangeRate).setScale(4, java.math.RoundingMode.HALF_UP);

        // Save Recurrence Rule if applicable
        Long recurrenceRuleId = null;
        RecurrenceRule rule = null;
        if (request.getRecurrenceRule() != null) {
            var ruleReq = request.getRecurrenceRule();
            rule = RecurrenceRule.builder()
                    .frequency(ruleReq.getFrequency())
                    .interval(ruleReq.getInterval())
                    .startDate(ruleReq.getStartDate())
                    .endDate(ruleReq.getEndDate())
                    .build();
            rule = recurrenceRuleRepository.save(rule);
            recurrenceRuleId = rule.getId();
        }

        // Save Transaction
        Transaction transaction = Transaction.builder()
                .userId(user.getId())
                .categoryId(request.getCategoryId())
                .accountId(request.getAccountId())
                .recurrenceRuleId(recurrenceRuleId)
                .amount(request.getAmount())
                .currency(request.getCurrency().toUpperCase())
                .convertedAmount(convertedAmount)
                .convertedCurrency(user.getDefaultCurrency())
                .exchangeRate(exchangeRate)
                .type(request.getType())
                .notes(request.getNotes())
                .date(request.getDate())
                .build();

        Transaction saved = transactionRepository.save(transaction);

        // Adjust Account Balance if applicable
        if (account != null) {
            BigDecimal accountAmount = currencyExchangeService.convert(request.getAmount(), request.getCurrency(), account.getCurrency());
            adjustAccountBalance(account, accountAmount, request.getType());
        }

        // Reconcile savings goal if applicable
        if (request.getType() == CategoryType.SAVINGS) {
            savingsGoalService.reconcileAllGoalsForCategory(user.getId(), request.getCategoryId());
        }

        if (rule != null) {
            recurringTransactionEngine.processRule(rule, LocalDate.now());
        }

        return mapToDto(saved);
    }

    @Transactional
    public TransactionDto createTransfer(CreateTransferRequest request, User user) {
        // Validate source account
        Account fromAccount = accountRepository.findById(request.getFromAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found with ID: " + request.getFromAccountId()));
        if (!fromAccount.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have access to the source account");
        }

        // Validate destination account
        Account toAccount = accountRepository.findById(request.getToAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found with ID: " + request.getToAccountId()));
        if (!toAccount.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have access to the destination account");
        }

        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }

        if (!fromAccount.getType().equals(toAccount.getType())) {
            throw new IllegalArgumentException("Source and destination accounts must be of the same type for a move transaction");
        }

        // Check source account balance native currency requirement
        BigDecimal requiredInFromCurrency = currencyExchangeService.convert(request.getAmount(), request.getCurrency(), fromAccount.getCurrency());
        if (fromAccount.getBalance().compareTo(requiredInFromCurrency) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient balance in source account '" + fromAccount.getName() + "'. Available: "
                    + fromAccount.getBalance() + " " + fromAccount.getCurrency());
        }

        // Fetch or create double-entry Categories
        Category expenseCategory = categoryService.getOrCreateTransferCategory(user, CategoryType.EXPENSE);
        Category incomeCategory = categoryService.getOrCreateTransferCategory(user, CategoryType.INCOME);

        // Convert amounts
        BigDecimal exchangeRate = currencyExchangeService.getExchangeRate(request.getCurrency(), user.getDefaultCurrency());
        BigDecimal convertedAmount = request.getAmount().multiply(exchangeRate).setScale(4, java.math.RoundingMode.HALF_UP);

        // Create transaction A (source: EXPENSE)
        Transaction sourceTx = Transaction.builder()
                .userId(user.getId())
                .categoryId(expenseCategory.getId())
                .accountId(fromAccount.getId())
                .amount(request.getAmount())
                .currency(request.getCurrency().toUpperCase())
                .convertedAmount(convertedAmount)
                .convertedCurrency(user.getDefaultCurrency())
                .exchangeRate(exchangeRate)
                .type(CategoryType.EXPENSE)
                .notes(request.getNotes())
                .date(request.getDate())
                .build();
        Transaction savedSource = transactionRepository.save(sourceTx);

        // Create transaction B (destination: INCOME)
        Transaction destTx = Transaction.builder()
                .userId(user.getId())
                .categoryId(incomeCategory.getId())
                .accountId(toAccount.getId())
                .amount(request.getAmount())
                .currency(request.getCurrency().toUpperCase())
                .convertedAmount(convertedAmount)
                .convertedCurrency(user.getDefaultCurrency())
                .exchangeRate(exchangeRate)
                .type(CategoryType.INCOME)
                .notes(request.getNotes())
                .date(request.getDate())
                .build();
        Transaction savedDest = transactionRepository.save(destTx);

        // Link them
        transactionRepository.updateLinkedTransactionId(savedSource.getId(), savedDest.getId());
        transactionRepository.updateLinkedTransactionId(savedDest.getId(), savedSource.getId());
        savedSource.setLinkedTransactionId(savedDest.getId());

        // Adjust balances
        BigDecimal debitAmount = currencyExchangeService.convert(request.getAmount(), request.getCurrency(), fromAccount.getCurrency());
        BigDecimal creditAmount = currencyExchangeService.convert(request.getAmount(), request.getCurrency(), toAccount.getCurrency());
        adjustAccountBalance(fromAccount, debitAmount, CategoryType.EXPENSE);
        adjustAccountBalance(toAccount, creditAmount, CategoryType.INCOME);

        return mapToDto(savedSource);
    }

    @Transactional
    public TransactionDto updateTransaction(Long transactionId, UpdateTransactionRequest request, User user) {
        // Fetch existing
        Transaction existing = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));
        if (!existing.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have permission to modify this transaction");
        }

        Long oldCategoryId = existing.getCategoryId();
        CategoryType oldType = existing.getType();

        // Validate Category
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));
        if (category.getUserId() != null && !category.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have access to the specified category");
        }
        if (category.getType() != request.getType()) {
            throw new IllegalArgumentException("Transaction type must match category type");
        }

        // Validate Amount based on Type
        if (request.getType() == CategoryType.SAVINGS) {
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalArgumentException("Savings transaction amount cannot be zero");
            }
        } else {
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Transaction amount must be greater than zero");
            }
        }

        // Validate Account if specified
        Account newAccount = null;
        if (request.getAccountId() != null) {
            newAccount = accountRepository.findById(request.getAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + request.getAccountId()));
            if (!newAccount.getUserId().equals(user.getId())) {
                throw new ForbiddenActionException("You do not have access to the specified account");
            }
        }

        // 1. Reverse the old transaction impact on the old account
        if (existing.getAccountId() != null) {
            Account oldAccount = accountRepository.findById(existing.getAccountId()).orElse(null);
            if (oldAccount != null) {
                BigDecimal oldAccountAmount = currencyExchangeService.convert(existing.getAmount(), existing.getCurrency(), oldAccount.getCurrency());
                reverseAccountBalance(oldAccount, oldAccountAmount, existing.getType());
            }
        }

        // 2. Apply new transaction details & calculations
        BigDecimal exchangeRate = currencyExchangeService.getExchangeRate(request.getCurrency(), user.getDefaultCurrency());
        BigDecimal convertedAmount = request.getAmount().multiply(exchangeRate).setScale(4, java.math.RoundingMode.HALF_UP);

        existing.setCategoryId(request.getCategoryId());
        existing.setAccountId(request.getAccountId());
        existing.setAmount(request.getAmount());
        existing.setCurrency(request.getCurrency().toUpperCase());
        existing.setConvertedAmount(convertedAmount);
        existing.setConvertedCurrency(user.getDefaultCurrency());
        existing.setExchangeRate(exchangeRate);
        existing.setType(request.getType());
        existing.setNotes(request.getNotes());
        existing.setDate(request.getDate());

        // Handle Recurrence Rule updates
        Long currentRecurrenceRuleId = existing.getRecurrenceRuleId();
        if (currentRecurrenceRuleId != null) {
            if (request.getRecurrenceRule() != null) {
                // Update existing recurrence rule
                var ruleReq = request.getRecurrenceRule();
                RecurrenceRule rule = recurrenceRuleRepository.findById(currentRecurrenceRuleId)
                        .orElseThrow(() -> new ResourceNotFoundException("Recurrence rule not found with ID: " + currentRecurrenceRuleId));
                rule.setFrequency(ruleReq.getFrequency());
                rule.setInterval(ruleReq.getInterval());
                rule.setStartDate(ruleReq.getStartDate());
                rule.setEndDate(ruleReq.getEndDate());
                recurrenceRuleRepository.save(rule);
            } else {
                // Remove recurrence rule association
                existing.setRecurrenceRuleId(null);
                recurrenceRuleRepository.deleteById(currentRecurrenceRuleId);
            }
        } else {
            if (request.getRecurrenceRule() != null) {
                // Create a new recurrence rule
                var ruleReq = request.getRecurrenceRule();
                RecurrenceRule rule = RecurrenceRule.builder()
                        .frequency(ruleReq.getFrequency())
                        .interval(ruleReq.getInterval())
                        .startDate(ruleReq.getStartDate())
                        .endDate(ruleReq.getEndDate())
                        .build();
                rule = recurrenceRuleRepository.save(rule);
                existing.setRecurrenceRuleId(rule.getId());
            }
        }

        Transaction updated = transactionRepository.save(existing);

        // 3. Apply the new transaction impact on the new account
        if (newAccount != null) {
            BigDecimal newAccountAmount = currencyExchangeService.convert(request.getAmount(), request.getCurrency(), newAccount.getCurrency());
            adjustAccountBalance(newAccount, newAccountAmount, request.getType());
        }

        // Reconcile savings goals if applicable
        if (oldType == CategoryType.SAVINGS) {
            savingsGoalService.reconcileAllGoalsForCategory(user.getId(), oldCategoryId);
        }
        if (request.getType() == CategoryType.SAVINGS && (oldType != CategoryType.SAVINGS || !request.getCategoryId().equals(oldCategoryId))) {
            savingsGoalService.reconcileAllGoalsForCategory(user.getId(), request.getCategoryId());
        }

        if (updated.getRecurrenceRuleId() != null && request.getRecurrenceRule() != null) {
            RecurrenceRule rule = recurrenceRuleRepository.findById(updated.getRecurrenceRuleId()).orElse(null);
            if (rule != null) {
                recurringTransactionEngine.processRule(rule, LocalDate.now());
            }
        }

        return mapToDto(updated);
    }

    @Transactional
    public void deleteTransaction(Long transactionId, String mode, User user) {
        Transaction existing = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));
        if (!existing.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have permission to delete this transaction");
        }

        Long recurrenceRuleId = existing.getRecurrenceRuleId();
        if (recurrenceRuleId == null || "THIS_ONLY".equalsIgnoreCase(mode)) {
            // Delete just this occurrence
            deleteSingleTransactionInternal(existing);

            // Clean up rule if no other transactions reference it
            if (recurrenceRuleId != null) {
                List<Transaction> remaining = transactionRepository.findByRecurrenceRuleId(recurrenceRuleId);
                if (remaining.isEmpty()) {
                    recurrenceRuleRepository.deleteById(recurrenceRuleId);
                }
            }
        } else if ("ALL".equalsIgnoreCase(mode)) {
            // Delete all occurrences
            List<Transaction> siblings = transactionRepository.findByRecurrenceRuleId(recurrenceRuleId);
            for (Transaction tx : siblings) {
                deleteSingleTransactionInternal(tx);
            }
            recurrenceRuleRepository.deleteById(recurrenceRuleId);
        } else if ("FUTURE".equalsIgnoreCase(mode)) {
            // Delete this and future occurrences
            List<Transaction> siblings = transactionRepository.findByRecurrenceRuleId(recurrenceRuleId);
            LocalDateTime targetDate = existing.getDate();

            for (Transaction tx : siblings) {
                if (!tx.getDate().isBefore(targetDate)) {
                    deleteSingleTransactionInternal(tx);
                }
            }

            // End the recurrence rule on the day before the deleted occurrence
            RecurrenceRule rule = recurrenceRuleRepository.findById(recurrenceRuleId).orElse(null);
            if (rule != null) {
                LocalDate newEndDate = targetDate.toLocalDate().minusDays(1);
                if (newEndDate.isBefore(rule.getStartDate())) {
                    // If the new end date is before the start date, delete the rule if no other transactions remain
                    List<Transaction> remaining = transactionRepository.findByRecurrenceRuleId(recurrenceRuleId);
                    if (remaining.isEmpty()) {
                        recurrenceRuleRepository.deleteById(recurrenceRuleId);
                    } else {
                        rule.setEndDate(rule.getStartDate());
                        recurrenceRuleRepository.save(rule);
                    }
                } else {
                    rule.setEndDate(newEndDate);
                    recurrenceRuleRepository.save(rule);
                }
            }
        }
    }

    private void deleteSingleTransactionInternal(Transaction tx) {
        if (tx.getAccountId() != null) {
            Account account = accountRepository.findById(tx.getAccountId()).orElse(null);
            if (account != null) {
                BigDecimal accountAmount = currencyExchangeService.convert(tx.getAmount(), tx.getCurrency(), account.getCurrency());
                reverseAccountBalance(account, accountAmount, tx.getType());
            }
        }
        
        Long linkedId = tx.getLinkedTransactionId();
        if (linkedId != null) {
            // Clear links first to break recursion
            transactionRepository.clearLink(tx.getId());
            transactionRepository.clearLink(linkedId);
            
            // Delete the other transaction
            transactionRepository.findById(linkedId).ifPresent(this::deleteSingleTransactionInternal);
        }

        transactionRepository.deleteById(tx.getId());
        if (tx.getType() == CategoryType.SAVINGS) {
            savingsGoalService.reconcileAllGoalsForCategory(tx.getUserId(), tx.getCategoryId());
        }
    }

    private void adjustAccountBalance(Account account, BigDecimal amount, CategoryType type) {
        if (type == CategoryType.INCOME) {
            account.setBalance(account.getBalance().add(amount));
        } else if (type == CategoryType.EXPENSE) {
            account.setBalance(account.getBalance().subtract(amount));
        } else if (type == CategoryType.SAVINGS) {
            if (account.getType() == AccountType.SAVINGS) {
                account.setBalance(account.getBalance().add(amount));
            } else {
                account.setBalance(account.getBalance().subtract(amount));
            }
        }
        accountRepository.save(account);
    }

    private void reverseAccountBalance(Account account, BigDecimal amount, CategoryType type) {
        if (type == CategoryType.INCOME) {
            account.setBalance(account.getBalance().subtract(amount));
        } else if (type == CategoryType.EXPENSE) {
            account.setBalance(account.getBalance().add(amount));
        } else if (type == CategoryType.SAVINGS) {
            if (account.getType() == AccountType.SAVINGS) {
                account.setBalance(account.getBalance().subtract(amount));
            } else {
                account.setBalance(account.getBalance().add(amount));
            }
        }
        accountRepository.save(account);
    }

    public TransactionDto mapToDto(Transaction transaction) {
        if (transaction == null) {
            return null;
        }
        RecurrenceRuleDto ruleDto = null;
        if (transaction.getRecurrenceRuleId() != null) {
            ruleDto = recurrenceRuleRepository.findById(transaction.getRecurrenceRuleId())
                    .map(rule -> RecurrenceRuleDto.builder()
                            .id(rule.getId())
                            .frequency(rule.getFrequency())
                            .interval(rule.getInterval())
                            .startDate(rule.getStartDate())
                            .endDate(rule.getEndDate())
                            .createdAt(rule.getCreatedAt())
                            .updatedAt(rule.getUpdatedAt())
                            .build())
                    .orElse(null);
        }

        String mappedType = transaction.getType() != null ? transaction.getType().name() : null;
        Long fromAccountId = null;
        Long toAccountId = null;

        if (transaction.getLinkedTransactionId() != null) {
            mappedType = "MOVE";
            Transaction linked = transactionRepository.findById(transaction.getLinkedTransactionId()).orElse(null);
            if (linked != null) {
                if (transaction.getType() == CategoryType.EXPENSE) {
                    fromAccountId = transaction.getAccountId();
                    toAccountId = linked.getAccountId();
                } else {
                    fromAccountId = linked.getAccountId();
                    toAccountId = transaction.getAccountId();
                }
            }
        }

        return TransactionDto.builder()
                .id(transaction.getId())
                .categoryId(transaction.getCategoryId())
                .accountId(transaction.getAccountId())
                .recurrenceRuleId(transaction.getRecurrenceRuleId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .convertedAmount(transaction.getConvertedAmount())
                .exchangeRate(transaction.getExchangeRate())
                .type(mappedType)
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .notes(transaction.getNotes())
                .date(transaction.getDate())
                .linkedTransactionId(transaction.getLinkedTransactionId())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .recurrenceRule(ruleDto)
                .build();
    }
}

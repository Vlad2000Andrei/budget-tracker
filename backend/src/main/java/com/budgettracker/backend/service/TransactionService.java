package com.budgettracker.backend.service;

import com.budgettracker.backend.dto.CreateTransactionRequest;
import com.budgettracker.backend.dto.TransactionDto;
import com.budgettracker.backend.dto.UpdateTransactionRequest;
import com.budgettracker.backend.exception.ForbiddenActionException;
import com.budgettracker.backend.exception.ResourceNotFoundException;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.model.Account;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.Transaction;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.AccountRepository;
import com.budgettracker.backend.repository.CategoryRepository;
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

    @Autowired
    public TransactionService(TransactionRepository transactionRepository,
                              CategoryRepository categoryRepository,
                              AccountRepository accountRepository,
                              CurrencyExchangeService currencyExchangeService,
                              SavingsGoalService savingsGoalService) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.currencyExchangeService = currencyExchangeService;
        this.savingsGoalService = savingsGoalService;
    }

    public List<TransactionDto> getTransactions(User user, Long accountId, Long categoryId,
                                                LocalDate startDate, LocalDate endDate, CategoryType type) {
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

        return transactionRepository.findAll(user.getId(), accountId, categoryId, startDate, endDate, type)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
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

        // Save Transaction
        Transaction transaction = Transaction.builder()
                .userId(user.getId())
                .categoryId(request.getCategoryId())
                .accountId(request.getAccountId())
                .amount(request.getAmount())
                .currency(request.getCurrency().toUpperCase())
                .convertedAmount(convertedAmount)
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

        return mapToDto(saved);
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
        existing.setExchangeRate(exchangeRate);
        existing.setType(request.getType());
        existing.setNotes(request.getNotes());
        existing.setDate(request.getDate());

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

        return mapToDto(updated);
    }

    @Transactional
    public void deleteTransaction(Long transactionId, User user) {
        Transaction existing = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));
        if (!existing.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have permission to delete this transaction");
        }

        // Reverse the transaction impact on the account
        if (existing.getAccountId() != null) {
            Account account = accountRepository.findById(existing.getAccountId()).orElse(null);
            if (account != null) {
                BigDecimal accountAmount = currencyExchangeService.convert(existing.getAmount(), existing.getCurrency(), account.getCurrency());
                reverseAccountBalance(account, accountAmount, existing.getType());
            }
        }

        transactionRepository.deleteById(transactionId);

        // Reconcile savings goal if applicable
        if (existing.getType() == CategoryType.SAVINGS) {
            savingsGoalService.reconcileAllGoalsForCategory(user.getId(), existing.getCategoryId());
        }
    }

    private void adjustAccountBalance(Account account, BigDecimal amount, CategoryType type) {
        if (type == CategoryType.INCOME) {
            account.setBalance(account.getBalance().add(amount));
        } else { // EXPENSE or SAVINGS
            account.setBalance(account.getBalance().subtract(amount));
        }
        accountRepository.save(account);
    }

    private void reverseAccountBalance(Account account, BigDecimal amount, CategoryType type) {
        if (type == CategoryType.INCOME) {
            account.setBalance(account.getBalance().subtract(amount));
        } else { // EXPENSE or SAVINGS
            account.setBalance(account.getBalance().add(amount));
        }
        accountRepository.save(account);
    }

    public TransactionDto mapToDto(Transaction transaction) {
        if (transaction == null) {
            return null;
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
                .type(transaction.getType())
                .notes(transaction.getNotes())
                .date(transaction.getDate())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}

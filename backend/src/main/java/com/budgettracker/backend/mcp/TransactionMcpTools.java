package com.budgettracker.backend.mcp;

import com.budgettracker.backend.dto.CreateTransactionRequest;
import com.budgettracker.backend.dto.CreateTransferRequest;
import com.budgettracker.backend.dto.TransactionDto;
import com.budgettracker.backend.dto.UpdateTransactionRequest;
import com.budgettracker.backend.exception.ForbiddenActionException;
import com.budgettracker.backend.exception.ResourceNotFoundException;
import com.budgettracker.backend.jooq.enums.AccountType;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.model.Account;
import com.budgettracker.backend.model.Transaction;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.AccountRepository;
import com.budgettracker.backend.repository.TransactionRepository;
import com.budgettracker.backend.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class TransactionMcpTools {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final McpSecurityContextHelper securityHelper;

    @Autowired
    public TransactionMcpTools(TransactionService transactionService,
                               TransactionRepository transactionRepository,
                               AccountRepository accountRepository,
                               McpSecurityContextHelper securityHelper) {
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.securityHelper = securityHelper;
    }

    @Tool(name = "list_transactions", description = "List transactions for the authenticated user, optionally filtered by accountId, categoryId, date range, or transaction type (INCOME, EXPENSE, TRANSFER).")
    public List<TransactionDto> listTransactions(
            @ToolParam(description = "Optional account ID to filter transactions by", required = false) Long accountId,
            @ToolParam(description = "Optional category ID to filter transactions by", required = false) Long categoryId,
            @ToolParam(description = "Optional start date in YYYY-MM-DD format", required = false) String startDate,
            @ToolParam(description = "Optional end date in YYYY-MM-DD format", required = false) String endDate,
            @ToolParam(description = "Optional transaction type: INCOME, EXPENSE, or TRANSFER", required = false) String type) {

        User user = securityHelper.getCurrentUser();
        LocalDate start = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate.trim()) : null;
        LocalDate end = (endDate != null && !endDate.isBlank()) ? LocalDate.parse(endDate.trim()) : null;
        return transactionService.getTransactions(user, accountId, categoryId, start, end, type);
    }

    @Tool(name = "get_transaction", description = "Get details of a specific transaction by its ID. Fails if the transaction does not belong to the user.")
    public TransactionDto getTransaction(
            @ToolParam(description = "The ID of the transaction to retrieve", required = true) Long transactionId) {

        User user = securityHelper.getCurrentUser();
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));
        if (!tx.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have access to this transaction");
        }
        return transactionService.mapToDto(tx);
    }

    @Tool(name = "create_transaction", description = "Create a new income or expense transaction. If accountId is omitted, it automatically defaults to the user's primary financial account.")
    public TransactionDto createTransaction(
            @ToolParam(description = "Transaction amount (must be positive, e.g. 25.50)", required = true) BigDecimal amount,
            @ToolParam(description = "Transaction type: INCOME or EXPENSE", required = true) String type,
            @ToolParam(description = "Category ID to categorize this transaction", required = true) Long categoryId,
            @ToolParam(description = "Optional 3-letter currency code (e.g. USD, EUR). Defaults to user's currency.", required = false) String currency,
            @ToolParam(description = "Optional financial account ID. If omitted, automatically routes to default account.", required = false) Long accountId,
            @ToolParam(description = "Optional transaction date-time (ISO format, e.g. 2026-11-07T14:30:00). Defaults to now.", required = false) String date,
            @ToolParam(description = "Optional description or note for the transaction", required = false) String note) {

        User user = securityHelper.getCurrentUser();
        Long finalAccountId = accountId != null ? accountId : resolveDefaultAccountId(user);
        String finalCurrency = (currency != null && !currency.isBlank())
                ? currency.trim().toUpperCase()
                : (user.getDefaultCurrency() != null ? user.getDefaultCurrency() : "USD");
        LocalDateTime txDate = (date != null && !date.isBlank()) ? LocalDateTime.parse(date.trim()) : LocalDateTime.now();
        CategoryType catType = CategoryType.valueOf(type.trim().toUpperCase());

        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .accountId(finalAccountId)
                .categoryId(categoryId)
                .amount(amount)
                .currency(finalCurrency)
                .type(catType)
                .date(txDate)
                .notes(note)
                .build();

        return transactionService.createTransaction(request, user);
    }

    @Tool(name = "create_transfer", description = "Transfer money between two accounts belonging to the user.")
    public TransactionDto createTransfer(
            @ToolParam(description = "Source account ID", required = true) Long fromAccountId,
            @ToolParam(description = "Destination account ID", required = true) Long toAccountId,
            @ToolParam(description = "Transfer amount (must be positive)", required = true) BigDecimal amount,
            @ToolParam(description = "3-letter currency code (e.g. USD, EUR)", required = true) String currency,
            @ToolParam(description = "Optional transfer date-time (ISO format). Defaults to now.", required = false) String date,
            @ToolParam(description = "Optional transfer note", required = false) String note) {

        User user = securityHelper.getCurrentUser();
        LocalDateTime transferDate = (date != null && !date.isBlank()) ? LocalDateTime.parse(date.trim()) : LocalDateTime.now();

        CreateTransferRequest request = CreateTransferRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(amount)
                .currency(currency.trim().toUpperCase())
                .date(transferDate)
                .notes(note)
                .build();

        return transactionService.createTransfer(request, user);
    }

    @Tool(name = "update_transaction", description = "Update an existing transaction details.")
    public TransactionDto updateTransaction(
            @ToolParam(description = "The ID of the transaction to update", required = true) Long transactionId,
            @ToolParam(description = "Updated amount", required = true) BigDecimal amount,
            @ToolParam(description = "Updated 3-letter currency code", required = true) String currency,
            @ToolParam(description = "Updated type: INCOME or EXPENSE", required = true) String type,
            @ToolParam(description = "Updated category ID", required = true) Long categoryId,
            @ToolParam(description = "Optional updated account ID", required = false) Long accountId,
            @ToolParam(description = "Optional updated date-time (ISO format)", required = false) String date,
            @ToolParam(description = "Optional updated note", required = false) String note) {

        User user = securityHelper.getCurrentUser();
        LocalDateTime txDate = (date != null && !date.isBlank()) ? LocalDateTime.parse(date.trim()) : LocalDateTime.now();
        CategoryType catType = CategoryType.valueOf(type.trim().toUpperCase());

        UpdateTransactionRequest request = UpdateTransactionRequest.builder()
                .accountId(accountId)
                .categoryId(categoryId)
                .amount(amount)
                .currency(currency.trim().toUpperCase())
                .type(catType)
                .date(txDate)
                .notes(note)
                .build();

        return transactionService.updateTransaction(transactionId, request, user);
    }

    @Tool(name = "delete_transaction", description = "Delete a transaction by ID. For recurring transactions, mode can be THIS_ONLY, FUTURE, or ALL.")
    public String deleteTransaction(
            @ToolParam(description = "Transaction ID to delete", required = true) Long transactionId,
            @ToolParam(description = "Deletion mode for recurring transactions: THIS_ONLY (default), FUTURE, or ALL", required = false) String mode) {

        User user = securityHelper.getCurrentUser();
        String effectiveMode = (mode != null && !mode.isBlank()) ? mode.trim().toUpperCase() : "THIS_ONLY";
        transactionService.deleteTransaction(transactionId, effectiveMode, user);
        return "Transaction " + transactionId + " successfully deleted (mode: " + effectiveMode + ")";
    }

    private Long resolveDefaultAccountId(User user) {
        List<Account> accounts = accountRepository.findByUserId(user.getId());
        if (!accounts.isEmpty()) {
            return accounts.get(0).getId();
        }
        Account defaultAcc = accountRepository.save(Account.builder()
                .userId(user.getId())
                .name("Main Account")
                .type(AccountType.CHECKING)
                .balance(BigDecimal.ZERO.setScale(4))
                .currency(user.getDefaultCurrency() != null ? user.getDefaultCurrency() : "USD")
                .build());
        return defaultAcc.getId();
    }
}

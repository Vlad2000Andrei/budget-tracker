package com.budgettracker.backend.service;

import com.budgettracker.backend.dto.AccountDto;
import com.budgettracker.backend.dto.CreateAccountRequest;
import com.budgettracker.backend.dto.UpdateAccountRequest;
import com.budgettracker.backend.exception.ForbiddenActionException;
import com.budgettracker.backend.exception.ResourceNotFoundException;
import com.budgettracker.backend.model.Account;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    @Autowired
    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public List<AccountDto> getAccounts(User user) {
        return accountRepository.findByUserId(user.getId())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AccountDto createAccount(CreateAccountRequest request, User user) {
        BigDecimal balance = request.getInitialBalance() != null
                ? request.getInitialBalance().setScale(4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4);

        Account account = Account.builder()
                .userId(user.getId())
                .name(request.getName())
                .type(request.getType())
                .balance(balance)
                .currency(request.getCurrency().toUpperCase())
                .build();

        Account saved = accountRepository.save(account);
        return mapToDto(saved);
    }

    @Transactional
    public AccountDto updateAccount(Long accountId, UpdateAccountRequest request, User user) {
        Account existing = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        if (!existing.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have permission to modify this account");
        }
        existing.setName(request.getName());
        if (request.getBalance() != null) {
            existing.setBalance(request.getBalance().setScale(4, java.math.RoundingMode.HALF_UP));
        }
        Account updated = accountRepository.save(existing);
        return mapToDto(updated);
    }

    @Transactional
    public void deleteAccount(Long accountId, User user) {
        Account existing = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        if (!existing.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have permission to delete this account");
        }

        accountRepository.deleteById(accountId);
    }

    public AccountDto mapToDto(Account account) {
        if (account == null) {
            return null;
        }
        return AccountDto.builder()
                .id(account.getId())
                .name(account.getName())
                .type(account.getType())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}

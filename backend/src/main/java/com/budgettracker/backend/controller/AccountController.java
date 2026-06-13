package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.AccountDto;
import com.budgettracker.backend.dto.CreateAccountRequest;
import com.budgettracker.backend.dto.UpdateAccountRequest;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    @Autowired
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<List<AccountDto>> getAccounts(User user) {
        List<AccountDto> accounts = accountService.getAccounts(user);
        return ResponseEntity.ok(accounts);
    }

    @PostMapping
    public ResponseEntity<AccountDto> createAccount(@Valid @RequestBody CreateAccountRequest request, User user) {
        AccountDto created = accountService.createAccount(request, user);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{accountId}")
    public ResponseEntity<AccountDto> updateAccount(@PathVariable Long accountId,
                                                    @Valid @RequestBody UpdateAccountRequest request,
                                                    User user) {
        AccountDto updated = accountService.updateAccount(accountId, request, user);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long accountId, User user) {
        accountService.deleteAccount(accountId, user);
        return ResponseEntity.noContent().build();
    }
}

package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.DuplicateCheckRequest;
import com.budgettracker.backend.dto.DuplicateCheckResponse;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/imports")
public class ImportController {

    private final TransactionService transactionService;

    @Autowired
    public ImportController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/detect-duplicates")
    public ResponseEntity<DuplicateCheckResponse> detectDuplicates(
            @Valid @RequestBody DuplicateCheckRequest request, User user) {
        DuplicateCheckResponse response = transactionService.detectDuplicates(request, user);
        return ResponseEntity.ok(response);
    }
}

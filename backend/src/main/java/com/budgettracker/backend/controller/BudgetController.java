package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.BudgetDto;
import com.budgettracker.backend.dto.CreateBudgetRequest;
import com.budgettracker.backend.dto.UpdateBudgetRequest;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.service.BudgetService;
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
@RequestMapping("/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    @Autowired
    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public ResponseEntity<List<BudgetDto>> getBudgets(User user) {
        List<BudgetDto> budgets = budgetService.getBudgets(user);
        return ResponseEntity.ok(budgets);
    }

    @PostMapping
    public ResponseEntity<BudgetDto> createBudget(@Valid @RequestBody CreateBudgetRequest request, User user) {
        BudgetDto created = budgetService.createBudget(request, user);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{budgetId}")
    public ResponseEntity<BudgetDto> updateBudget(@PathVariable Long budgetId,
                                                 @Valid @RequestBody UpdateBudgetRequest request,
                                                 User user) {
        BudgetDto updated = budgetService.updateBudget(budgetId, request, user);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{budgetId}")
    public ResponseEntity<Void> deleteBudget(@PathVariable Long budgetId, User user) {
        budgetService.deleteBudget(budgetId, user);
        return ResponseEntity.noContent().build();
    }
}

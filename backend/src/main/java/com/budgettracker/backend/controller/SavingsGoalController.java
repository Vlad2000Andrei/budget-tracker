package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.CreateSavingsGoalRequest;
import com.budgettracker.backend.dto.SavingsGoalDto;
import com.budgettracker.backend.dto.UpdateSavingsGoalRequest;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.service.SavingsGoalService;
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
@RequestMapping("/v1/savings-goals")
public class SavingsGoalController {

    private final SavingsGoalService savingsGoalService;

    @Autowired
    public SavingsGoalController(SavingsGoalService savingsGoalService) {
        this.savingsGoalService = savingsGoalService;
    }

    @GetMapping
    public ResponseEntity<List<SavingsGoalDto>> getSavingsGoals(User user) {
        List<SavingsGoalDto> goals = savingsGoalService.getSavingsGoals(user);
        return ResponseEntity.ok(goals);
    }

    @PostMapping
    public ResponseEntity<SavingsGoalDto> createSavingsGoal(@Valid @RequestBody CreateSavingsGoalRequest request, User user) {
        SavingsGoalDto created = savingsGoalService.createSavingsGoal(request, user);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{goalId}")
    public ResponseEntity<SavingsGoalDto> updateSavingsGoal(@PathVariable Long goalId,
                                                           @Valid @RequestBody UpdateSavingsGoalRequest request,
                                                           User user) {
        SavingsGoalDto updated = savingsGoalService.updateSavingsGoal(goalId, request, user);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{goalId}")
    public ResponseEntity<Void> deleteSavingsGoal(@PathVariable Long goalId, User user) {
        savingsGoalService.deleteSavingsGoal(goalId, user);
        return ResponseEntity.noContent().build();
    }
}

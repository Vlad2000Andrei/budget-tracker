package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.RecurringTransactionDto;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.service.RecurrenceRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/recurrence-rules")
public class RecurrenceRuleController {

    private final RecurrenceRuleService recurrenceRuleService;

    @Autowired
    public RecurrenceRuleController(RecurrenceRuleService recurrenceRuleService) {
        this.recurrenceRuleService = recurrenceRuleService;
    }

    @GetMapping
    public ResponseEntity<List<RecurringTransactionDto>> getRecurringTransactions(User user) {
        List<RecurringTransactionDto> list = recurrenceRuleService.getRecurringTransactionsForUser(user);
        return ResponseEntity.ok(list);
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteRecurrenceRule(@PathVariable Long ruleId, User user) {
        recurrenceRuleService.deleteRecurrenceRule(ruleId, user);
        return ResponseEntity.noContent().build();
    }
}

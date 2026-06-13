package com.budgettracker.backend.service;

import com.budgettracker.backend.dto.BudgetDto;
import com.budgettracker.backend.dto.CreateBudgetRequest;
import com.budgettracker.backend.dto.UpdateBudgetRequest;
import com.budgettracker.backend.exception.ForbiddenActionException;
import com.budgettracker.backend.exception.ResourceNotFoundException;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.enums.RolloverRuleType;
import com.budgettracker.backend.model.Budget;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.BudgetRepository;
import com.budgettracker.backend.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;

    @Autowired
    public BudgetService(BudgetRepository budgetRepository, CategoryRepository categoryRepository) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<BudgetDto> getBudgets(User user) {
        return budgetRepository.findByUserId(user.getId())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public BudgetDto createBudget(CreateBudgetRequest request, User user) {
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));

        // Check ownership of the category
        if (category.getUserId() != null && !category.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have access to the specified category");
        }

        // Validate type is EXPENSE
        if (category.getType() != CategoryType.EXPENSE) {
            throw new IllegalArgumentException("Budgets can only be set on categories of type EXPENSE");
        }

        // Check for overlaps
        if (budgetRepository.hasOverlappingBudget(user.getId(), request.getCategoryId(), request.getStartDate(), request.getEndDate(), null)) {
            throw new IllegalArgumentException("An overlapping budget already exists for this category and time period");
        }

        Budget budget = Budget.builder()
                .userId(user.getId())
                .categoryId(request.getCategoryId())
                .amountLimit(request.getAmountLimit())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .rolloverRule(request.getRolloverRule() != null ? request.getRolloverRule() : RolloverRuleType.NONE)
                .build();

        Budget saved = budgetRepository.save(budget);
        return mapToDto(saved);
    }

    @Transactional
    public BudgetDto updateBudget(Long budgetId, UpdateBudgetRequest request, User user) {
        Budget existing = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found with ID: " + budgetId));

        // Ownership check
        if (!existing.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have permission to modify this budget");
        }

        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));

        // Check ownership of the category
        if (category.getUserId() != null && !category.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have access to the specified category");
        }

        // Validate type is EXPENSE
        if (category.getType() != CategoryType.EXPENSE) {
            throw new IllegalArgumentException("Budgets can only be set on categories of type EXPENSE");
        }

        // Check for overlaps
        if (budgetRepository.hasOverlappingBudget(user.getId(), request.getCategoryId(), request.getStartDate(), request.getEndDate(), budgetId)) {
            throw new IllegalArgumentException("An overlapping budget already exists for this category and time period");
        }

        existing.setCategoryId(request.getCategoryId());
        existing.setAmountLimit(request.getAmountLimit());
        existing.setStartDate(request.getStartDate());
        existing.setEndDate(request.getEndDate());
        existing.setRolloverRule(request.getRolloverRule() != null ? request.getRolloverRule() : RolloverRuleType.NONE);

        Budget updated = budgetRepository.save(existing);
        return mapToDto(updated);
    }

    @Transactional
    public void deleteBudget(Long budgetId, User user) {
        Budget existing = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found with ID: " + budgetId));

        // Ownership check
        if (!existing.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have permission to delete this budget");
        }

        budgetRepository.deleteById(budgetId);
    }

    private BudgetDto mapToDto(Budget budget) {
        if (budget == null) {
            return null;
        }
        return BudgetDto.builder()
                .id(budget.getId())
                .userId(budget.getUserId())
                .categoryId(budget.getCategoryId())
                .amountLimit(budget.getAmountLimit())
                .startDate(budget.getStartDate())
                .endDate(budget.getEndDate())
                .rolloverRule(budget.getRolloverRule())
                .createdAt(budget.getCreatedAt())
                .updatedAt(budget.getUpdatedAt())
                .build();
    }
}

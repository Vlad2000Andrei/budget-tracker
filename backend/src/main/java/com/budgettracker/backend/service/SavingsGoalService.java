package com.budgettracker.backend.service;

import com.budgettracker.backend.dto.CreateSavingsGoalRequest;
import com.budgettracker.backend.dto.SavingsGoalDto;
import com.budgettracker.backend.dto.UpdateSavingsGoalRequest;
import com.budgettracker.backend.exception.ForbiddenActionException;
import com.budgettracker.backend.exception.ResourceNotFoundException;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.SavingsGoal;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.CategoryRepository;
import com.budgettracker.backend.repository.SavingsGoalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SavingsGoalService {

    private final SavingsGoalRepository savingsGoalRepository;
    private final CategoryRepository categoryRepository;

    @Autowired
    public SavingsGoalService(SavingsGoalRepository savingsGoalRepository, CategoryRepository categoryRepository) {
        this.savingsGoalRepository = savingsGoalRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<SavingsGoalDto> getSavingsGoals(User user) {
        return savingsGoalRepository.findByUserId(user.getId())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SavingsGoalDto createSavingsGoal(CreateSavingsGoalRequest request, User user) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));

        // Check ownership of the category
        if (category.getUserId() != null && !category.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have access to the specified category");
        }

        // Validate type is SAVINGS
        if (category.getType() != CategoryType.SAVINGS) {
            throw new IllegalArgumentException("Savings goals can only be set on categories of type SAVINGS");
        }

        // Calculate initial accumulated amount
        List<Long> descendants = categoryRepository.getDescendantCategoryIds(request.getCategoryId());
        BigDecimal initialAccumulated = savingsGoalRepository.calculateAccumulatedSavings(user.getId(), descendants);

        SavingsGoal goal = SavingsGoal.builder()
                .userId(user.getId())
                .categoryId(request.getCategoryId())
                .targetAmount(request.getTargetAmount())
                .currentAmount(initialAccumulated)
                .targetDate(request.getTargetDate())
                .build();

        SavingsGoal saved = savingsGoalRepository.save(goal);
        return mapToDto(saved);
    }

    @Transactional
    public SavingsGoalDto updateSavingsGoal(Long goalId, UpdateSavingsGoalRequest request, User user) {
        SavingsGoal existing = savingsGoalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Savings goal not found with ID: " + goalId));

        // Ownership check
        if (!existing.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have permission to modify this savings goal");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));

        // Check ownership of the category
        if (category.getUserId() != null && !category.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have access to the specified category");
        }

        // Validate type is SAVINGS
        if (category.getType() != CategoryType.SAVINGS) {
            throw new IllegalArgumentException("Savings goals can only be set on categories of type SAVINGS");
        }

        // Recalculate accumulated amount in case category changed
        List<Long> descendants = categoryRepository.getDescendantCategoryIds(request.getCategoryId());
        BigDecimal accumulated = savingsGoalRepository.calculateAccumulatedSavings(user.getId(), descendants);

        existing.setCategoryId(request.getCategoryId());
        existing.setTargetAmount(request.getTargetAmount());
        existing.setCurrentAmount(accumulated);
        existing.setTargetDate(request.getTargetDate());

        SavingsGoal updated = savingsGoalRepository.save(existing);
        return mapToDto(updated);
    }

    @Transactional
    public void deleteSavingsGoal(Long goalId, User user) {
        SavingsGoal existing = savingsGoalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Savings goal not found with ID: " + goalId));

        // Ownership check
        if (!existing.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have permission to delete this savings goal");
        }

        savingsGoalRepository.deleteById(goalId);
    }

    @Transactional
    public void reconcileGoalAmount(Long userId, Long categoryId) {
        List<SavingsGoal> goals = savingsGoalRepository.findByCategoryId(categoryId)
                .stream()
                .filter(g -> g.getUserId().equals(userId))
                .collect(Collectors.toList());

        if (goals.isEmpty()) {
            return;
        }

        List<Long> descendants = categoryRepository.getDescendantCategoryIds(categoryId);
        BigDecimal sum = savingsGoalRepository.calculateAccumulatedSavings(userId, descendants);

        for (SavingsGoal goal : goals) {
            goal.setCurrentAmount(sum);
            savingsGoalRepository.save(goal);
        }
    }

    @Transactional
    public void reconcileAllGoalsForCategory(Long userId, Long categoryId) {
        Long currentCategoryId = categoryId;
        int depth = 0;
        while (currentCategoryId != null && depth < 100) {
            reconcileGoalAmount(userId, currentCategoryId);
            Category cat = categoryRepository.findById(currentCategoryId).orElse(null);
            currentCategoryId = (cat != null) ? cat.getParentId() : null;
            depth++;
        }
    }

    private SavingsGoalDto mapToDto(SavingsGoal goal) {
        if (goal == null) {
            return null;
        }
        return SavingsGoalDto.builder()
                .id(goal.getId())
                .userId(goal.getUserId())
                .categoryId(goal.getCategoryId())
                .targetAmount(goal.getTargetAmount())
                .currentAmount(goal.getCurrentAmount())
                .targetDate(goal.getTargetDate())
                .createdAt(goal.getCreatedAt())
                .updatedAt(goal.getUpdatedAt())
                .build();
    }
}

package com.budgettracker.backend.service;

import com.budgettracker.backend.dto.CategoryDto;
import com.budgettracker.backend.dto.CreateCategoryRequest;
import com.budgettracker.backend.dto.UpdateCategoryRequest;
import com.budgettracker.backend.exception.CategoryInUseException;
import com.budgettracker.backend.exception.ForbiddenActionException;
import com.budgettracker.backend.exception.ResourceNotFoundException;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;


@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Autowired
    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryDto> getCategories(User user) {
        List<Category> categories = categoryRepository.findByUserIdAndSystemWide(user.getId());
        return categories.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryDto createCategory(CreateCategoryRequest request, User user) {
        if (request.getParentId() != null) {
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found with ID: " + request.getParentId()));
            
            // Check ownership of parent category
            if (parent.getUserId() != null && !parent.getUserId().equals(user.getId())) {
                throw new ForbiddenActionException("You do not have access to the specified parent category");
            }

            // Check type matching
            if (parent.getType() != request.getType()) {
                throw new IllegalArgumentException("Child category type must match parent category type");
            }
        }

        Category category = Category.builder()
                .userId(user.getId()) // Always user-specific
                .parentId(request.getParentId())
                .name(request.getName())
                .icon(request.getIcon())
                .color(request.getColor())
                .type(request.getType())
                .build();

        Category saved = categoryRepository.save(category);
        return mapToDto(saved);
    }

    @Transactional
    public CategoryDto updateCategory(Long categoryId, UpdateCategoryRequest request, User user) {
        Category existing = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));

        // System-wide categories are read-only for normal users
        if (existing.getUserId() == null) {
            throw new ForbiddenActionException("System-wide default categories cannot be modified");
        }

        // Ensure category ownership
        if (!existing.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have permission to modify this category");
        }

        if (request.getParentId() != null) {
            // Self-parent check
            if (categoryId.equals(request.getParentId())) {
                throw new IllegalArgumentException("A category cannot be its own parent");
            }

            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found with ID: " + request.getParentId()));

            // Check ownership of parent category
            if (parent.getUserId() != null && !parent.getUserId().equals(user.getId())) {
                throw new ForbiddenActionException("You do not have access to the specified parent category");
            }

            // Check type matching
            if (parent.getType() != existing.getType()) {
                throw new IllegalArgumentException("Child category type must match parent category type");
            }

            // Check circular dependency
            if (categoryRepository.hasCircularDependency(categoryId, request.getParentId())) {
                throw new IllegalArgumentException("Circular dependency detected in category hierarchy");
            }
        }

        existing.setName(request.getName());
        existing.setParentId(request.getParentId());
        existing.setIcon(request.getIcon());
        existing.setColor(request.getColor());

        Category updated = categoryRepository.save(existing);
        return mapToDto(updated);
    }

    @Transactional
    public void deleteCategory(Long categoryId, User user) {
        Category existing = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));

        // System-wide categories are read-only
        if (existing.getUserId() == null) {
            throw new ForbiddenActionException("System-wide default categories cannot be deleted");
        }

        // Ensure ownership
        if (!existing.getUserId().equals(user.getId())) {
            throw new ForbiddenActionException("You do not have permission to delete this category");
        }

        // Check if category or any descendants are used by transactions
        if (categoryRepository.hasDescendantTransactions(categoryId)) {
            throw new CategoryInUseException("Cannot delete category because it or one of its subcategories has active transactions");
        }

        categoryRepository.deleteById(categoryId);
    }

    @Transactional
    public void seedDefaultCategories(User user) {
        seedDefaultDataForUser(user);
    }

    @Transactional
    public void seedDefaultDataForUser(User user) {
        // 1. Food Parent (EXPENSE)
        Category food = categoryRepository.save(Category.builder()
                .userId(user.getId())
                .name("Food")
                .type(CategoryType.EXPENSE)
                .color("#FF5733")
                .icon("fastfood")
                .build());

        // Food -> Groceries
        categoryRepository.save(Category.builder()
                .userId(user.getId())
                .parentId(food.getId())
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .color("#FF5733")
                .icon("shopping_cart")
                .build());

        // Food -> Restaurants
        categoryRepository.save(Category.builder()
                .userId(user.getId())
                .parentId(food.getId())
                .name("Restaurants")
                .type(CategoryType.EXPENSE)
                .color("#FF5733")
                .icon("restaurant")
                .build());

        // 2. Utilities (EXPENSE)
        Category utilities = categoryRepository.save(Category.builder()
                .userId(user.getId())
                .name("Utilities")
                .type(CategoryType.EXPENSE)
                .color("#3357FF")
                .icon("power")
                .build());

        // 3. Income Parent (INCOME)
        Category income = categoryRepository.save(Category.builder()
                .userId(user.getId())
                .name("Income")
                .type(CategoryType.INCOME)
                .color("#33FF57")
                .icon("work")
                .build());

        // Income -> Salary
        categoryRepository.save(Category.builder()
                .userId(user.getId())
                .parentId(income.getId())
                .name("Salary")
                .type(CategoryType.INCOME)
                .color("#33FF57")
                .icon("payments")
                .build());

        // 4. Savings (SAVINGS)
        categoryRepository.save(Category.builder()
                .userId(user.getId())
                .name("Savings")
                .type(CategoryType.SAVINGS)
                .color("#F3FF33")
                .icon("savings")
                .build());
    }

    private CategoryDto mapToDto(Category category) {
        if (category == null) {
            return null;
        }
        return CategoryDto.builder()
                .id(category.getId())
                .parentId(category.getParentId())
                .name(category.getName())
                .icon(category.getIcon())
                .color(category.getColor())
                .type(category.getType())
                .systemWide(category.getUserId() == null)
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}

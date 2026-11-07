package com.budgettracker.backend.mcp;

import com.budgettracker.backend.dto.CategoryDto;
import com.budgettracker.backend.dto.CreateCategoryRequest;
import com.budgettracker.backend.dto.UpdateCategoryRequest;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class CategoryMcpTools {

    private final CategoryService categoryService;
    private final McpSecurityContextHelper securityHelper;

    @Autowired
    public CategoryMcpTools(CategoryService categoryService,
                            McpSecurityContextHelper securityHelper) {
        this.categoryService = categoryService;
        this.securityHelper = securityHelper;
    }

    @Tool(name = "list_categories", description = "List all categories (system default and user custom categories) with their hierarchies, icons, and colors.")
    public List<CategoryDto> listCategories() {
        User user = securityHelper.getCurrentUser();
        return categoryService.getCategories(user);
    }

    @Tool(name = "create_category", description = "Create a new custom category for the user.")
    public CategoryDto createCategory(
            @ToolParam(description = "Category name (e.g. 'Coffee', 'Gym')", required = true) String name,
            @ToolParam(description = "Category type: INCOME, EXPENSE, or SAVINGS", required = true) String type,
            @ToolParam(description = "Optional parent category ID to nest this as a subcategory", required = false) Long parentId,
            @ToolParam(description = "Optional icon name (e.g. 'local_cafe', 'fitness_center')", required = false) String icon,
            @ToolParam(description = "Optional hex color code (e.g. '#FF5733')", required = false) String color) {

        User user = securityHelper.getCurrentUser();
        CategoryType catType = CategoryType.valueOf(type.trim().toUpperCase());

        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name(name.trim())
                .type(catType)
                .parentId(parentId)
                .icon(icon)
                .color(color)
                .build();

        return categoryService.createCategory(request, user);
    }

    @Tool(name = "update_category", description = "Update an existing custom category. System categories are protected and cannot be modified.")
    public CategoryDto updateCategory(
            @ToolParam(description = "Category ID to update", required = true) Long categoryId,
            @ToolParam(description = "Updated category name", required = true) String name,
            @ToolParam(description = "Optional updated parent category ID", required = false) Long parentId,
            @ToolParam(description = "Optional updated icon name", required = false) String icon,
            @ToolParam(description = "Optional updated hex color code", required = false) String color) {

        User user = securityHelper.getCurrentUser();
        UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                .name(name.trim())
                .parentId(parentId)
                .icon(icon)
                .color(color)
                .build();

        return categoryService.updateCategory(categoryId, request, user);
    }

    @Tool(name = "delete_category", description = "Delete a custom category. Fails if the category or any subcategories have existing transactions.")
    public String deleteCategory(
            @ToolParam(description = "Category ID to delete", required = true) Long categoryId) {

        User user = securityHelper.getCurrentUser();
        categoryService.deleteCategory(categoryId, user);
        return "Category " + categoryId + " successfully deleted";
    }
}

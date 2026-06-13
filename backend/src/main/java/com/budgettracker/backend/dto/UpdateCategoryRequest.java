package com.budgettracker.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCategoryRequest {

    private Long parentId;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be less than 100 characters")
    private String name;

    @Size(max = 50, message = "Icon name must be less than 50 characters")
    private String icon;

    @Pattern(regexp = "^#([A-Fa-f0-9]{6})$", message = "Color must be a valid 6-character hex color (e.g. #FF5733)")
    private String color;
}

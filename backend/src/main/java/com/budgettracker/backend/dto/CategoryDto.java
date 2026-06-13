package com.budgettracker.backend.dto;

import com.budgettracker.backend.jooq.enums.CategoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDto {
    private Long id;
    private Long parentId;
    private String name;
    private String icon;
    private String color;
    private CategoryType type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

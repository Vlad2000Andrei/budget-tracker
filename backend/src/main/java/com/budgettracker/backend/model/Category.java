package com.budgettracker.backend.model;

import com.budgettracker.backend.jooq.enums.CategoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {
    private Long id;
    private Long userId;     // NULL means system-wide default category
    private Long parentId;   // NULL means top-level category
    private String name;
    private String icon;
    private String color;
    private CategoryType type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

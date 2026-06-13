package com.budgettracker.backend.dto;

import com.budgettracker.backend.jooq.enums.RecurrenceFrequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurrenceRuleDto {
    private Long id;
    private RecurrenceFrequency frequency;
    private int interval;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

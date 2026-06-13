package com.budgettracker.backend.dto;

import com.budgettracker.backend.jooq.enums.RecurrenceFrequency;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRecurrenceRuleRequest {

    @NotNull(message = "Frequency is required")
    private RecurrenceFrequency frequency;

    @Min(value = 1, message = "Interval must be at least 1")
    private int interval = 1;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    private LocalDate endDate;
}

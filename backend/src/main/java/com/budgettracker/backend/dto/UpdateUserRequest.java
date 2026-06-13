package com.budgettracker.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {
    @NotBlank(message = "Default currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Default currency must be a 3-letter uppercase ISO currency code")
    private String defaultCurrency;
}

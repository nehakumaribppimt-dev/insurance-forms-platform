package com.example.forms.api;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record JobRequest(@NotBlank String requestId, @NotBlank String policyNumber, @Min(1) int policyRevision,
                         @NotBlank String productCode, @NotBlank String transactionType, @NotBlank String jurisdiction,
                         @NotNull LocalDate transactionEffectiveDate, @NotBlank String requestedBy) {
}

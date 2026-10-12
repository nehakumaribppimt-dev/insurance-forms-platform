package com.example.forms.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record PolicySnapshot(String policyNumber, int revision, String productCode, String jurisdiction,
                             String insuredName, LocalDate effectiveDate, LocalDate expirationDate,
                             BigDecimal annualPremium, Map<String, String> risk, List<Coverage> coverages,
                             List<String> changedFields) {
    public boolean hasCoverage(String code) {
        return coverages.stream().anyMatch(c -> c.code().equals(code));
    }

    public Coverage coverage(String code) {
        return coverages.stream().filter(c -> c.code().equals(code)).findFirst().orElse(null);
    }

    public record Coverage(String code, BigDecimal limit, BigDecimal deductible) {
    }
}

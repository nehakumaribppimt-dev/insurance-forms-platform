package com.example.forms.service;

import com.example.forms.domain.PolicySnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class MockPolicyClient implements PolicyClient {
    private static PolicySnapshot.Coverage c(String name, int limit, int deductible) {
        return new PolicySnapshot.Coverage(name, BigDecimal.valueOf(limit), BigDecimal.valueOf(deductible));
    }

    @Override
    public PolicySnapshot getRevision(String id, int rev) {
        if (id.equals("H100245") && rev == 1)
            return new PolicySnapshot(id, rev, "HOME", "TX", "Rahul Sharma", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), BigDecimal.valueOf(2400), Map.of("PROPERTY_ADDRESS", "25 Main Street, Dallas, TX 75201", "PROPERTY_TYPE", "SINGLE_FAMILY", "YEAR_BUILT", "2018"), List.of(c("DWELLING", 500000, 2000), c("PERSONAL_PROPERTY", 150000, 2000), c("PERSONAL_LIABILITY", 300000, 0)), List.of());
        if (id.equals("H100245") && rev == 2)
            return new PolicySnapshot(id, rev, "HOME", "TX", "Rahul Sharma", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), BigDecimal.valueOf(2650), Map.of("PROPERTY_ADDRESS", "25 Main Street, Dallas, TX 75201", "PROPERTY_TYPE", "SINGLE_FAMILY", "YEAR_BUILT", "2018", "SCHEDULED_ITEM", "Diamond ring"), List.of(c("DWELLING", 500000, 2000), c("PERSONAL_PROPERTY", 150000, 2000), c("PERSONAL_LIABILITY", 300000, 0), c("SCHEDULED_JEWELRY", 12000, 0)), List.of("COVERAGES"));
        if (id.equals("A200678") && rev == 1)
            return new PolicySnapshot(id, rev, "AUTO", "TX", "Alex Morgan", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), BigDecimal.valueOf(1450), Map.of("VEHICLE_DESCRIPTION", "2025 Honda Civic", "VIN", "1HGCM82633A123456"), List.of(c("BODILY_INJURY", 100000, 0), c("PROPERTY_DAMAGE", 50000, 0), c("COLLISION", 0, 500), c("COMPREHENSIVE", 0, 250)), List.of());
        if (id.equals("A200678") && rev == 2)
            return new PolicySnapshot(id, rev, "AUTO", "TX", "Alex Morgan", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), BigDecimal.valueOf(1580), Map.of("VEHICLE_DESCRIPTION", "2025 Honda Civic; 2024 Toyota Camry", "VIN", "1HGCM82633A123456"), List.of(c("BODILY_INJURY", 100000, 0), c("PROPERTY_DAMAGE", 50000, 0), c("COLLISION", 0, 500), c("COMPREHENSIVE", 0, 250)), List.of("VEHICLES"));
        throw new IllegalArgumentException("POLICY_REVISION_NOT_FOUND: " + id + " revision " + rev);
    }
}

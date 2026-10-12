package com.example.forms.service;

import com.example.forms.domain.PolicySnapshot;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
public class FormCatalog {
    private final List<Version> versions = List.of(
            new Version("HOME_DECLARATIONS", "2025-01", LocalDate.of(2025, 1, 1), null, "home_declarations.odt"),
            new Version("HOME_BASE", "2025-01", LocalDate.of(2025, 1, 1), null, "home_base.odt"),
            new Version("TX_HOME_NOTICE", "2025-01", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "tx_home_notice_2025.odt"),
            new Version("TX_HOME_NOTICE", "2026-01", LocalDate.of(2026, 1, 1), null, "tx_home_notice_2026.odt"),
            new Version("HOME_LIABILITY", "2025-01", LocalDate.of(2025, 1, 1), null, "home_liability.odt"),
            new Version("SCHEDULED_PROPERTY_ENDORSEMENT", "2025-01", LocalDate.of(2025, 1, 1), null, "scheduled_property.odt"),
            new Version("HOME_CHANGE_NOTICE", "2025-01", LocalDate.of(2025, 1, 1), null, "home_change.odt"),
            new Version("AUTO_DECLARATIONS", "2025-01", LocalDate.of(2025, 1, 1), null, "auto_declarations.odt"),
            new Version("AUTO_BASE", "2025-01", LocalDate.of(2025, 1, 1), null, "auto_base.odt"),
            new Version("TX_AUTO_NOTICE", "2025-01", LocalDate.of(2025, 1, 1), null, "tx_auto_notice.odt"),
            new Version("AUTO_COLLISION", "2025-01", LocalDate.of(2025, 1, 1), null, "auto_collision.odt"),
            new Version("AUTO_VEHICLE_CHANGE", "2025-01", LocalDate.of(2025, 1, 1), null, "auto_vehicle_change.odt"),
            new Version("POLICY_CANCELLATION_NOTICE", "2025-01", LocalDate.of(2025, 1, 1), null, "cancellation.odt"),
            new Version("POLICY_REINSTATEMENT_NOTICE", "2025-01", LocalDate.of(2025, 1, 1), null, "reinstatement.odt")
    );

    public Version version(String code, LocalDate date) {
        return versions.stream().filter(v -> v.code.equals(code) && !date.isBefore(v.from) && (v.to == null || !date.isAfter(v.to))).max(Comparator.comparing(Version::from)).orElseThrow(() -> new IllegalStateException("NO_APPROVED_FORM_VERSION: " + code + " at " + date));
    }

    public List<Version> versions(String code) {
        return versions.stream().filter(v -> v.code.equals(code)).toList();
    }

    public List<Selection> select(PolicySnapshot p, String transaction, LocalDate date) {
        LinkedHashMap<String, String> matches = new LinkedHashMap<>();
        if (transaction.equals("CANCELLATION")) {
            matches.put("POLICY_CANCELLATION_NOTICE", "Cancellation transaction");
        } else if (transaction.equals("REINSTATEMENT")) {
            matches.put("POLICY_REINSTATEMENT_NOTICE", "Reinstatement transaction");
        } else {
            if (p.productCode().equals("HOME")) {
                if (transaction.equals("NEW_BUSINESS") || transaction.equals("RENEWAL")) {
                    matches.put("HOME_DECLARATIONS", "New business/renewal");
                    matches.put("HOME_BASE", "Home policy base");
                }
                if (p.jurisdiction().equals("TX"))
                    matches.put("TX_HOME_NOTICE", "Texas state-specific illustrative rule");
                if (p.hasCoverage("PERSONAL_LIABILITY")) matches.put("HOME_LIABILITY", "Liability coverage selected");
                if (p.hasCoverage("SCHEDULED_JEWELRY"))
                    matches.put("SCHEDULED_PROPERTY_ENDORSEMENT", "Scheduled jewelry coverage selected");
                if (transaction.equals("ENDORSEMENT") && p.changedFields().contains("COVERAGES"))
                    matches.put("HOME_CHANGE_NOTICE", "Coverage change");
            } else if (p.productCode().equals("AUTO")) {
                if (transaction.equals("NEW_BUSINESS") || transaction.equals("RENEWAL")) {
                    matches.put("AUTO_DECLARATIONS", "New business/renewal");
                    matches.put("AUTO_BASE", "Auto policy base");
                }
                if (p.jurisdiction().equals("TX"))
                    matches.put("TX_AUTO_NOTICE", "Texas state-specific illustrative rule");
                if (p.hasCoverage("COLLISION")) matches.put("AUTO_COLLISION", "Collision coverage selected");
                if (transaction.equals("ENDORSEMENT") && p.changedFields().contains("VEHICLES"))
                    matches.put("AUTO_VEHICLE_CHANGE", "Vehicle change");
            } else throw new IllegalArgumentException("UNSUPPORTED_PRODUCT: " + p.productCode());
        }
        if (matches.isEmpty()) throw new IllegalStateException("NO_APPLICABLE_FORMS");
        return matches.entrySet().stream().map(e -> new Selection(e.getKey(), version(e.getKey(), date), e.getValue())).toList();
    }

    public record Version(String code, String version, LocalDate from, LocalDate to, String template) {
    }

    public record Selection(String formCode, Version version, String reason) {
    }
}

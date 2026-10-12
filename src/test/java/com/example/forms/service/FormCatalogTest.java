package com.example.forms.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FormCatalogTest {
    private final FormCatalog catalog = new FormCatalog();

    @Test
    void dateBoundary() {
        assertEquals("2025-01", catalog.version("TX_HOME_NOTICE", LocalDate.of(2025, 12, 31)).version());
        assertEquals("2026-01", catalog.version("TX_HOME_NOTICE", LocalDate.of(2026, 1, 1)).version());
    }

    @Test
    void homeNewBusiness() {
        var p = new MockPolicyClient().getRevision("H100245", 1);
        var names = catalog.select(p, "NEW_BUSINESS", LocalDate.of(2026, 1, 1)).stream().map(FormCatalog.Selection::formCode).toList();
        assertTrue(names.contains("HOME_DECLARATIONS"));
        assertTrue(names.contains("HOME_LIABILITY"));
        assertFalse(names.contains("SCHEDULED_PROPERTY_ENDORSEMENT"));
    }

    @Test
    void homeEndorsement() {
        var p = new MockPolicyClient().getRevision("H100245", 2);
        var names = catalog.select(p, "ENDORSEMENT", LocalDate.of(2026, 6, 1)).stream().map(FormCatalog.Selection::formCode).toList();
        assertTrue(names.contains("SCHEDULED_PROPERTY_ENDORSEMENT"));
        assertTrue(names.contains("HOME_CHANGE_NOTICE"));
    }

    @Test
    void autoVehicleEndorsement() {
        var p = new MockPolicyClient().getRevision("A200678", 2);
        var names = catalog.select(p, "ENDORSEMENT", LocalDate.of(2026, 6, 1)).stream().map(FormCatalog.Selection::formCode).toList();
        assertTrue(names.contains("AUTO_VEHICLE_CHANGE"));
    }
}

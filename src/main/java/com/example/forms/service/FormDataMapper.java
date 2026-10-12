package com.example.forms.service;

import com.example.forms.domain.PolicySnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class FormDataMapper {
    private String money(BigDecimal n) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(n);
    }

    public Map<String, String> map(PolicySnapshot p, String formCode) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("POLICY_NUMBER", p.policyNumber());
        values.put("POLICY_REVISION", String.valueOf(p.revision()));
        values.put("PRODUCT", p.productCode());
        values.put("STATE", p.jurisdiction());
        values.put("INSURED_NAME", p.insuredName());
        values.put("EFFECTIVE_DATE", p.effectiveDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
        values.put("EXPIRATION_DATE", p.expirationDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
        values.put("PREMIUM", money(p.annualPremium()));
        values.putAll(p.risk());
        for (var c : p.coverages()) {
            values.put(c.code() + "_LIMIT", money(c.limit()));
            values.put(c.code() + "_DEDUCTIBLE", money(c.deductible()));
        }
        if (p.coverage("DWELLING") != null) values.put("DEDUCTIBLE", money(p.coverage("DWELLING").deductible()));
        if (p.coverage("COLLISION") != null) values.put("DEDUCTIBLE", money(p.coverage("COLLISION").deductible()));
        return values;
    }
}

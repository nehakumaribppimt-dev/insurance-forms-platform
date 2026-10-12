package com.example.forms.service;

import com.example.forms.domain.*;
import com.example.forms.repo.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
public class JobProcessor {
    private final FormJobRepository jobs;
    private final GeneratedDocumentRepository docs;
    private final PolicyClient policies;
    private final FormCatalog catalog;
    private final FormDataMapper mapper;
    private final OdtDocumentService renderer;

    public JobProcessor(FormJobRepository j, GeneratedDocumentRepository d, PolicyClient p, FormCatalog c, FormDataMapper m, OdtDocumentService r) {
        jobs = j;
        docs = d;
        policies = p;
        catalog = c;
        mapper = m;
        renderer = r;
    }

    @Async
    public void process(String id) {
        FormJob job = jobs.findById(id).orElseThrow();
        job.status = "PROCESSING";
        job.updatedAt = Instant.now();
        jobs.save(job);
        try {
            PolicySnapshot policy = policies.getRevision(job.policyNumber, job.policyRevision);
            LocalDate date = LocalDate.parse(job.transactionEffectiveDate);
            if (!policy.productCode().equals(job.productCode) || !policy.jurisdiction().equals(job.jurisdiction))
                throw new IllegalArgumentException("REQUEST_POLICY_MISMATCH");
            for (FormCatalog.Selection selection : catalog.select(policy, job.transactionType, date)) {
                String docId = "DOC-" + UUID.randomUUID().toString().substring(0, 12);
                var output = renderer.render(selection.version().template(), mapper.map(policy, selection.formCode()), docId);
                docs.save(new GeneratedDocument(docId, job.id, policy.policyNumber(), policy.revision(), selection.formCode(), selection.version().version(), output.getFileName().toString()));
            }
            job.status = "COMPLETED";
            job.errorCode = null;
            job.errorMessage = null;
        } catch (Exception ex) {
            job.status = "FAILED";
            job.errorCode = ex instanceof IllegalArgumentException ? "BUSINESS_VALIDATION_FAILED" : "PROCESSING_FAILED";
            job.errorMessage = ex.getMessage() == null ? "Unknown processing failure" : ex.getMessage().substring(0, Math.min(900, ex.getMessage().length()));
        }
        job.updatedAt = Instant.now();
        jobs.save(job);
    }
}

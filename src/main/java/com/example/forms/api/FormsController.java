package com.example.forms.api;

import com.example.forms.domain.*;
import com.example.forms.repo.*;
import com.example.forms.service.*;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/v1") // API versioning
public class FormsController {

    private final PolicyClient policies; // to obtain upstream policy details
    private final FormCatalog catalog; //to determine applicable forms and versions
    private final JobProcessor processor; // to start processing actual insurance form
    private final FormJobRepository jobs; // use to save and retrieve processing jobs from db
    private final GeneratedDocumentRepository docs; //to retrieve generated doc records from db
    private final OdtDocumentService renderer; //to resolve the file location of generated doc. doc processing is also connected to it

    /*constructor dependency injection - used bcz it makes dependencies explicit, supports easier unit testing and allow us to keep them final*/
    public FormsController(FormJobRepository j, GeneratedDocumentRepository d, JobProcessor p, PolicyClient pc, FormCatalog c, OdtDocumentService r) {
        jobs = j;
        docs = d;
        processor = p;
        policies = pc;
        catalog = c;
        renderer = r;
    }

    @PostMapping("/form-jobs") // handle http post request
    //use to create new processing job
    public ResponseEntity<?> create(@Valid @RequestBody JobRequest req, @RequestHeader("Idempotency-Key") String key) {
        if (key.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key required");
        if (!Set.of("NEW_BUSINESS", "RENEWAL", "ENDORSEMENT", "CANCELLATION", "REINSTATEMENT").contains(req.transactionType()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid transactionType");
        Optional<FormJob> prior = jobs.findByIdempotencyKey(key);
        if (prior.isPresent()) {
            FormJob j = prior.get();
            if (!j.policyNumber.equals(req.policyNumber()) || j.policyRevision != req.policyRevision() || !j.transactionType.equals(req.transactionType()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key reused for different operation");
            return ResponseEntity.status(202).body(accepted(j));
        }
        FormJob j = new FormJob("JOB-" + UUID.randomUUID().toString().substring(0, 12), req.requestId(), req.policyNumber(), req.policyRevision(), req.productCode(), req.jurisdiction(), req.transactionType(), req.transactionEffectiveDate().toString(), key);
        try {
            jobs.saveAndFlush(j);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            FormJob existing = jobs.findByIdempotencyKey(key).orElseThrow();
            return ResponseEntity.status(202).body(accepted(existing));
        }
        processor.process(j.id);
        return ResponseEntity.status(202).body(accepted(j));
    }

    private Map<String, Object> accepted(FormJob j) {
        return Map.of("jobId", j.id, "requestId", j.requestId, "status", j.status, "statusPath", "/v1/form-jobs/" + j.id);
    }

    @GetMapping("/form-jobs/{id}")
    public Object status(@PathVariable String id) {
        FormJob j = jobs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return Map.of("jobId", j.id, "requestId", j.requestId, "policyNumber", j.policyNumber, "status", j.status, "errorCode", j.errorCode == null ? "" : j.errorCode, "errorMessage", j.errorMessage == null ? "" : j.errorMessage, "documents", docs.findByJobId(id));
    }

    @GetMapping("/policies/{policyNumber}/documents")
    public Object documents(@PathVariable String policyNumber) {
        return docs.findByPolicyNumberOrderByCreatedAtDesc(policyNumber);
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable String id) {
        GeneratedDocument d = docs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Path path = renderer.locate(d.filename);
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        MediaType type = d.filename.endsWith(".pdf") ? MediaType.APPLICATION_PDF : MediaType.parseMediaType("application/vnd.oasis.opendocument.text");
        return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + d.filename + "\"").body(new FileSystemResource(path));
    }

    @GetMapping("/forms/applicable")
    public Object applicable(@RequestParam String policyNumber, @RequestParam int revision, @RequestParam(defaultValue = "NEW_BUSINESS") String transactionType, @RequestParam String effectiveDate) {
        return catalog.select(policies.getRevision(policyNumber, revision), transactionType, java.time.LocalDate.parse(effectiveDate));
    }

    @GetMapping("/forms/{code}/versions")
    public Object versions(@PathVariable String code) {
        return catalog.versions(code);
    }

    @PostMapping("/form-jobs/{id}/retry")
    public Object retry(@PathVariable String id) {
        FormJob j = jobs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!j.status.equals("FAILED"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed jobs can retry");
        if (!docs.findByJobId(id).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Partial documents exist; manual reconciliation required");
        processor.process(id);
        return accepted(j);
    }

    @ExceptionHandler({IllegalArgumentException.class, java.time.format.DateTimeParseException.class})
    public ResponseEntity<?> bad(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "INVALID_REQUEST", "message", ex.getMessage() == null ? "Invalid input" : ex.getMessage()));
    }
}

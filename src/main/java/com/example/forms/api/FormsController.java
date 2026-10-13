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
    public FormsController(FormJobRepository j, GeneratedDocumentRepository d, JobProcessor p, PolicyClient pc,
                           FormCatalog c, OdtDocumentService r) {
        jobs = j;
        docs = d;
        processor = p;
        policies = pc;
        catalog = c;
        renderer = r;
    }

    @PostMapping("/form-jobs") // handle http post request
    //use to create new processing job
    public ResponseEntity<?> create(@Valid @RequestBody JobRequest req, @RequestHeader("Idempotency-Key") String key)
    /*  // @RequestHeader("Idempotency-Key")-> takes value from postman header tab .
     We are telling SB, whenever someone calls this api, look inside HTTP request headers, find header name
     idempotency-key and assign its value to the java variable key . use->
         let assume policy system has sent the request . request is sent to the backend,job is saved in db but due
         to network prob, response does not get reached to caller. caller will send the same request.
         if each request will create new job, then duplicate doc processing can happen. thats why we use
         idempotency-key
     */
    {
        // first validatation
        if (key.isBlank()) // let say, idempoyency-key is blank, then , key.isBlank() = true -> throw exp (404)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key required");


        // second validation. checks that the incoming transaction type matches allowed values of trans type or not
        // Set.of()-> create immutable set
        if (!Set.of("NEW_BUSINESS", "RENEWAL", "ENDORSEMENT", "CANCELLATION", "REINSTATEMENT")
                .contains(req.transactionType()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid transactionType");


        //duplicate request handling
        /*jobs.findByIdempotencyKey(key) -> job repo will search that if there is any existing processing job in db
        with this idempotency-key then prior.isPresent()= true  */

        Optional<FormJob> prior = jobs.findByIdempotencyKey(key);
        if (prior.isPresent()) {
            FormJob j = prior.get();

            /*when key is same but policy is different in requests, then it will throw conflict exception bcz
            we cant use same idempotency key for different operations
             */
            if (!j.policyNumber.equals(req.policyNumber()) || j.policyRevision != req.policyRevision() ||
                    !j.transactionType.equals(req.transactionType()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key reused for " +
                        "different operation");
            return ResponseEntity.status(202).body(accepted(j));
            /*when same request is sent again, backend won't create new job, it will return the acknowledgment
            of existing job */
        }

        /* if we dont find any existing job, then controller will create a new formjob obj. it doesnt generate pdf yet,
        only a processing job obj is created.
        in this obj, we are storing info of incoming request.
        JobRequest - is the request data which we get from API. FormJob - is the processing job entity/obj of our app.
        */
        FormJob j = new FormJob("JOB-" + UUID.randomUUID().toString().substring(0, 12), req.requestId(),
                req.policyNumber(), req.policyRevision(), req.productCode(), req.jurisdiction(),
                req.transactionType(), req.transactionEffectiveDate().toString(), key);

        try {
            jobs.saveAndFlush(j); //it saves entity in db and explicitly ask hibernate to execute pending sql queries before continuing.
        }
        /* we are using try catch block bcz -> let say, two requests came at same time, both will check
        jobs.findByIdempotencyKey(key) and since initially no job will be present,both request will try to insert in db
         and if in db, there is unique constraint on idempotency-key, then one request will able to insert successfully
         whereas another one will violate the unique constraint. this is known as race condition.
         this catch block is used to handle concurrent duplicate-insert situation */
        catch (org.springframework.dao.DataIntegrityViolationException e) {
            FormJob existing = jobs.findByIdempotencyKey(key).orElseThrow();
            return ResponseEntity.status(202).body(accepted(existing));
        }

        // here, controller provide job id to jobprocessor and jobprocssor will process the job
        processor.process(j.id);

        //return http response
        return ResponseEntity.status(202).body(accepted(j));
    }

    //helper method -> creates map. we have used in returning http response
    private Map<String, Object> accepted(FormJob j) {
        return Map.of(
                "jobId", j.id,
                "requestId", j.requestId,
                "status", j.status,
                "statusPath","/v1/form-jobs/" + j.id);
    }

    //to get the status and generated doc records in response
    @GetMapping("/form-jobs/{id}")
    public Object status(@PathVariable String id) {
        FormJob j = jobs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return Map.of("jobId", j.id, "requestId", j.requestId, "policyNumber", j.policyNumber,
                "status", j.status, "errorCode", j.errorCode == null ? "" : j.errorCode, "errorMessage",
                j.errorMessage == null ? "" : j.errorMessage, "documents", docs.findByJobId(id));
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
        MediaType type = d.filename.endsWith(".pdf") ? MediaType.APPLICATION_PDF :
                MediaType.parseMediaType("application/vnd.oasis.opendocument.text");
        return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + d.filename + "\"").body(new FileSystemResource(path));
    }

    @GetMapping("/forms/applicable")
    public Object applicable(@RequestParam String policyNumber, @RequestParam int revision,
                             @RequestParam(defaultValue = "NEW_BUSINESS") String transactionType,
                             @RequestParam String effectiveDate) {
        return catalog.select(policies.getRevision(policyNumber, revision), transactionType,
                java.time.LocalDate.parse(effectiveDate));
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Partial documents exist; manual" +
                    " reconciliation required");
        processor.process(id);
        return accepted(j);
    }

    @ExceptionHandler({IllegalArgumentException.class, java.time.format.DateTimeParseException.class})
    public ResponseEntity<?> bad(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "INVALID_REQUEST", "message",
                ex.getMessage() == null ? "Invalid input" : ex.getMessage()));
    }
}

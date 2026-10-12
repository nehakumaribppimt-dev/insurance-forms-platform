package com.example.forms.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "generated_documents")
public class GeneratedDocument {
    @Id
    public String id;
    public String jobId;
    public String policyNumber;
    public int policyRevision;
    public String formCode;
    public String formVersion;
    public String filename;
    public String status;
    public Instant createdAt;

    protected GeneratedDocument() {
    }

    public GeneratedDocument(String id, String jobId, String policyNumber, int revision, String formCode, String version, String filename) {
        this.id = id;
        this.jobId = jobId;
        this.policyNumber = policyNumber;
        this.policyRevision = revision;
        this.formCode = formCode;
        this.formVersion = version;
        this.filename = filename;
        this.status = "GENERATED";
        this.createdAt = Instant.now();
    }
}

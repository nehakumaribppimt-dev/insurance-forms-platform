package com.example.forms.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "form_jobs", uniqueConstraints = @UniqueConstraint(columnNames = "idempotencyKey"))
public class FormJob {
    @Id
    public String id;
    @Column(nullable = false)
    public String requestId;
    @Column(nullable = false)
    public String policyNumber;
    public int policyRevision;
    public String productCode;
    public String jurisdiction;
    public String transactionType;
    public String transactionEffectiveDate;
    @Column(nullable = false)
    public String idempotencyKey;
    public String status;
    public String errorCode;
    @Column(length = 1000)
    public String errorMessage;
    public Instant createdAt;
    public Instant updatedAt;

    protected FormJob() {
    }

    public FormJob(String id, String requestId, String policyNumber, int revision, String product, String state, String transaction, String date, String key) {
        this.id = id;
        this.requestId = requestId;
        this.policyNumber = policyNumber;
        this.policyRevision = revision;
        this.productCode = product;
        this.jurisdiction = state;
        this.transactionType = transaction;
        this.transactionEffectiveDate = date;
        this.idempotencyKey = key;
        this.status = "ACCEPTED";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}

package com.example.forms.repo;

import com.example.forms.domain.GeneratedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, String> {
    List<GeneratedDocument> findByJobId(String jobId);

    List<GeneratedDocument> findByPolicyNumberOrderByCreatedAtDesc(String policyNumber);
}

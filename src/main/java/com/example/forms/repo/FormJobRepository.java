package com.example.forms.repo;

import com.example.forms.domain.FormJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FormJobRepository extends JpaRepository<FormJob, String> {
    Optional<FormJob> findByIdempotencyKey(String key);
}

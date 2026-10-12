package com.example.forms.service;

import com.example.forms.domain.PolicySnapshot;

public interface PolicyClient {
    PolicySnapshot getRevision(String policyNumber, int revision);
}

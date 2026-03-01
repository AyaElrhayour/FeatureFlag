package com.assignment.featureflagservice.helpers;

import com.assignment.featureflagservice.repository.AuditHistoryRepository;
import com.assignment.featureflagservice.repository.FeatureFlagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestDataCleaner {

    @Autowired
    private FeatureFlagRepository flagRepository;

    @Autowired
    private AuditHistoryRepository auditRepository;

    public void cleanAll() {
        auditRepository.deleteAll();
        flagRepository.deleteAll();
    }
}
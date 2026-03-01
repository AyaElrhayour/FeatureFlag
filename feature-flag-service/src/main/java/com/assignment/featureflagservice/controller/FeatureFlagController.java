package com.assignment.featureflagservice.controller;

import com.assignment.featureflagservice.api.FeatureFlagsApi;
import com.assignment.featureflagservice.dto.*;
import com.assignment.featureflagservice.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class FeatureFlagController implements FeatureFlagsApi {

    private final FeatureFlagService service;

    @Override
    public ResponseEntity<FlagResponse> createFlag(
            CreateFlagRequest createFlagRequest,
            String xActor) {
        FlagResponse response = service.createFlag(createFlagRequest, xActor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<FlagResponse> getFlag(String flagKey) {
        return ResponseEntity.ok(service.getFlag(flagKey));
    }

    @Override
    public ResponseEntity<List<FlagResponse>> listFlags() {
        return ResponseEntity.ok(service.listFlags());
    }

    @Override
    public ResponseEntity<FlagResponse> updateFlag(
            String flagKey,
            UpdateFlagRequest updateFlagRequest,
            String xActor) {
        return ResponseEntity.ok(service.updateFlag(flagKey, updateFlagRequest, xActor));
    }

    @Override
    public ResponseEntity<Void> deleteFlag(
            String flagKey,
            String xActor) {
        service.deleteFlag(flagKey, xActor);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<AuditResponse>> getAuditHistory(String flagKey) {
        return ResponseEntity.ok(service.getAuditHistory(flagKey));
    }
}
package com.assignment.featureflagservice.service;

import com.assignment.featureflagservice.dto.*;
import com.assignment.featureflagservice.entity.AuditHistory;
import com.assignment.featureflagservice.entity.FeatureFlag;
import com.assignment.featureflagservice.enums.AuditAction;
import com.assignment.featureflagservice.exception.FlagAlreadyExistsException;
import com.assignment.featureflagservice.exception.FlagNotFoundException;
import com.assignment.featureflagservice.exception.VersionConflictException;
import com.assignment.featureflagservice.mapper.FeatureFlagMapper;
import com.assignment.featureflagservice.repository.AuditHistoryRepository;
import com.assignment.featureflagservice.repository.FeatureFlagRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository flagRepository;
    private final AuditHistoryRepository auditRepository;
    private final FeatureFlagMapper mapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public FlagResponse createFlag(CreateFlagRequest request, String actor) {
        log.info("Creating feature flag: {}", request.getFlagKey());
        if (flagRepository.existsByFlagKey(request.getFlagKey())) {
            throw new FlagAlreadyExistsException(request.getFlagKey());
        }
        FeatureFlag flag = FeatureFlag.builder()
                .flagKey(request.getFlagKey())
                .description(request.getDescription())
                .devEnabled(request.getEnvironments().getDev())
                .stagingEnabled(request.getEnvironments().getStaging())
                .prodEnabled(request.getEnvironments().getProd())
                .build();
        FeatureFlag saved = flagRepository.save(flag);
        writeAudit(saved, AuditAction.CREATED, null, toJson(saved), actor);
        log.info("Feature flag created: {} at version {}", saved.getFlagKey(), saved.getVersion());
        return mapper.toFlagResponse(saved);
    }

    @Transactional(readOnly = true)
    public FlagResponse getFlag(String flagKey) {
        log.info("Fetching feature flag: {}", flagKey);
        return mapper.toFlagResponse(findByKeyOrThrow(flagKey));
    }

    @Transactional(readOnly = true)
    public List<FlagResponse> listFlags() {
        log.info("Listing all feature flags");
        return mapper.toFlagResponseList(flagRepository.findAll());
    }

    @Transactional
    public FlagResponse updateFlag(String flagKey, UpdateFlagRequest request, String actor) {
        log.info("Updating feature flag: {} with version {}", flagKey, request.getVersion());
        FeatureFlag flag = findByKeyOrThrow(flagKey);
        if (!flag.getVersion().equals(request.getVersion())) {
            throw new VersionConflictException(flagKey, request.getVersion());
        }
        String beforeState = toJson(flag);
        flag.setDescription(request.getDescription());
        flag.setDevEnabled(request.getEnvironments().getDev());
        flag.setStagingEnabled(request.getEnvironments().getStaging());
        flag.setProdEnabled(request.getEnvironments().getProd());
        flag.setVersion(flag.getVersion() + 1);
        FeatureFlag updated = flagRepository.saveAndFlush(flag);
        writeAudit(updated, AuditAction.UPDATED, beforeState, toJson(updated), actor);
        log.info("Feature flag updated: {} now at version {}", updated.getFlagKey(), updated.getVersion());
        return mapper.toFlagResponse(updated);
    }

    @Transactional
    public void deleteFlag(String flagKey, String actor) {
        log.info("Deleting feature flag: {}", flagKey);
        FeatureFlag flag = findByKeyOrThrow(flagKey);
        String beforeState = toJson(flag);
        flagRepository.delete(flag);
        writeAudit(flag, AuditAction.DELETED, beforeState, null, actor);
        log.info("Feature flag deleted: {}", flagKey);
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> getAuditHistory(String flagKey) {
        log.info("Fetching audit history for flag: {}", flagKey);
        if (!flagRepository.existsByFlagKey(flagKey)
                && !auditRepository.existsByFlagKey(flagKey)) {
            throw new FlagNotFoundException(flagKey);
        }
        return mapper.toAuditResponseList(
                auditRepository.findByFlagKeyOrderByChangedAtDesc(flagKey));
    }

    public FeatureFlag findByKeyOrThrow(String flagKey) {
        return flagRepository.findByFlagKey(flagKey)
                .orElseThrow(() -> new FlagNotFoundException(flagKey));
    }

    private void writeAudit(FeatureFlag flag, AuditAction action,
                            String beforeState, String afterState, String actor) {
        AuditHistory audit = AuditHistory.builder()
                .flagKey(flag.getFlagKey())
                .version(flag.getVersion())
                .action(action)
                .changedBy(actor != null ? actor : "unknown")
                .changedAt(OffsetDateTime.now())
                .beforeState(beforeState)
                .afterState(afterState)
                .build();
        auditRepository.save(audit);
    }

    private String toJson(FeatureFlag flag) {
        try {
            return objectMapper.writeValueAsString(flag);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize flag {} to JSON for audit", flag.getFlagKey());
            return "{}";
        }
    }
}
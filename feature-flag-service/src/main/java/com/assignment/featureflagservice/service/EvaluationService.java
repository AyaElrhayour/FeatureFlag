package com.assignment.featureflagservice.service;

import com.assignment.featureflagservice.dto.EvaluationRequest;
import com.assignment.featureflagservice.dto.EvaluationResponse;
import com.assignment.featureflagservice.entity.FeatureFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final FeatureFlagService flagService;

    @Transactional(readOnly = true)
    public EvaluationResponse evaluateFlag(String flagKey, EvaluationRequest request) {
        log.info("Evaluating flag: {} for environment: {}", flagKey, request.getEnvironment());
        FeatureFlag flag = flagService.findByKeyOrThrow(flagKey);
        boolean enabled = switch (request.getEnvironment()) {
            case DEV -> flag.isDevEnabled();
            case STAGING -> flag.isStagingEnabled();
            case PROD -> flag.isProdEnabled();
        };
        String reason = enabled
                ? "Flag is enabled in " + request.getEnvironment().getValue()
                : "Flag is disabled in " + request.getEnvironment().getValue();
        EvaluationResponse response = new EvaluationResponse();
        response.setFlagKey(flagKey);
        response.setEnabled(enabled);
        response.setFlagVersion(flag.getVersion());
        response.setReason(reason);
        response.setEnvironment(request.getEnvironment().getValue());
        log.info("Flag: {} evaluated as {} in {}", flagKey, enabled, request.getEnvironment());
        return response;
    }
}
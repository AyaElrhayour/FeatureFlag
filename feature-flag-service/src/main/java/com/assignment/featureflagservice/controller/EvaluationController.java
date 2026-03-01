package com.assignment.featureflagservice.controller;

import com.assignment.featureflagservice.api.EvaluationApi;
import com.assignment.featureflagservice.dto.EvaluationRequest;
import com.assignment.featureflagservice.dto.EvaluationResponse;
import com.assignment.featureflagservice.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class EvaluationController implements EvaluationApi {

    private final EvaluationService evaluationService;

    @Override
    public ResponseEntity<EvaluationResponse> evaluateFlag(
            String flagKey,
            EvaluationRequest evaluationRequest) {
        return ResponseEntity.ok(evaluationService.evaluateFlag(flagKey, evaluationRequest));
    }
}
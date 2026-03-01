package com.assignment.featureflagservice.service;

import com.assignment.featureflagservice.dto.EvaluationRequest;
import com.assignment.featureflagservice.dto.EvaluationResponse;
import com.assignment.featureflagservice.entity.FeatureFlag;
import com.assignment.featureflagservice.exception.FlagNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock
    private FeatureFlagService flagService;

    @InjectMocks
    private EvaluationService evaluationService;

    private FeatureFlag flag;

    @BeforeEach
    void setUp() {
        flag = FeatureFlag.builder()
                .flagKey("test-flag")
                .description("Test flag")
                .devEnabled(true)
                .stagingEnabled(false)
                .prodEnabled(true)
                .version(2)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Nested
    class EnvironmentRoutingTests {

        @ParameterizedTest
        @CsvSource({
                "DEV,     true",
                "STAGING, false",
                "PROD,    true"
        })
        void givenFlagWithPerEnvironmentState_whenEvaluate_thenReturnCorrectEnabledState(
                String environmentName, boolean expectedEnabled) {
            EvaluationRequest request = new EvaluationRequest();
            request.setEnvironment(
                    EvaluationRequest.EnvironmentEnum.valueOf(environmentName.trim()));
            when(flagService.findByKeyOrThrow("test-flag")).thenReturn(flag);
            EvaluationResponse response = evaluationService.evaluateFlag("test-flag", request);
            assertThat(response.getEnabled()).isEqualTo(expectedEnabled);
            assertThat(response.getFlagKey()).isEqualTo("test-flag");
            assertThat(response.getFlagVersion()).isEqualTo(2);
            assertThat(response.getEnvironment())
                    .isEqualTo(request.getEnvironment().getValue());
        }
    }

    @Nested
    class ReasonStringTests {

        @Test
        void givenEnabledFlagInEnvironment_whenEvaluate_thenReasonMentionsEnabled() {
            EvaluationRequest request = new EvaluationRequest();
            request.setEnvironment(EvaluationRequest.EnvironmentEnum.DEV);
            when(flagService.findByKeyOrThrow("test-flag")).thenReturn(flag);
            EvaluationResponse response = evaluationService.evaluateFlag("test-flag", request);
            assertThat(response.getReason()).containsIgnoringCase("enabled");
        }

        @Test
        void givenDisabledFlagInEnvironment_whenEvaluate_thenReasonMentionsDisabled() {
            EvaluationRequest request = new EvaluationRequest();
            request.setEnvironment(EvaluationRequest.EnvironmentEnum.STAGING);
            when(flagService.findByKeyOrThrow("test-flag")).thenReturn(flag);
            EvaluationResponse response = evaluationService.evaluateFlag("test-flag", request);
            assertThat(response.getReason()).containsIgnoringCase("disabled");
        }
    }

    @Nested
    class FlagNotFoundTests {

        @Test
        void givenNonExistentFlag_whenEvaluate_thenThrowFlagNotFoundException() {
            EvaluationRequest request = new EvaluationRequest();
            request.setEnvironment(EvaluationRequest.EnvironmentEnum.PROD);
            when(flagService.findByKeyOrThrow("ghost-flag"))
                    .thenThrow(new FlagNotFoundException("ghost-flag"));
            assertThatThrownBy(() -> evaluationService.evaluateFlag("ghost-flag", request))
                    .isInstanceOf(FlagNotFoundException.class)
                    .hasMessageContaining("ghost-flag");
        }
    }
}
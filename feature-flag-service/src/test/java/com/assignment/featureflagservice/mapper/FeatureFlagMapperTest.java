package com.assignment.featureflagservice.mapper;

import com.assignment.featureflagservice.dto.AuditResponse;
import com.assignment.featureflagservice.dto.Environments;
import com.assignment.featureflagservice.dto.FlagResponse;
import com.assignment.featureflagservice.entity.AuditHistory;
import com.assignment.featureflagservice.entity.FeatureFlag;
import com.assignment.featureflagservice.enums.AuditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagMapperTest {

    private final FeatureFlagMapper mapper = new FeatureFlagMapperImpl();
    private FeatureFlag flag;
    private AuditHistory auditHistory;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        now = OffsetDateTime.now();

        flag = FeatureFlag.builder()
                .id(1L)
                .flagKey("test-flag")
                .description("Test description")
                .devEnabled(true)
                .stagingEnabled(false)
                .prodEnabled(true)
                .version(1)
                .createdAt(now)
                .updatedAt(now)
                .build();

        auditHistory = AuditHistory.builder()
                .id(10L)
                .flagKey("test-flag")
                .version(1)
                .action(AuditAction.CREATED)
                .changedBy("test-user")
                .changedAt(now)
                .beforeState(null)
                .afterState("{\"flagKey\":\"test-flag\"}")
                .build();
    }

    @Nested
    class ToFlagResponseTests {

        @Test
        void givenValidFlag_whenToFlagResponse_thenAllFieldsMappedCorrectly() {
            FlagResponse response = mapper.toFlagResponse(flag);

            assertThat(response).isNotNull();
            assertThat(response.getFlagKey()).isEqualTo("test-flag");
            assertThat(response.getDescription()).isEqualTo("Test description");
            assertThat(response.getVersion()).isEqualTo(1);
            assertThat(response.getCreatedAt()).isEqualTo(now);
            assertThat(response.getUpdatedAt()).isEqualTo(now);
        }

        @Test
        void givenValidFlag_whenToFlagResponse_thenEnvironmentsMappedFromThreeBooleans() {
            FlagResponse response = mapper.toFlagResponse(flag);

            assertThat(response.getEnvironments()).isNotNull();
            assertThat(response.getEnvironments().getDev()).isTrue();
            assertThat(response.getEnvironments().getStaging()).isFalse();
            assertThat(response.getEnvironments().getProd()).isTrue();
        }

        @Test
        void givenNullFlag_whenToFlagResponse_thenReturnNull() {
            assertThat(mapper.toFlagResponse(null)).isNull();
        }
    }

    @Nested
    class ToFlagResponseListTests {

        @Test
        void givenFlagList_whenToFlagResponseList_thenAllFlagsMapped() {
            FeatureFlag flag2 = FeatureFlag.builder()
                    .id(2L)
                    .flagKey("second-flag")
                    .description("Second")
                    .devEnabled(false)
                    .stagingEnabled(true)
                    .prodEnabled(false)
                    .version(2)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            List<FlagResponse> responses = mapper.toFlagResponseList(List.of(flag, flag2));

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).getFlagKey()).isEqualTo("test-flag");
            assertThat(responses.get(1).getFlagKey()).isEqualTo("second-flag");
        }

        @Test
        void givenEmptyList_whenToFlagResponseList_thenReturnEmptyList() {
            assertThat(mapper.toFlagResponseList(List.of())).isEmpty();
        }

        @Test
        void givenNullList_whenToFlagResponseList_thenReturnNull() {
            assertThat(mapper.toFlagResponseList(null)).isNull();
        }
    }

    @Nested
    class ToEnvironmentsTests {

        @Test
        void givenAllEnvironmentsEnabled_whenToEnvironments_thenAllTrue() {
            flag.setDevEnabled(true);
            flag.setStagingEnabled(true);
            flag.setProdEnabled(true);

            Environments result = mapper.toEnvironments(flag);

            assertThat(result.getDev()).isTrue();
            assertThat(result.getStaging()).isTrue();
            assertThat(result.getProd()).isTrue();
        }

        @Test
        void givenAllEnvironmentsDisabled_whenToEnvironments_thenAllFalse() {
            flag.setDevEnabled(false);
            flag.setStagingEnabled(false);
            flag.setProdEnabled(false);

            Environments result = mapper.toEnvironments(flag);

            assertThat(result.getDev()).isFalse();
            assertThat(result.getStaging()).isFalse();
            assertThat(result.getProd()).isFalse();
        }

        @Test
        void givenMixedEnvironmentState_whenToEnvironments_thenEachBooleanMappedIndependently() {
            flag.setDevEnabled(true);
            flag.setStagingEnabled(false);
            flag.setProdEnabled(true);

            Environments result = mapper.toEnvironments(flag);

            assertThat(result.getDev()).isTrue();
            assertThat(result.getStaging()).isFalse();
            assertThat(result.getProd()).isTrue();
        }
    }

    @Nested
    class ToAuditResponseTests {

        @Test
        void givenAuditHistoryWithCreatedAction_whenToAuditResponse_thenAllFieldsMappedCorrectly() {
            AuditResponse response = mapper.toAuditResponse(auditHistory);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getFlagKey()).isEqualTo("test-flag");
            assertThat(response.getVersion()).isEqualTo(1);
            assertThat(response.getChangedBy()).isEqualTo("test-user");
            assertThat(response.getChangedAt()).isEqualTo(now);
            assertThat(response.getBeforeState()).isNull();
            assertThat(response.getAfterState()).isEqualTo("{\"flagKey\":\"test-flag\"}");
        }

        @Test
        void givenNullAuditHistory_whenToAuditResponse_thenReturnNull() {
            assertThat(mapper.toAuditResponse(null)).isNull();
        }

        @ParameterizedTest
        @EnumSource(AuditAction.class)
        void givenEachAuditAction_whenToAuditResponse_thenActionEnumMappedCorrectly(
                AuditAction action) {
            auditHistory.setAction(action);

            AuditResponse response = mapper.toAuditResponse(auditHistory);

            assertThat(response.getAction()).isNotNull();
            assertThat(response.getAction().getValue())
                    .isEqualTo(action.name());
        }
    }

    @Nested
    class ToAuditResponseListTests {

        @Test
        void givenAuditHistoryList_whenToAuditResponseList_thenAllEntriesMapped() {
            AuditHistory second = AuditHistory.builder()
                    .id(11L)
                    .flagKey("test-flag")
                    .version(2)
                    .action(AuditAction.UPDATED)
                    .changedBy("other-user")
                    .changedAt(now.minusHours(1))
                    .beforeState("{\"before\":{}}")
                    .afterState("{\"after\":{}}")
                    .build();

            List<AuditResponse> responses =
                    mapper.toAuditResponseList(List.of(auditHistory, second));

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).getId()).isEqualTo(10L);
            assertThat(responses.get(0).getAction())
                    .isEqualTo(AuditResponse.ActionEnum.CREATED);
            assertThat(responses.get(1).getId()).isEqualTo(11L);
            assertThat(responses.get(1).getAction())
                    .isEqualTo(AuditResponse.ActionEnum.UPDATED);
        }

        @Test
        void givenEmptyList_whenToAuditResponseList_thenReturnEmptyList() {
            assertThat(mapper.toAuditResponseList(List.of())).isEmpty();
        }

        @Test
        void givenNullList_whenToAuditResponseList_thenReturnNull() {
            assertThat(mapper.toAuditResponseList(null)).isNull();
        }
    }
}
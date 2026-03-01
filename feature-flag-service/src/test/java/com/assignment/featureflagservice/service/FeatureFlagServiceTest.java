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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    @Mock private FeatureFlagRepository flagRepository;
    @Mock private AuditHistoryRepository auditRepository;
    @Mock private FeatureFlagMapper mapper;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private FeatureFlagService service;

    private FeatureFlag existingFlag;
    private FlagResponse flagResponse;
    private Environments environments;

    @BeforeEach
    void setUp() {
        environments = new Environments();
        environments.setDev(true);
        environments.setStaging(false);
        environments.setProd(false);

        existingFlag = FeatureFlag.builder()
                .id(1L)
                .flagKey("test-flag")
                .description("Test description")
                .devEnabled(true)
                .stagingEnabled(false)
                .prodEnabled(false)
                .version(1)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        flagResponse = new FlagResponse();
        flagResponse.setFlagKey("test-flag");
        flagResponse.setDescription("Test description");
        flagResponse.setEnvironments(environments);
        flagResponse.setVersion(1);
    }

    @Nested
    class CreateFlagTests {

        private CreateFlagRequest buildRequest(String flagKey) {
            CreateFlagRequest request = new CreateFlagRequest();
            request.setFlagKey(flagKey);
            request.setDescription("Test description");
            request.setEnvironments(environments);
            return request;
        }

        @Test
        void givenValidRequest_whenCreateFlag_thenFlagIsCreatedAndAuditWritten() throws Exception {
            when(flagRepository.existsByFlagKey("new-flag")).thenReturn(false);
            when(flagRepository.save(any(FeatureFlag.class))).thenReturn(existingFlag);
            when(mapper.toFlagResponse(existingFlag)).thenReturn(flagResponse);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            FlagResponse result = service.createFlag(buildRequest("new-flag"), "test-user");
            assertThat(result).isNotNull();
            assertThat(result.getFlagKey()).isEqualTo("test-flag");
            ArgumentCaptor<AuditHistory> auditCaptor = ArgumentCaptor.forClass(AuditHistory.class);
            verify(auditRepository).save(auditCaptor.capture());
            AuditHistory audit = auditCaptor.getValue();
            assertThat(audit.getAction()).isEqualTo(AuditAction.CREATED);
            assertThat(audit.getBeforeState()).isNull();
            assertThat(audit.getAfterState()).isEqualTo("{}");
            assertThat(audit.getChangedBy()).isEqualTo("test-user");
        }

        @Test
        void givenDuplicateFlagKey_whenCreateFlag_thenThrowFlagAlreadyExistsException() {
            when(flagRepository.existsByFlagKey("existing-flag")).thenReturn(true);
            assertThatThrownBy(() -> service.createFlag(buildRequest("existing-flag"), "user"))
                    .isInstanceOf(FlagAlreadyExistsException.class)
                    .hasMessageContaining("existing-flag");
            verify(flagRepository, never()).save(any());
            verify(auditRepository, never()).save(any());
        }

        @Test
        void givenNullActor_whenCreateFlag_thenAuditWrittenWithUnknown() throws Exception {
            when(flagRepository.existsByFlagKey(anyString())).thenReturn(false);
            when(flagRepository.save(any())).thenReturn(existingFlag);
            when(mapper.toFlagResponse(any())).thenReturn(flagResponse);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            service.createFlag(buildRequest("new-flag"), null);

            ArgumentCaptor<AuditHistory> captor = ArgumentCaptor.forClass(AuditHistory.class);
            verify(auditRepository).save(captor.capture());
            assertThat(captor.getValue().getChangedBy()).isEqualTo("unknown");
        }
    }

    @Nested
    class GetFlagTests {

        @Test
        void givenExistingFlagKey_whenGetFlag_thenReturnFlagResponse() {
            when(flagRepository.findByFlagKey("test-flag")).thenReturn(Optional.of(existingFlag));
            when(mapper.toFlagResponse(existingFlag)).thenReturn(flagResponse);

            FlagResponse result = service.getFlag("test-flag");

            assertThat(result).isNotNull();
            assertThat(result.getFlagKey()).isEqualTo("test-flag");
        }

        @Test
        void givenNonExistentFlagKey_whenGetFlag_thenThrowFlagNotFoundException() {
            when(flagRepository.findByFlagKey("ghost-flag")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getFlag("ghost-flag"))
                    .isInstanceOf(FlagNotFoundException.class)
                    .hasMessageContaining("ghost-flag");
        }
    }

    @Nested
    class ListFlagsTests {

        @Test
        void givenFlagsExist_whenListFlags_thenReturnAllFlags() {
            List<FeatureFlag> flags = List.of(existingFlag);
            List<FlagResponse> responses = List.of(flagResponse);
            when(flagRepository.findAll()).thenReturn(flags);
            when(mapper.toFlagResponseList(flags)).thenReturn(responses);
            assertThat(service.listFlags()).hasSize(1);
        }

        @Test
        void givenNoFlagsExist_whenListFlags_thenReturnEmptyList() {
            when(flagRepository.findAll()).thenReturn(List.of());
            when(mapper.toFlagResponseList(List.of())).thenReturn(List.of());

            assertThat(service.listFlags()).isEmpty();
        }
    }

    @Nested
    class UpdateFlagTests {

        private UpdateFlagRequest buildUpdateRequest(Integer version) {
            UpdateFlagRequest request = new UpdateFlagRequest();
            request.setDescription("Updated description");
            request.setEnvironments(environments);
            request.setVersion(version);
            return request;
        }

        @Test
        void givenValidVersionAndRequest_whenUpdateFlag_thenFlagUpdatedAndAuditWritten()
                throws Exception {
            when(flagRepository.findByFlagKey("test-flag")).thenReturn(Optional.of(existingFlag));
            when(flagRepository.saveAndFlush(any(FeatureFlag.class))).thenReturn(existingFlag);
            when(mapper.toFlagResponse(existingFlag)).thenReturn(flagResponse);
            when(objectMapper.writeValueAsString(any()))
                    .thenReturn("{\"before\":{}}", "{\"after\":{}}");
            FlagResponse result = service.updateFlag("test-flag", buildUpdateRequest(1), "updater");
            assertThat(result).isNotNull();
            ArgumentCaptor<AuditHistory> auditCaptor = ArgumentCaptor.forClass(AuditHistory.class);
            verify(auditRepository).save(auditCaptor.capture());
            AuditHistory audit = auditCaptor.getValue();
            assertThat(audit.getAction()).isEqualTo(AuditAction.UPDATED);
            assertThat(audit.getBeforeState()).isEqualTo("{\"before\":{}}");
            assertThat(audit.getAfterState()).isEqualTo("{\"after\":{}}");
            assertThat(audit.getChangedBy()).isEqualTo("updater");
        }

        @Test
        void givenStaleVersion_whenUpdateFlag_thenThrowVersionConflictException() {
            existingFlag.setVersion(3);
            when(flagRepository.findByFlagKey("test-flag")).thenReturn(Optional.of(existingFlag));
            assertThatThrownBy(() ->
                    service.updateFlag("test-flag", buildUpdateRequest(1), "user"))
                    .isInstanceOf(VersionConflictException.class)
                    .hasMessageContaining("test-flag")
                    .hasMessageContaining("1");
            verify(flagRepository, never()).save(any());
            verify(auditRepository, never()).save(any());
        }

        @Test
        void givenNonExistentFlag_whenUpdateFlag_thenThrowFlagNotFoundException() {
            when(flagRepository.findByFlagKey("ghost-flag")).thenReturn(Optional.empty());
            assertThatThrownBy(() ->
                    service.updateFlag("ghost-flag", buildUpdateRequest(1), "user"))
                    .isInstanceOf(FlagNotFoundException.class)
                    .hasMessageContaining("ghost-flag");
        }
    }

    @Nested
    class DeleteFlagTests {

        @Test
        void givenExistingFlag_whenDeleteFlag_thenFlagDeletedAndAuditWritten() throws Exception {
            when(flagRepository.findByFlagKey("test-flag")).thenReturn(Optional.of(existingFlag));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            service.deleteFlag("test-flag", "deleter");
            verify(flagRepository).delete(existingFlag);
            ArgumentCaptor<AuditHistory> auditCaptor = ArgumentCaptor.forClass(AuditHistory.class);
            verify(auditRepository).save(auditCaptor.capture());
            AuditHistory audit = auditCaptor.getValue();
            assertThat(audit.getAction()).isEqualTo(AuditAction.DELETED);
            assertThat(audit.getBeforeState()).isEqualTo("{}");
            assertThat(audit.getAfterState()).isNull();
            assertThat(audit.getChangedBy()).isEqualTo("deleter");
        }

        @Test
        void givenNonExistentFlag_whenDeleteFlag_thenThrowFlagNotFoundException() {
            when(flagRepository.findByFlagKey("ghost-flag")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.deleteFlag("ghost-flag", "user"))
                    .isInstanceOf(FlagNotFoundException.class)
                    .hasMessageContaining("ghost-flag");
            verify(flagRepository, never()).delete(any());
            verify(auditRepository, never()).save(any());
        }
    }

    @Nested
    class GetAuditHistoryTests {

        @Test
        void givenExistingFlag_whenGetAuditHistory_thenReturnRecordsOrderedMostRecentFirst() {
            AuditHistory audit1 = AuditHistory.builder()
                    .flagKey("test-flag").action(AuditAction.UPDATED)
                    .changedAt(OffsetDateTime.now()).version(2).changedBy("user").build();
            AuditHistory audit2 = AuditHistory.builder()
                    .flagKey("test-flag").action(AuditAction.CREATED)
                    .changedAt(OffsetDateTime.now().minusHours(1)).version(1).changedBy("user").build();
            AuditResponse response1 = new AuditResponse();
            AuditResponse response2 = new AuditResponse();
            when(flagRepository.existsByFlagKey("test-flag")).thenReturn(true);
            when(auditRepository.findByFlagKeyOrderByChangedAtDesc("test-flag"))
                    .thenReturn(List.of(audit1, audit2));
            when(mapper.toAuditResponseList(List.of(audit1, audit2)))
                    .thenReturn(List.of(response1, response2));
            List<AuditResponse> result = service.getAuditHistory("test-flag");
            assertThat(result).hasSize(2);
            verify(auditRepository).findByFlagKeyOrderByChangedAtDesc("test-flag");
        }

        @Test
        void givenDeletedFlagWithExistingAuditRecords_whenGetAuditHistory_thenReturnAuditRecords() {
            when(flagRepository.existsByFlagKey("deleted-flag")).thenReturn(false);
            when(auditRepository.existsByFlagKey("deleted-flag")).thenReturn(true);
            when(auditRepository.findByFlagKeyOrderByChangedAtDesc("deleted-flag"))
                    .thenReturn(List.of());
            when(mapper.toAuditResponseList(any())).thenReturn(List.of());
            List<AuditResponse> result = service.getAuditHistory("deleted-flag");
            assertThat(result).isNotNull();
        }

        @Test
        void givenFlagNeverExisted_whenGetAuditHistory_thenThrowFlagNotFoundException() {
            when(flagRepository.existsByFlagKey("ghost-flag")).thenReturn(false);
            when(auditRepository.existsByFlagKey("ghost-flag")).thenReturn(false);
            assertThatThrownBy(() -> service.getAuditHistory("ghost-flag"))
                    .isInstanceOf(FlagNotFoundException.class)
                    .hasMessageContaining("ghost-flag");
        }
    }
}
package com.assignment.featureflagservice.controller;

import com.assignment.featureflagservice.config.GlobalExceptionHandler;
import com.assignment.featureflagservice.dto.*;
import com.assignment.featureflagservice.exception.FlagAlreadyExistsException;
import com.assignment.featureflagservice.exception.FlagNotFoundException;
import com.assignment.featureflagservice.exception.VersionConflictException;
import com.assignment.featureflagservice.service.FeatureFlagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeatureFlagController.class)
@Import(GlobalExceptionHandler.class)
class FeatureFlagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FeatureFlagService service;

    private FlagResponse flagResponse;
    private Environments environments;

    @BeforeEach
    void setUp() {
        environments = new Environments();
        environments.setDev(true);
        environments.setStaging(false);
        environments.setProd(false);
        flagResponse = new FlagResponse();
        flagResponse.setFlagKey("test-flag");
        flagResponse.setDescription("Test description");
        flagResponse.setEnvironments(environments);
        flagResponse.setVersion(1);
        flagResponse.setCreatedAt(OffsetDateTime.now());
        flagResponse.setUpdatedAt(OffsetDateTime.now());
    }

    @Nested
    class CreateFlagTests {

        @Test
        void givenValidRequest_whenCreateFlag_thenReturn201WithFlagResponse() throws Exception {
            CreateFlagRequest request = new CreateFlagRequest();
            request.setFlagKey("test-flag");
            request.setDescription("Test description");
            request.setEnvironments(environments);
            when(service.createFlag(any(CreateFlagRequest.class), eq("actor-user")))
                    .thenReturn(flagResponse);
            mockMvc.perform(post("/api/v1/flags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Actor", "actor-user")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.flagKey").value("test-flag"))
                    .andExpect(jsonPath("$.version").value(1))
                    .andExpect(jsonPath("$.environments.dev").value(true));
        }

        @Test
        void givenMissingFlagKey_whenCreateFlag_thenReturn422() throws Exception {
            CreateFlagRequest request = new CreateFlagRequest();
            request.setDescription("Test description");
            request.setEnvironments(environments);
            mockMvc.perform(post("/api/v1/flags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.path").exists());
        }

        @Test
        void givenMissingDescription_whenCreateFlag_thenReturn422() throws Exception {
            CreateFlagRequest request = new CreateFlagRequest();
            request.setFlagKey("test-flag");
            request.setEnvironments(environments);
            mockMvc.perform(post("/api/v1/flags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422));
        }

        @Test
        void givenMissingEnvironments_whenCreateFlag_thenReturn422() throws Exception {
            CreateFlagRequest request = new CreateFlagRequest();
            request.setFlagKey("test-flag");
            request.setDescription("Test description");
            mockMvc.perform(post("/api/v1/flags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422));
        }

        @Test
        void givenDuplicateFlagKey_whenCreateFlag_thenReturn409() throws Exception {
            CreateFlagRequest request = new CreateFlagRequest();
            request.setFlagKey("duplicate-flag");
            request.setDescription("Test");
            request.setEnvironments(environments);
            when(service.createFlag(any(), any()))
                    .thenThrow(new FlagAlreadyExistsException("duplicate-flag"));
            mockMvc.perform(post("/api/v1/flags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("FLAG_ALREADY_EXISTS"))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("duplicate-flag")));
        }

        @Test
        void givenNoXActorHeader_whenCreateFlag_thenActorDefaultsToUnknown() throws Exception {
            CreateFlagRequest request = new CreateFlagRequest();
            request.setFlagKey("test-flag");
            request.setDescription("Test description");
            request.setEnvironments(environments);
            when(service.createFlag(any(), eq("unknown"))).thenReturn(flagResponse);
            mockMvc.perform(post("/api/v1/flags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
            verify(service).createFlag(any(), eq("unknown"));
        }
    }

    @Nested
    class GetFlagTests {

        @Test
        void givenExistingFlagKey_whenGetFlag_thenReturn200WithFlagResponse() throws Exception {
            when(service.getFlag("test-flag")).thenReturn(flagResponse);
            mockMvc.perform(get("/api/v1/flags/test-flag"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.flagKey").value("test-flag"))
                    .andExpect(jsonPath("$.version").value(1));
        }

        @Test
        void givenNonExistentFlagKey_whenGetFlag_thenReturn404() throws Exception {
            when(service.getFlag("ghost-flag"))
                    .thenThrow(new FlagNotFoundException("ghost-flag"));
            mockMvc.perform(get("/api/v1/flags/ghost-flag"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("FLAG_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("ghost-flag")));
        }
    }

    @Nested
    class ListFlagsTests {

        @Test
        void givenFlagsExist_whenListFlags_thenReturn200WithFlagList() throws Exception {
            when(service.listFlags()).thenReturn(List.of(flagResponse));
            mockMvc.perform(get("/api/v1/flags"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].flagKey").value("test-flag"));
        }

        @Test
        void givenNoFlagsExist_whenListFlags_thenReturn200WithEmptyArray() throws Exception {
            when(service.listFlags()).thenReturn(List.of());
            mockMvc.perform(get("/api/v1/flags"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    class UpdateFlagTests {

        private UpdateFlagRequest buildUpdateRequest(int version) {
            UpdateFlagRequest request = new UpdateFlagRequest();
            request.setDescription("Updated description");
            request.setEnvironments(environments);
            request.setVersion(version);
            return request;
        }

        @Test
        void givenValidVersionAndRequest_whenUpdateFlag_thenReturn200WithUpdatedFlag()
                throws Exception {
            when(service.updateFlag(eq("test-flag"), any(UpdateFlagRequest.class), eq("updater")))
                    .thenReturn(flagResponse);
            mockMvc.perform(put("/api/v1/flags/test-flag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Actor", "updater")
                            .content(objectMapper.writeValueAsString(buildUpdateRequest(1))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.flagKey").value("test-flag"));
        }

        @Test
        void givenStaleVersion_whenUpdateFlag_thenReturn409() throws Exception {
            when(service.updateFlag(eq("test-flag"), any(), any()))
                    .thenThrow(new VersionConflictException("test-flag", 1));
            mockMvc.perform(put("/api/v1/flags/test-flag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildUpdateRequest(1))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("VERSION_CONFLICT"));
        }

        @Test
        void givenNonExistentFlag_whenUpdateFlag_thenReturn404() throws Exception {
            when(service.updateFlag(eq("ghost-flag"), any(), any()))
                    .thenThrow(new FlagNotFoundException("ghost-flag"));
            mockMvc.perform(put("/api/v1/flags/ghost-flag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildUpdateRequest(1))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        void givenMissingVersion_whenUpdateFlag_thenReturn422() throws Exception {
            UpdateFlagRequest request = new UpdateFlagRequest();
            request.setDescription("Updated");
            request.setEnvironments(environments);
            mockMvc.perform(put("/api/v1/flags/test-flag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422));
        }
    }

    @Nested
    class DeleteFlagTests {

        @Test
        void givenExistingFlag_whenDeleteFlag_thenReturn204() throws Exception {
            doNothing().when(service).deleteFlag(eq("test-flag"), eq("deleter"));
            mockMvc.perform(delete("/api/v1/flags/test-flag")
                            .header("X-Actor", "deleter"))
                    .andExpect(status().isNoContent());
            verify(service).deleteFlag("test-flag", "deleter");
        }

        @Test
        void givenNonExistentFlag_whenDeleteFlag_thenReturn404() throws Exception {
            doThrow(new FlagNotFoundException("ghost-flag"))
                    .when(service).deleteFlag(eq("ghost-flag"), any());
            mockMvc.perform(delete("/api/v1/flags/ghost-flag"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("FLAG_NOT_FOUND"));
        }
    }

    @Nested
    class AuditHistoryTests {

        @Test
        void givenExistingFlag_whenGetAuditHistory_thenReturn200WithAuditList() throws Exception {
            AuditResponse audit = new AuditResponse();
            audit.setFlagKey("test-flag");
            audit.setVersion(1);
            audit.setAction(AuditResponse.ActionEnum.CREATED);
            audit.setChangedBy("user");
            audit.setChangedAt(OffsetDateTime.now());
            when(service.getAuditHistory("test-flag")).thenReturn(List.of(audit));
            mockMvc.perform(get("/api/v1/flags/test-flag/audit"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].flagKey").value("test-flag"))
                    .andExpect(jsonPath("$[0].action").value("CREATED"));
        }

        @Test
        void givenNonExistentFlag_whenGetAuditHistory_thenReturn404() throws Exception {
            when(service.getAuditHistory("ghost-flag"))
                    .thenThrow(new FlagNotFoundException("ghost-flag"));
            mockMvc.perform(get("/api/v1/flags/ghost-flag/audit"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }
}
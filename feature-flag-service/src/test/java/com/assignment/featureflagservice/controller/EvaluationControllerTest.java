package com.assignment.featureflagservice.controller;

import com.assignment.featureflagservice.config.GlobalExceptionHandler;
import com.assignment.featureflagservice.dto.EvaluationRequest;
import com.assignment.featureflagservice.dto.EvaluationResponse;
import com.assignment.featureflagservice.exception.FlagNotFoundException;
import com.assignment.featureflagservice.service.EvaluationService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EvaluationController.class)
@Import(GlobalExceptionHandler.class)
class EvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EvaluationService evaluationService;

    private EvaluationResponse evaluationResponse;

    @BeforeEach
    void setUp() {
        evaluationResponse = new EvaluationResponse();
        evaluationResponse.setFlagKey("test-flag");
        evaluationResponse.setEnabled(true);
        evaluationResponse.setFlagVersion(1);
        evaluationResponse.setReason("Flag is enabled in prod");
        evaluationResponse.setEnvironment("prod");
    }

    @Nested
    class EvaluateFlagTests {

        @Test
        void givenValidRequest_whenEvaluateFlag_thenReturn200WithEvaluationResponse()
                throws Exception {
            EvaluationRequest request = new EvaluationRequest();
            request.setEnvironment(EvaluationRequest.EnvironmentEnum.PROD);
            when(evaluationService.evaluateFlag(eq("test-flag"), any(EvaluationRequest.class)))
                    .thenReturn(evaluationResponse);
            mockMvc.perform(post("/api/v1/flags/test-flag/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.flagKey").value("test-flag"))
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.flagVersion").value(1))
                    .andExpect(jsonPath("$.reason").value("Flag is enabled in prod"))
                    .andExpect(jsonPath("$.environment").value("prod"));
        }

        @Test
        void givenMissingEnvironment_whenEvaluateFlag_thenReturn422() throws Exception {
            EvaluationRequest request = new EvaluationRequest();
            mockMvc.perform(post("/api/v1/flags/test-flag/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.path").exists());
        }

        @Test
        void givenNonExistentFlag_whenEvaluateFlag_thenReturn404() throws Exception {
            EvaluationRequest request = new EvaluationRequest();
            request.setEnvironment(EvaluationRequest.EnvironmentEnum.PROD);
            when(evaluationService.evaluateFlag(eq("ghost-flag"), any()))
                    .thenThrow(new FlagNotFoundException("ghost-flag"));
            mockMvc.perform(post("/api/v1/flags/ghost-flag/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("FLAG_NOT_FOUND"));
        }

        @Test
        void givenInvalidEnvironmentValue_whenEvaluateFlag_thenReturn400() throws Exception {
            String invalidBody = "{\"environment\": \"invalid-env\"}";
            mockMvc.perform(post("/api/v1/flags/test-flag/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest());
        }
    }
}
package com.assignment.featureflagsdk.http;

import com.assignment.featureflagsdk.Context;
import com.assignment.featureflagsdk.EvaluationResult;
import com.assignment.featureflagsdk.exception.FeatureFlagException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class EvaluationHttpClient {

    private final String baseUrl;
    private final String environment;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;

    public EvaluationHttpClient(
            String baseUrl,
            String environment,
            Duration connectTimeout,
            Duration requestTimeout) {

        this.baseUrl = baseUrl;
        this.environment = environment;
        this.requestTimeout = requestTimeout;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
    }

    EvaluationHttpClient(
            String baseUrl,
            String environment,
            Duration requestTimeout,
            HttpClient httpClient,
            ObjectMapper objectMapper) {

        this.baseUrl = baseUrl;
        this.environment = environment;
        this.requestTimeout = requestTimeout;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public EvaluationResult evaluate(String flagKey, Context context) {
        try {
            String requestBody = buildRequestBody(context);
            String url = baseUrl + "/api/v1/flags/" + flagKey + "/evaluate";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), EvaluationResult.class);
            }

            throw new FeatureFlagException(
                    "Evaluation API returned status " + response.statusCode()
                            + " for flag: " + flagKey);

        } catch (FeatureFlagException e) {
            throw e;
        } catch (Exception e) {
            throw new FeatureFlagException(
                    "Failed to evaluate flag: " + flagKey, e);
        }
    }

    private String buildRequestBody(Context context) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("environment", environment);

        if (context != null) {
            Map<String, Object> contextMap = new HashMap<>();
            if (context.getUserId() != null) {
                contextMap.put("userId", context.getUserId());
            }
            if (context.getAttributes() != null && !context.getAttributes().isEmpty()) {
                contextMap.put("attributes", context.getAttributes());
            }
            body.put("context", contextMap);
        }

        return objectMapper.writeValueAsString(body);
    }
}
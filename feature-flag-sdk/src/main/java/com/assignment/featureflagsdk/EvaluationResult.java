package com.assignment.featureflagsdk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvaluationResult {
    private String flagKey;
    private boolean enabled;
    private Integer flagVersion;
    private String reason;
    private String environment;
}
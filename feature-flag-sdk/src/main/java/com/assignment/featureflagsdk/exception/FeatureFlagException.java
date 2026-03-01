package com.assignment.featureflagsdk.exception;

public class FeatureFlagException extends RuntimeException {

    public FeatureFlagException(String message) {
        super(message);
    }

    public FeatureFlagException(String message, Throwable cause) {
        super(message, cause);
    }
}
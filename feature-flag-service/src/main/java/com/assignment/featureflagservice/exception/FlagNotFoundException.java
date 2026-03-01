package com.assignment.featureflagservice.exception;

public class FlagNotFoundException extends RuntimeException {

    public FlagNotFoundException(String flagKey) {
        super("Feature flag not found: " + flagKey);
    }
}
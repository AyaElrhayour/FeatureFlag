package com.assignment.featureflagservice.exception;

public class FlagAlreadyExistsException extends RuntimeException {

    public FlagAlreadyExistsException(String flagKey) {
        super("Feature flag already exists: " + flagKey);
    }
}
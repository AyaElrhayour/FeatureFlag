package com.assignment.featureflagservice.exception;

public class VersionConflictException extends RuntimeException {

    public VersionConflictException(String flagKey, Integer providedVersion) {
        super("Version conflict for flag '" + flagKey +
                "': provided version " + providedVersion + " is stale");
    }
}
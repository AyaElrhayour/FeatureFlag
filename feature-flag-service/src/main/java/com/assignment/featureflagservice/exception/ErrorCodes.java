package com.assignment.featureflagservice.exception;

public final class ErrorCodes {

    private ErrorCodes() {}

    public static final String FLAG_NOT_FOUND = "FLAG_NOT_FOUND";
    public static final String FLAG_ALREADY_EXISTS = "FLAG_ALREADY_EXISTS";
    public static final String VERSION_CONFLICT = "VERSION_CONFLICT";
    public static final String INVALID_ENVIRONMENT = "INVALID_ENVIRONMENT";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
}
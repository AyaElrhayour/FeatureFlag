package com.assignment.featureflagservice.config;

import com.assignment.featureflagservice.dto.ErrorResponse;
import com.assignment.featureflagservice.exception.ErrorCodes;
import com.assignment.featureflagservice.exception.FlagAlreadyExistsException;
import com.assignment.featureflagservice.exception.FlagNotFoundException;
import com.assignment.featureflagservice.exception.VersionConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FlagNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFlagNotFound(
            FlagNotFoundException ex,
            HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(buildError(
                        HttpStatus.NOT_FOUND,
                        ErrorCodes.FLAG_NOT_FOUND,
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(FlagAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleFlagAlreadyExists(
            FlagAlreadyExistsException ex,
            HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(buildError(
                        HttpStatus.CONFLICT,
                        ErrorCodes.FLAG_ALREADY_EXISTS,
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ErrorResponse> handleVersionConflict(
            VersionConflictException ex,
            HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(buildError(
                        HttpStatus.CONFLICT,
                        ErrorCodes.VERSION_CONFLICT,
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse>                                                                                                                                                                                                                                                                                                                                                             handleValidationFailed(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(buildError(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ErrorCodes.VALIDATION_FAILED,
                        message,
                        request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        String message = "Malformed request body or invalid field value";
        if (ex.getCause() instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException ife) {
            if (ife.getTargetType() != null && ife.getTargetType().isEnum()) {
                message = "Invalid value '" + ife.getValue() +
                        "' for field. Accepted values: " +
                        java.util.Arrays.toString(ife.getTargetType().getEnumConstants());
            }
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(
                        HttpStatus.BAD_REQUEST,
                        ErrorCodes.INVALID_ENVIRONMENT,
                        message,
                        request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(
                        HttpStatus.BAD_REQUEST,
                        ErrorCodes.INVALID_ENVIRONMENT,
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_ERROR",
                        "An unexpected error occurred",
                        request.getRequestURI()));
    }

    private ErrorResponse buildError(
            HttpStatus status,
            String error,
            String message,
            String path) {
        ErrorResponse response = new ErrorResponse();
        response.setTimestamp(OffsetDateTime.now());
        response.setStatus(status.value());
        response.setError(error);
        response.setMessage(message);
        response.setPath(path);
        return response;
    }
}
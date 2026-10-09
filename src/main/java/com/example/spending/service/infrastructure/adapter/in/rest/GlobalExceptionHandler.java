package com.example.spending.service.infrastructure.adapter.in.rest;

import com.example.spending.service.application.exception.TransactionParkedException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global REST exception handler mapping application and validation exceptions
 * to standard RFC 9457 ProblemDetail responses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Validation failed for one or more fields"
        );
        problemDetail.setTitle("Validation Failed");
        problemDetail.setType(URI.create("urn:problem-type:validation-error"));
        problemDetail.setProperty("timestamp", Instant.now());

        Object target = ex.getBindingResult().getTarget();
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            String jsonName = resolveJsonPropertyName(target, error.getField());
            errors.put(jsonName, error.getDefaultMessage());
            if (!jsonName.equals(error.getField())) {
                errors.put(error.getField(), error.getDefaultMessage());
            }
        }
        problemDetail.setProperty("invalid_params", errors);

        log.warn("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problemDetail.setTitle("Missing Parameter");
        problemDetail.setType(URI.create("urn:problem-type:missing-parameter"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("parameter", ex.getParameterName());

        log.warn("Missing parameter: {}", ex.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problemDetail.setTitle("Constraint Violation");
        problemDetail.setType(URI.create("urn:problem-type:constraint-violation"));
        problemDetail.setProperty("timestamp", Instant.now());

        log.warn("Constraint violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("urn:problem-type:bad-request"));
        problemDetail.setProperty("timestamp", Instant.now());

        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(TransactionParkedException.class)
    public ResponseEntity<ProblemDetail> handleTransactionParked(TransactionParkedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMessage()
        );
        problemDetail.setTitle("Transaction Parked");
        problemDetail.setType(URI.create("urn:problem-type:transaction-parked"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("unprocessed_transaction_id", ex.getUnprocessedTransactionId());

        log.warn("Transaction parked in DLQ: id={}, reason={}", ex.getUnprocessedTransactionId(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problemDetail);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problemDetail.setTitle("Conflict");
        problemDetail.setType(URI.create("urn:problem-type:conflict"));
        problemDetail.setProperty("timestamp", Instant.now());

        log.warn("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred"
        );
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create("urn:problem-type:internal-error"));
        problemDetail.setProperty("timestamp", Instant.now());

        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    private String resolveJsonPropertyName(Object target, String fieldName) {
        if (target != null && fieldName != null) {
            try {
                Class<?> clazz = target.getClass();
                if (clazz.isRecord()) {
                    for (RecordComponent rc : clazz.getRecordComponents()) {
                        if (rc.getName().equals(fieldName)) {
                            JsonProperty prop = rc.getAnnotation(JsonProperty.class);
                            if (prop != null && !prop.value().isBlank()) {
                                return prop.value();
                            }
                        }
                    }
                }
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.getName().equals(fieldName)) {
                        JsonProperty prop = f.getAnnotation(JsonProperty.class);
                        if (prop != null && !prop.value().isBlank()) {
                            return prop.value();
                        }
                    }
                }
            } catch (Exception ignored) {
                // fallback to snake_case
            }
        }
        return toSnakeCase(fieldName);
    }

    private String toSnakeCase(String name) {
        if (name == null) {
            return null;
        }
        return name.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }
}

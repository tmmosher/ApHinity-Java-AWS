package com.aphinity.client_analytics_core.api.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Map<String, ErrorDefinition> SAFE_REASONS = Map.ofEntries(
        Map.entry("Invalid credentials", new ErrorDefinition("invalid_credentials", "Invalid credentials")),
        Map.entry("Invalid refresh token", new ErrorDefinition("invalid_refresh_token", "Invalid refresh token")),
        Map.entry("Missing refresh token", new ErrorDefinition("missing_refresh_token", "Missing refresh token")),
        Map.entry("Email already in use", new ErrorDefinition("email_in_use", "Email already in use")),
        Map.entry("Must be at least 8 characters", new ErrorDefinition("password_too_short", "Must be at least 8 characters")),
        Map.entry("Must contain at least one digit", new ErrorDefinition("password_missing_digit", "Must contain at least one digit")),
        Map.entry("Must contain at least one letter", new ErrorDefinition("password_missing_letter", "Must contain at least one letter")),
        Map.entry("Must contain at least one special character", new ErrorDefinition("password_missing_special", "Must contain at least one special character")),
        Map.entry("Password must be at least 8 characters long", new ErrorDefinition("password_too_short", "Password must be at least 8 characters long")),
        Map.entry("Captcha required", new ErrorDefinition("captcha_required", "Captcha required")),
        Map.entry("Invalid captcha", new ErrorDefinition("captcha_invalid", "Invalid captcha")),
        Map.entry("Captcha not configured", new ErrorDefinition("captcha_unavailable", "Captcha not configured"))
    );

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        ErrorDefinition definition = safeDefinition(ex.getReason());
        int status = ex.getStatusCode().value();
        ApiErrorResponse response = new ApiErrorResponse(
            definition.code(),
            definition.message(),
            status,
            Instant.now(),
            Map.of()
        );
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            String field = error.getField();
            if (fieldErrors.containsKey(field)) {
                continue;
            }
            fieldErrors.put(field, mapValidationCode(error.getCode()));
        }
        ApiErrorResponse response = new ApiErrorResponse(
            "validation_failed",
            "Validation failed",
            HttpStatus.BAD_REQUEST.value(),
            Instant.now(),
            fieldErrors
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBody(HttpMessageNotReadableException ex) {
        ApiErrorResponse response = new ApiErrorResponse(
            "invalid_request_body",
            "Request body is invalid",
            HttpStatus.BAD_REQUEST.value(),
            Instant.now(),
            Map.of()
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        ApiErrorResponse response = new ApiErrorResponse(
            "internal_error",
            "Unexpected error",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            Instant.now(),
            Map.of()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private ErrorDefinition safeDefinition(String reason) {
        if (reason != null) {
            ErrorDefinition definition = SAFE_REASONS.get(reason);
            if (definition != null) {
                return definition;
            }
        }
        return new ErrorDefinition("request_failed", "Request failed");
    }

    private String mapValidationCode(String code) {
        if (code == null || code.isBlank()) {
            return "invalid";
        }
        return switch (code) {
            case "NotBlank", "NotNull" -> "required";
            case "Email" -> "invalid_email";
            case "Size" -> "invalid_length";
            case "Pattern" -> "invalid_format";
            case "Min" -> "too_small";
            case "Max" -> "too_large";
            default -> code.toLowerCase(Locale.ROOT);
        };
    }

    private record ErrorDefinition(String code, String message) {
    }
}

package com.aphinity.client_analytics_core.api.error;

import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Centralized exception-to-response mapping for API endpoints.
 * The handler keeps client-facing messages intentionally safe while still logging
 * detailed context for troubleshooting.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private final AsyncLogService logService;

    // Only reasons explicitly listed here are surfaced directly to API clients.
    private static final Map<String, ErrorDefinition> SAFE_REASONS = Map.ofEntries(
        Map.entry("Invalid credentials", new ErrorDefinition("invalid_credentials", "Invalid credentials")),
        Map.entry("Invalid refresh token", new ErrorDefinition("invalid_refresh_token", "Invalid refresh token")),
        Map.entry("Invalid recovery code", new ErrorDefinition("invalid_recovery_code", "Invalid recovery code")),
        Map.entry("Invalid authenticated user", new ErrorDefinition("invalid_authenticated_user", "Invalid authenticated user")),
        Map.entry("Missing refresh token", new ErrorDefinition("missing_refresh_token", "Missing refresh token")),
        Map.entry("Email already in use", new ErrorDefinition("email_in_use", "Email already in use")),
        Map.entry("Role configuration invalid", new ErrorDefinition("role_configuration_invalid", "Role configuration invalid")),
        Map.entry("Must be at least 8 characters", new ErrorDefinition("password_too_short", "Must be at least 8 characters")),
        Map.entry("Must contain at least one digit", new ErrorDefinition("password_missing_digit", "Must contain at least one digit")),
        Map.entry("Must contain at least one letter", new ErrorDefinition("password_missing_letter", "Must contain at least one letter")),
        Map.entry("Must contain at least one special character", new ErrorDefinition("password_missing_special", "Must contain at least one special character")),
        Map.entry("Password must be at least 8 characters long", new ErrorDefinition("password_too_short", "Password must be at least 8 characters long")),
        Map.entry("Current password is incorrect", new ErrorDefinition("password_incorrect", "Current password is incorrect")),
        Map.entry("Password change unavailable for this account", new ErrorDefinition("password_change_unavailable", "Password change unavailable for this account")),
        Map.entry("New password must be different from current password", new ErrorDefinition("password_reused", "New password must be different from current password")),
        Map.entry("Captcha required", new ErrorDefinition("captcha_required", "Captcha required")),
        Map.entry("Invalid captcha", new ErrorDefinition("captcha_invalid", "Invalid captcha")),
        Map.entry("Invalid captcha token", new ErrorDefinition("captcha_invalid", "Invalid captcha token")),
        Map.entry("Captcha not configured", new ErrorDefinition("captcha_unavailable", "Captcha not configured")),
        Map.entry("Unable to send recovery email", new ErrorDefinition("recovery_email_unavailable", "Unable to send recovery email")),
        Map.entry("Unable to send verification email", new ErrorDefinition("verification_email_unavailable", "Unable to send verification email")),
        Map.entry("Unable to issue verification code", new ErrorDefinition("verification_code_issue_failed", "Unable to issue verification code")),
        Map.entry("Account email is not verified", new ErrorDefinition("account_not_verified", "Account email is not verified")),
        Map.entry("Insufficient permissions", new ErrorDefinition("forbidden", "Insufficient permissions")),
        Map.entry("Location not found", new ErrorDefinition("location_not_found", "Location not found")),
        Map.entry("Target user not found", new ErrorDefinition("target_user_not_found", "Target user not found")),
        Map.entry("Location name is required", new ErrorDefinition("location_name_required", "Location name is required")),
        Map.entry("Location name already in use", new ErrorDefinition("location_name_in_use", "Location name already in use")),
        Map.entry("Invite not found", new ErrorDefinition("invite_not_found", "Invite not found")),
        Map.entry("Invite expired", new ErrorDefinition("invite_expired", "Invite expired")),
        Map.entry("Invite is not pending", new ErrorDefinition("invite_not_pending", "Invite is not pending")),
        Map.entry("Invite email does not match authenticated account", new ErrorDefinition("invite_email_mismatch", "Invite email does not match authenticated account")),
        Map.entry("An active invite already exists", new ErrorDefinition("invite_already_exists", "An active invite already exists")),
        Map.entry("Cannot invite your own account", new ErrorDefinition("self_invite_not_allowed", "Cannot invite your own account")),
        Map.entry("User already has access to this location", new ErrorDefinition("location_access_exists", "User already has access to this location")),
        Map.entry("Invited email is required", new ErrorDefinition("invited_email_required", "Invited email is required")),
        Map.entry("Unable to issue invite", new ErrorDefinition("invite_issue_failed", "Unable to issue invite"))
    );

    /**
     * @param logService asynchronous logging service for handled/unhandled exception diagnostics
     */
    public ApiExceptionHandler(AsyncLogService logService) {
        this.logService = logService;
    }

    /**
     * Handles explicit {@link ResponseStatusException}s raised by application code.
     *
     * @param ex thrown exception
     * @return normalized API error payload with the source status code
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        ErrorDefinition definition = safeDefinition(ex.getReason());
        int status = ex.getStatusCode().value();
        logService.log(formatHandledException(
            "ResponseStatusException",
            ex,
            "status=" + status + ", reason=" + safeMessage(ex.getReason())
        ));
        ApiErrorResponse response = new ApiErrorResponse(
            definition.code(),
            definition.message(),
            status,
            Instant.now(),
            Map.of()
        );

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Handles bean validation failures and returns field-level error codes.
     *
     * @param ex validation exception
     * @return validation error payload
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            String field = error.getField();
            // Keep the first failure per field so responses remain deterministic.
            if (fieldErrors.containsKey(field)) {
                continue;
            }
            fieldErrors.put(field, mapValidationCode(error.getCode()));
        }
        logService.log(formatHandledException(
            "MethodArgumentNotValidException",
            ex,
            "fields=" + fieldErrors
        ));
        ApiErrorResponse response = new ApiErrorResponse(
            "validation_failed",
            "Validation failed",
            HttpStatus.BAD_REQUEST.value(),
            Instant.now(),
            fieldErrors
        );
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles malformed JSON or invalid request bodies.
     *
     * @param ex parse/deserialize exception
     * @return bad request payload
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBody(HttpMessageNotReadableException ex) {
        logService.log(formatHandledException("HttpMessageNotReadableException", ex, null));
        ApiErrorResponse response = new ApiErrorResponse(
            "invalid_request_body",
            "Request body is invalid",
            HttpStatus.BAD_REQUEST.value(),
            Instant.now(),
            Map.of()
        );
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles authorization failures, including CSRF token failures.
     *
     * @param ex access denied exception
     * @return forbidden payload
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        boolean csrfError = ex instanceof MissingCsrfTokenException || ex instanceof InvalidCsrfTokenException;
        String code = csrfError ? "csrf_invalid" : "forbidden";
        String message = csrfError ? "Missing or invalid CSRF token" : "Insufficient permissions";
        logService.log(formatHandledException("AccessDeniedException", ex, "code=" + code));
        ApiErrorResponse response = new ApiErrorResponse(
            code,
            message,
            HttpStatus.FORBIDDEN.value(),
            Instant.now(),
            Map.of()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Fallback handler for uncaught exceptions.
     *
     * @param ex unexpected exception
     * @return internal server error payload
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        logService.log(formatUnhandledException(ex));
        ApiErrorResponse response = new ApiErrorResponse(
            "internal_error",
            "Unexpected error",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            Instant.now(),
            Map.of()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Resolves a safe client-facing definition for a reason string.
     *
     * @param reason exception reason
     * @return mapped safe definition or generic fallback
     */
    private ErrorDefinition safeDefinition(String reason) {
        if (reason != null) {
            ErrorDefinition definition = SAFE_REASONS.get(reason);
            if (definition != null) {
                return definition;
            }
        }
        return new ErrorDefinition("request_failed", "Request failed");
    }

    /**
     * Builds a concise structured log line for handled exceptions.
     *
     * @param label exception category label
     * @param ex handled exception
     * @param detail optional contextual detail
     * @return formatted log message
     */
    private String formatHandledException(String label, Exception ex, String detail) {
        StringBuilder message = new StringBuilder("Handled exception: ");
        message.append(label);
        if (detail != null && !detail.isBlank()) {
            message.append(" | ").append(detail);
        }
        if (ex != null) {
            message.append(" | type=").append(ex.getClass().getSimpleName());
            String exMessage = safeMessage(ex.getMessage());
            if (!exMessage.isBlank()) {
                message.append(" | message=").append(exMessage);
            }
        }
        return message.toString();
    }

    /**
     * Builds a detailed log line for unexpected exceptions.
     *
     * @param ex unexpected exception
     * @return formatted log message with stack trace
     */
    private String formatUnhandledException(Exception ex) {
        StringBuilder message = new StringBuilder("Unhandled exception");
        if (ex != null) {
            message.append(" | type=").append(ex.getClass().getSimpleName());
            String exMessage = safeMessage(ex.getMessage());
            if (!exMessage.isBlank()) {
                message.append(" | message=").append(exMessage);
            }
            message.append(" | stack=").append(stackTrace(ex));
        }
        return message.toString();
    }

    /**
     * Converts nullable exception messages into safe non-null strings.
     *
     * @param message nullable source message
     * @return empty string when input is null
     */
    private String safeMessage(String message) {
        return message == null ? "" : message;
    }

    /**
     * Captures a throwable stack trace as text for logging.
     *
     * @param throwable source throwable
     * @return trimmed stack trace string
     */
    private String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(writer)) {
            throwable.printStackTrace(printWriter);
        }
        return writer.toString().trim();
    }

    /**
     * Maps Bean Validation constraint codes to API field error codes.
     *
     * @param code bean validation code
     * @return normalized field error code
     */
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

    /**
     * Immutable client-facing error metadata.
     *
     * @param code stable API error code
     * @param message safe API error message
     */
    private record ErrorDefinition(String code, String message) {
    }
}

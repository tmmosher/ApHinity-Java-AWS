package com.aphinity.client_analytics_core.api.error;

import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApiExceptionHandlerTest {
    @Mock
    private AsyncLogService logService;

    @InjectMocks
    private ApiExceptionHandler apiExceptionHandler;

    @Test
    void handleResponseStatusMapsInvitedUserNotFoundReason() {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.NOT_FOUND, "Invited user not found");

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleResponseStatus(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("invited_user_not_found", response.getBody().code());
        assertEquals("Invited user not found", response.getBody().message());
    }

    @Test
    void handleResponseStatusMapsInvitedAccountNotVerifiedReason() {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.FORBIDDEN, "Invited account email is not verified");

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleResponseStatus(exception);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("invited_account_not_verified", response.getBody().code());
        assertEquals("Invited account email is not verified", response.getBody().message());
    }

    @Test
    void handleResponseStatusMapsGanttDependencyValidationReasons() {
        ResponseStatusException selfDependency = new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Task cannot depend on itself"
        );
        ResponseStatusException invalidLocation = new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Task dependencies must belong to this location"
        );

        ResponseEntity<ApiErrorResponse> selfDependencyResponse = apiExceptionHandler.handleResponseStatus(selfDependency);
        ResponseEntity<ApiErrorResponse> invalidLocationResponse = apiExceptionHandler.handleResponseStatus(invalidLocation);

        assertEquals("task_dependency_self_reference", selfDependencyResponse.getBody().code());
        assertEquals("Task cannot depend on itself", selfDependencyResponse.getBody().message());
        assertEquals("task_dependency_invalid_location", invalidLocationResponse.getBody().code());
        assertEquals("Task dependencies must belong to this location", invalidLocationResponse.getBody().message());
    }

    @Test
    void handleResponseStatusMapsMissingLocationWorkOrderEmailReason() {
        ResponseStatusException exception = new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Location work-order email is required"
        );

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleResponseStatus(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("location_work_order_email_required", response.getBody().code());
        assertEquals("Location work-order email is required", response.getBody().message());
    }

    @Test
    void handleResponseStatusMapsWorkOrderEmailDeliveryReason() {
        ResponseStatusException exception = new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Unable to send work-order email"
        );

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleResponseStatus(exception);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("work_order_email_unavailable", response.getBody().code());
        assertEquals("Unable to send work-order email", response.getBody().message());
    }

    @Test
    void handleUnexpectedSuppressesNoResourceStackTraceInMainLog() {
        NoResourceFoundException exception = new NoResourceFoundException(
            HttpMethod.GET,
            "assets/.env.js",
            "/assets/.env.js"
        );

        apiExceptionHandler.handleUnexpected(exception);

        ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(logService).log(logMessageCaptor.capture());
        String logMessage = logMessageCaptor.getValue();
        assertTrue(logMessage.contains("type=NoResourceFoundException"));
        assertTrue(logMessage.contains("No static resource"));
        assertFalse(logMessage.contains("| stack="));
    }

    @Test
    void handleUnexpectedKeepsStackTraceForOtherExceptions() {
        RuntimeException exception = new RuntimeException("boom");

        apiExceptionHandler.handleUnexpected(exception);

        ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(logService).log(logMessageCaptor.capture());
        String logMessage = logMessageCaptor.getValue();
        assertTrue(logMessage.contains("type=RuntimeException"));
        assertTrue(logMessage.contains("| stack="));
    }

    @Test
    void handleResponseStatusFallsBackToGenericErrorForUnknownReason() {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown reason");

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleResponseStatus(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("request_failed", response.getBody().code());
        assertEquals("Request failed", response.getBody().message());
    }

    @Test
    void handleValidationReturnsFirstErrorPerFieldAndMapsCodes() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", null, false, new String[]{"NotNull"}, null, null));
        bindingResult.addError(new FieldError("request", "email", null, false, new String[]{"Email"}, null, null));
        bindingResult.addError(new FieldError("request", "age", null, false, new String[]{"Min"}, null, null));

        MethodParameter parameter;
        try {
            Method method = ApiExceptionHandlerTest.class.getDeclaredMethod("sampleValidatedParameter", Object.class);
            parameter = new MethodParameter(method, 0);
        } catch (NoSuchMethodException ex) {
            throw new AssertionError("Failed to create method parameter for validation test", ex);
        }

        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
            parameter,
            bindingResult
        );

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("validation_failed", response.getBody().code());
        assertEquals("required", response.getBody().fieldErrors().get("email"));
        assertEquals("too_small", response.getBody().fieldErrors().get("age"));
        assertEquals(2, response.getBody().fieldErrors().size());
    }

    @Test
    void handleAccessDeniedReturnsCsrfErrorForMissingCsrfTokenException() {
        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleAccessDenied(
            new MissingCsrfTokenException("missing-token")
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("csrf_invalid", response.getBody().code());
        assertEquals("Missing or invalid CSRF token", response.getBody().message());
    }

    @Test
    void handleAccessDeniedReturnsForbiddenForGenericAccessDeniedException() {
        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleAccessDenied(
            new AccessDeniedException("forbidden")
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("forbidden", response.getBody().code());
        assertEquals("Insufficient permissions", response.getBody().message());
    }

    @Test
    void handleInvalidBodyReturnsNormalizedBadRequestPayload() {
        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleInvalidBody(
            new HttpMessageNotReadableException("bad body", mock(HttpInputMessage.class))
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("invalid_request_body", response.getBody().code());
        assertEquals("Request body is invalid", response.getBody().message());
        assertNotNull(response.getBody().timestamp());
    }

    private static void sampleValidatedParameter(Object value) {
        // helper signature used to construct a stable MethodParameter for tests
    }
}

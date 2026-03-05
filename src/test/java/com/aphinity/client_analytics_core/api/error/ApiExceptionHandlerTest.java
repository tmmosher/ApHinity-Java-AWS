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
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}

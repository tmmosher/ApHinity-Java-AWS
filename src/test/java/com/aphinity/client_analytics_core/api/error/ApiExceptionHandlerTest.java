package com.aphinity.client_analytics_core.api.error;

import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

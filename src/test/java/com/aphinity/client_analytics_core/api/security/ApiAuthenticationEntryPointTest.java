package com.aphinity.client_analytics_core.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiAuthenticationEntryPointTest {
    private final ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint();

    @Test
    void writesStandardUnauthorizedJsonResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/core/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("bad"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("\"code\":\"authentication_required\""));
        assertTrue(response.getContentAsString().contains("\"message\":\"Authentication required\""));
    }
}

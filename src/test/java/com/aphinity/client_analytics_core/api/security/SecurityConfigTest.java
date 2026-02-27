package com.aphinity.client_analytics_core.api.security;

import com.aphinity.client_analytics_core.api.auth.services.AuthCookieService;
import com.aphinity.client_analytics_core.api.auth.services.AuthService;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
@Import({
    SecurityConfig.class,
    ApiAuthenticationEntryPoint.class,
    CsrfCookieFilter.class,
    SecurityConfigTest.SecurityTestBeans.class
})
@TestPropertySource(properties = {
    "security.jwt.issuer=test-issuer",
    "security.jwt.secret=0123456789abcdef0123456789abcdef",
    "security.jwt.access-token-ttl-seconds=900",
    "security.jwt.refresh-token-ttl-seconds=3600"
})
class SecurityConfigTest {
    private static final Pattern SCRIPT_NONCE_PATTERN = Pattern.compile(
        "script-src 'self' 'nonce-([A-Za-z0-9_-]+)' https://challenges.cloudflare.com;"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CookieCsrfTokenRepository csrfTokenRepository;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthCookieService authCookieService;

    @MockitoBean
    private ClientRequestMetadataResolver clientRequestMetadataResolver;

    @MockitoBean
    private AsyncLogService asyncLogService;

    @Test
    void usesApiAuthenticationEntryPointForApiRoutes() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/core/probe"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"code\":\"authentication_required\"")))
            .andReturn();

        String cspHeader = result.getResponse().getHeader("Content-Security-Policy");
        org.assertj.core.api.Assertions.assertThat(cspHeader)
            .isNotBlank()
            .contains("frame-src 'self' https://challenges.cloudflare.com;")
            .contains("style-src-elem 'self' 'unsafe-inline';")
            .contains("style-src-attr 'unsafe-inline';");

        Matcher nonceMatcher = SCRIPT_NONCE_PATTERN.matcher(cspHeader);
        org.assertj.core.api.Assertions.assertThat(nonceMatcher.find()).isTrue();
        org.assertj.core.api.Assertions.assertThat(nonceMatcher.group(1)).isNotBlank();
    }

    @Test
    void usesRedirectEntryPointForNonApiRoutes() throws Exception {
        mockMvc.perform(get("/private"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void csrfCookieRepositoryUsesStrictSameSite() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("");
        MockHttpServletResponse response = new MockHttpServletResponse();

        CsrfToken token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);

        var csrfCookie = response.getCookie("XSRF-TOKEN");
        org.assertj.core.api.Assertions.assertThat(csrfCookie).isNotNull();
        org.assertj.core.api.Assertions.assertThat(csrfCookie.getAttribute("SameSite")).isEqualTo("Strict");
    }

    @Test
    void cspNonceIsRotatedPerRequest() throws Exception {
        String firstHeader = mockMvc.perform(get("/api/core/probe"))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getHeader("Content-Security-Policy");

        String secondHeader = mockMvc.perform(get("/api/core/probe"))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getHeader("Content-Security-Policy");

        String firstNonce = extractScriptNonce(firstHeader);
        String secondNonce = extractScriptNonce(secondHeader);

        org.assertj.core.api.Assertions.assertThat(firstNonce).isNotEqualTo(secondNonce);
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/core/probe")
        String apiProbe() {
            return "ok";
        }

        @GetMapping("/private")
        String privateProbe() {
            return "ok";
        }
    }

    @TestConfiguration
    static class SecurityTestBeans {
        @Bean
        AccessTokenRefreshFilter accessTokenRefreshFilter(
            AuthService authService,
            AuthCookieService authCookieService,
            ClientRequestMetadataResolver clientRequestMetadataResolver,
            JwtProperties jwtProperties
        ) {
            return new AccessTokenRefreshFilter(
                authService,
                authCookieService,
                clientRequestMetadataResolver,
                jwtProperties
            );
        }
    }

    private String extractScriptNonce(String cspHeader) {
        Matcher nonceMatcher = SCRIPT_NONCE_PATTERN.matcher(cspHeader);
        org.assertj.core.api.Assertions.assertThat(nonceMatcher.find()).isTrue();
        return nonceMatcher.group(1);
    }
}

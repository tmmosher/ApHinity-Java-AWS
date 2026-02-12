package com.aphinity.client_analytics_core.web;

import com.aphinity.client_analytics_core.api.security.AccessTokenRefreshFilter;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = WebConfigTest.ProbeController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = AccessTokenRefreshFilter.class
    )
)
@Import(WebConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class WebConfigTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AsyncLogService asyncLogService;

    @Test
    void forwardsDashboardNestedRoutesToSpaRoot() throws Exception {
        mockMvc.perform(get("/dashboard/locations/123"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/"));
    }

    @Test
    void doesNotForwardMappedApiRoutes() throws Exception {
        mockMvc.perform(get("/api/echo"))
            .andExpect(status().isInternalServerError())
            .andExpect(forwardedUrl(null));
    }

    @Test
    void doesNotForwardUnknownApiRoutes() throws Exception {
        mockMvc.perform(get("/api/unknown"))
            .andExpect(status().isInternalServerError())
            .andExpect(forwardedUrl(null));
    }

    @RestController
    public static class ProbeController {
        @GetMapping("/")
        public String index() {
            return "index";
        }

        @GetMapping("/api/echo")
        public String apiEcho() {
            return "api";
        }
    }
}

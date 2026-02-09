package com.aphinity.client_analytics_core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class StaticCacheControlFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        filterChain.doFilter(request, response);

        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return;
        }
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            return;
        }

        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            return;
        }

        if (path.startsWith("/assets/")) {
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
            return;
        }

        if ("/".equals(path) || "/index.html".equals(path) || isSpaRoute(path)) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        }
    }

    private boolean isSpaRoute(String path) {
        if (path.startsWith("/api/")) {
            return false;
        }
        if (path.startsWith("/actuator")) {
            return false;
        }
        return !path.contains(".");
    }
}

package com.aphinity.client_analytics_core.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class StaticResourceExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(StaticResourceExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(
        NoResourceFoundException exception,
        HttpServletRequest request
    ) {
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/assets/")) {
            log.warn("Static asset not found path={}", path);
            return ResponseEntity
                .notFound()
                .cacheControl(CacheControl.noStore())
                .build();
        }

        return ResponseEntity.notFound().build();
    }
}

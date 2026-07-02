package com.aphinity.client_analytics_core.api.core.services.servicecalendar;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the packaged service-calendar import template for download endpoints.
 */
@Service
public class ServiceCalendarTemplateService {
    private static final String SERVICE_CALENDAR_TEMPLATE_PATH =
            "templates/service_calendar_template.xlsx";

    /**
     * Returns the packaged service-calendar template resource.
     *
     * @return classpath template resource
     */
    public Resource getTemplate() {
        Resource resource = new ClassPathResource(SERVICE_CALENDAR_TEMPLATE_PATH);
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Service calendar template unavailable");
        }
        return resource;
    }
}

package com.aphinity.client_analytics_core.api.core.services.gantt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GanttChartTemplateService {
    private static final String GANTT_CHART_TEMPLATE_PATH = "templates/gantt_chart_template.xlsx";

    public Resource getTemplate() {
        Resource resource = new ClassPathResource(GANTT_CHART_TEMPLATE_PATH);
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Gantt chart template unavailable");
        }
        return resource;
    }
}

package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;


/** Shared composition for dashboard import and range-projection collaborators. */
@Configuration
public class DashboardImportComposition {
    @Bean
    @Qualifier("dashboardImportObjectMapper")
    ObjectMapper dashboardImportObjectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

}

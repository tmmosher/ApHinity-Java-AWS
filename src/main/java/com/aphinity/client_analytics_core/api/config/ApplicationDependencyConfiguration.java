package com.aphinity.client_analytics_core.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Cross-cutting application dependency composition. */
@Configuration
public class ApplicationDependencyConfiguration {
    @Bean
    Clock applicationClock() {
        return Clock.system(ZoneId.of("America/Phoenix"));
    }
}

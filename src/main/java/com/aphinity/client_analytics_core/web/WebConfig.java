package com.aphinity.client_analytics_core.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        String[] directRoutes = {
            "/login",
            "/signup",
            "/support",
            "/recovery",
            "/verify",
            "/dashboard",
            "/dashboard/"
        };
        for (String route : directRoutes) {
            registry.addViewController(route).setViewName("forward:/");
        }
        registry.addViewController("/dashboard/{*path}").setViewName("forward:/");
    }
}


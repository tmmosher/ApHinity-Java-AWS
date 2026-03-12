package com.aphinity.client_analytics_core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClientAnalyticsCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClientAnalyticsCoreApplication.class, args);
	}
}

package com.aphinity.client_analytics_core.api.auth.properties;

import io.awspring.cloud.ses.SimpleEmailServiceJavaMailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import software.amazon.awssdk.services.ses.SesClient;

@Configuration
@ConditionalOnClass({JavaMailSender.class, SimpleEmailServiceJavaMailSender.class, SesClient.class})
public class SesMailConfig {

    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    public JavaMailSender javaMailSender(SesClient sesClient) {
        return new SimpleEmailServiceJavaMailSender(sesClient);
    }
}

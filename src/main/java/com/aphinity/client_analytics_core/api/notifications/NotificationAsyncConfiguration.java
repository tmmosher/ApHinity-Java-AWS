package com.aphinity.client_analytics_core.api.notifications;

import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures the executor used for asynchronous notification delivery.
 */
@Configuration
@EnableAsync
public class NotificationAsyncConfiguration implements AsyncConfigurer {
    private final AsyncLogService asyncLogService;

    public NotificationAsyncConfiguration(AsyncLogService asyncLogService) {
        this.asyncLogService = asyncLogService;
    }

    @Bean(name = "mailTaskExecutor")
    public TaskExecutor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return mailTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            if (throwable instanceof MailException) {
                return;
            }
            asyncLogService.log(
                "Async notification task failed | method=" + method.getName()
                    + ", errorType=" + throwable.getClass().getSimpleName()
                    + ", errorMessage=" + sanitize(throwable.getMessage())
            );
        };
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}

package com.aphinity.client_analytics_core.logging;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for asynchronous application logging.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "app.logging")
public class AppLoggingProperties {
    @NotBlank
    private String directory = "logs";

    @NotBlank
    private String filePrefix = "aphinity";

    @Min(1)
    private int queueCapacity = 10000;

    @Min(250)
    private long flushIntervalMs = 1000;

    /**
     * @return directory where log files are written
     */
    public String getDirectory() {
        return directory;
    }

    /**
     * @param directory directory where log files are written
     */
    public void setDirectory(String directory) {
        this.directory = directory;
    }

    /**
     * @return log filename prefix
     */
    public String getFilePrefix() {
        return filePrefix;
    }

    /**
     * @param filePrefix log filename prefix
     */
    public void setFilePrefix(String filePrefix) {
        this.filePrefix = filePrefix;
    }

    /**
     * @return in-memory log queue capacity
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * @param queueCapacity in-memory log queue capacity
     */
    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    /**
     * @return scheduled flush interval in milliseconds
     */
    public long getFlushIntervalMs() {
        return flushIntervalMs;
    }

    /**
     * @param flushIntervalMs scheduled flush interval in milliseconds
     */
    public void setFlushIntervalMs(long flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }
}

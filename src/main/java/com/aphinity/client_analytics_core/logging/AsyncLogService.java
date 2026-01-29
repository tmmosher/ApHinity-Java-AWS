package com.aphinity.client_analytics_core.logging;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AsyncLogService {
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter LINE_DATE_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AppLoggingProperties properties;
    private final BlockingQueue<LogEntry> queue;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicLong droppedCount = new AtomicLong(0);
    private final Object flushLock = new Object();
    private final ZoneId zoneId = ZoneId.systemDefault();

    private volatile LocalDate currentDate;
    private BufferedWriter writer;

    public AsyncLogService(AppLoggingProperties properties) {
        this.properties = properties;
        this.queue = new ArrayBlockingQueue<>(properties.getQueueCapacity());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new LogThreadFactory());
    }

    public void log(String message) {
        log(Instant.now(), message);
    }

    public void log(Instant timestamp, String message) {
        if (!accepting.get()) {
            return;
        }
        Instant safeTimestamp = timestamp != null ? timestamp : Instant.now();
        String safeMessage = message != null ? message : "";
        boolean enqueued = queue.offer(new LogEntry(safeTimestamp, safeMessage));
        if (!enqueued) {
            droppedCount.incrementAndGet();
        }
    }

    @PostConstruct
    void start() {
        scheduler.scheduleAtFixedRate(
            this::flushSafely,
            properties.getFlushIntervalMs(),
            properties.getFlushIntervalMs(),
            TimeUnit.MILLISECONDS
        );
    }

    @PreDestroy
    void stop() {
        accepting.set(false);
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
        flushSafely();
        closeWriter();
    }

    private void flushSafely() {
        synchronized (flushLock) {
            try {
                flushOnce();
            } catch (Exception ex) {
                System.err.println("AsyncLogService flush failed");
                ex.printStackTrace();
            }
        }
    }

    private void flushOnce() throws IOException {
        LocalDate now = LocalDate.now(zoneId);
        updateDateIfNeeded(now);

        long droppedSnapshot = droppedCount.get();
        if (queue.isEmpty() && droppedSnapshot == 0) {
            return;
        }

        ensureWriter(now);
        if (writer == null) {
            return;
        }

        List<LogEntry> entries = new ArrayList<>();
        queue.drainTo(entries);
        int index = 0;
        try {
            for (; index < entries.size(); index++) {
                LogEntry entry = entries.get(index);
                writer.write(formatEntry(entry));
                writer.newLine();
            }
            if (droppedSnapshot > 0) {
                writer.write(formatEntry(
                    new LogEntry(Instant.now(), "Dropped " + droppedSnapshot + " log entries due to full queue")
                ));
                writer.newLine();
            }
            writer.flush();
            if (droppedSnapshot > 0) {
                droppedCount.addAndGet(-droppedSnapshot);
            }
        } catch (IOException ex) {
            requeueRemaining(entries, index);
            closeWriter();
            throw ex;
        }
    }

    private void updateDateIfNeeded(LocalDate now) {
        if (currentDate == null || !currentDate.equals(now)) {
            closeWriter();
            currentDate = now;
        }
    }

    private void ensureWriter(LocalDate now) throws IOException {
        if (writer == null) {
            writer = openWriter(now);
        }
    }

    private void requeueRemaining(List<LogEntry> entries, int startIndex) {
        for (int i = startIndex; i < entries.size(); i++) {
            if (!queue.offer(entries.get(i))) {
                droppedCount.incrementAndGet();
            }
        }
    }

    private BufferedWriter openWriter(LocalDate date) throws IOException {
        Path directory = Paths.get(properties.getDirectory());
        Files.createDirectories(directory);
        String filename = properties.getFilePrefix() + "-" + FILE_DATE_FORMAT.format(date) + ".log";
        Path file = directory.resolve(filename);
        return Files.newBufferedWriter(
            file,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
            StandardOpenOption.WRITE
        );
    }

    private void closeWriter() {
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            System.err.println("AsyncLogService failed to close writer");
            ex.printStackTrace();
        } finally {
            writer = null;
        }
    }

    private String formatEntry(LogEntry entry) {
        Instant timestamp = entry.timestamp() != null ? entry.timestamp() : Instant.now();
        String time = LINE_DATE_FORMAT.format(timestamp.atZone(zoneId));
        return time + " " + sanitize(entry.message());
    }

    private String sanitize(String message) {
        if (message == null) {
            return "";
        }
        return message.replace("\r", "\\r").replace("\n", "\\n");
    }

    private static final class LogThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "app-log-writer");
            thread.setDaemon(true);
            return thread;
        }
    }
}

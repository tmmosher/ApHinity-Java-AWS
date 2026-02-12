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

/**
 * Asynchronous file logger with bounded in-memory buffering and periodic flush.
 */
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

    /**
     * @param properties logging configuration properties
     */
    public AsyncLogService(AppLoggingProperties properties) {
        this.properties = properties;
        this.queue = new ArrayBlockingQueue<>(properties.getQueueCapacity());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new LogThreadFactory());
    }

    /**
     * Enqueues a log line with the current timestamp.
     *
     * @param message log message
     */
    public void log(String message) {
        log(Instant.now(), message);
    }

    /**
     * Enqueues a timestamped log entry.
     *
     * @param timestamp entry timestamp, defaults to now when null
     * @param message entry message, defaults to empty when null
     */
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

    /**
     * Starts periodic background flush scheduling.
     */
    @PostConstruct
    void start() {
        scheduler.scheduleAtFixedRate(
            this::flushSafely,
            properties.getFlushIntervalMs(),
            properties.getFlushIntervalMs(),
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Stops scheduling, flushes remaining entries, and closes file resources.
     */
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

    /**
     * Guards flush execution so only one flush runs at a time.
     */
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

    /**
     * Drains the queue and appends entries to the active daily log file.
     *
     * @throws IOException when writing fails
     */
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
        // Drain first so producer threads stay minimally blocked during file I/O.
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
            // Preserve entries that were not written yet so they can be retried later.
            requeueRemaining(entries, index);
            closeWriter();
            throw ex;
        }
    }

    /**
     * Rotates the writer when the local date changes.
     *
     * @param now current local date
     */
    private void updateDateIfNeeded(LocalDate now) {
        if (currentDate == null || !currentDate.equals(now)) {
            closeWriter();
            currentDate = now;
        }
    }

    /**
     * Lazily opens the active day log file writer.
     *
     * @param now current local date
     * @throws IOException when opening fails
     */
    private void ensureWriter(LocalDate now) throws IOException {
        if (writer == null) {
            writer = openWriter(now);
        }
    }

    /**
     * Requeues unwritten entries after a flush failure.
     *
     * @param entries drained entries
     * @param startIndex first index that was not persisted
     */
    private void requeueRemaining(List<LogEntry> entries, int startIndex) {
        for (int i = startIndex; i < entries.size(); i++) {
            if (!queue.offer(entries.get(i))) {
                droppedCount.incrementAndGet();
            }
        }
    }

    /**
     * Opens/creates the log file for the provided date.
     *
     * @param date log-file date
     * @return append-mode writer
     * @throws IOException when opening fails
     */
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

    /**
     * Flushes and closes the active writer if present.
     */
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

    /**
     * Formats one log entry as a single line.
     *
     * @param entry log entry
     * @return formatted line
     */
    private String formatEntry(LogEntry entry) {
        Instant timestamp = entry.timestamp() != null ? entry.timestamp() : Instant.now();
        String time = LINE_DATE_FORMAT.format(timestamp.atZone(zoneId));
        return time + " " + sanitize(entry.message());
    }

    /**
     * Escapes line breaks to keep one logical entry per output line.
     *
     * @param message log message
     * @return sanitized message
     */
    private String sanitize(String message) {
        if (message == null) {
            return "";
        }
        return message.replace("\r", "\\r").replace("\n", "\\n");
    }

    /**
     * Creates the dedicated background thread used for flush scheduling.
     */
    private static final class LogThreadFactory implements ThreadFactory {
        /**
         * @param runnable scheduled task
         * @return daemon log writer thread
         */
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "app-log-writer");
            thread.setDaemon(true);
            return thread;
        }
    }
}

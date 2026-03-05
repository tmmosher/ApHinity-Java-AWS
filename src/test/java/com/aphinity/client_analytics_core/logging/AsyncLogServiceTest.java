package com.aphinity.client_analytics_core.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsyncLogServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void flushWritesEntriesSanitizesNewlinesAndReportsDroppedQueueEntries() throws Exception {
        AsyncLogService service = new AsyncLogService(loggingProperties(tempDir, 1));
        try {
            service.log(Instant.parse("2026-01-01T00:00:00Z"), "line-one\nline-two");
            service.log(Instant.parse("2026-01-01T00:01:00Z"), "dropped");

            invokeNoArg(service, "flushSafely");

            List<Path> files = Files.list(tempDir).toList();
            assertEquals(1, files.size());
            String content = Files.readString(files.getFirst());
            assertTrue(content.contains("line-one\\nline-two"));
            assertTrue(content.contains("Dropped 1 log entries due to full queue"));
        } finally {
            service.stop();
        }
    }

    @Test
    void stopPreventsFurtherQueueing() throws Exception {
        AsyncLogService service = new AsyncLogService(loggingProperties(tempDir, 4));
        service.stop();
        service.log("ignored-after-stop");

        @SuppressWarnings("unchecked")
        BlockingQueue<Object> queue = (BlockingQueue<Object>) readField(service, "queue");
        assertEquals(0, queue.size());
    }

    @Test
    void logNormalizesNullTimestampAndMessage() throws Exception {
        AsyncLogService service = new AsyncLogService(loggingProperties(tempDir, 4));
        try {
            service.log(null, null);

            @SuppressWarnings("unchecked")
            BlockingQueue<LogEntry> queue = (BlockingQueue<LogEntry>) readField(service, "queue");
            LogEntry entry = queue.poll();

            assertNotNull(entry);
            assertNotNull(entry.timestamp());
            assertEquals("", entry.message());
        } finally {
            service.stop();
        }
    }

    @Test
    void flushWithNoEntriesDoesNotCreateFile() throws Exception {
        AsyncLogService service = new AsyncLogService(loggingProperties(tempDir, 4));
        try {
            invokeNoArg(service, "flushSafely");
            assertEquals(0, Files.list(tempDir).toList().size());
        } finally {
            service.stop();
        }
    }

    @Test
    void flushSafelySwallowsOpenWriterFailuresAndLeavesQueueIntact() throws Exception {
        Path nonDirectory = tempDir.resolve("not-a-directory.log");
        Files.writeString(nonDirectory, "already-a-file");

        AppLoggingProperties properties = loggingProperties(nonDirectory, 4);
        AsyncLogService service = new AsyncLogService(properties);
        try {
            service.log("line-one");
            invokeNoArg(service, "flushSafely");

            @SuppressWarnings("unchecked")
            BlockingQueue<Object> queue = (BlockingQueue<Object>) readField(service, "queue");
            assertEquals(1, queue.size());
        } finally {
            service.stop();
        }
    }

    @Test
    void flushOnceRequeuesEntriesWhenWriterThrows() throws Exception {
        AsyncLogService service = new AsyncLogService(loggingProperties(tempDir, 4));
        try {
            service.log(Instant.parse("2026-01-01T00:00:00Z"), "line-one");

            writeField(service, "currentDate", LocalDate.now());
            writeField(service, "writer", new BufferedWriter(new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) throws IOException {
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            }) {
                @Override
                public void write(String s, int off, int len) throws IOException {
                    throw new IOException("forced write failure");
                }
            });

            InvocationTargetException thrown = assertThrows(
                InvocationTargetException.class,
                () -> invokeNoArg(service, "flushOnce")
            );
            assertNotNull(thrown.getCause());
            assertTrue(thrown.getCause() instanceof IOException);

            @SuppressWarnings("unchecked")
            BlockingQueue<Object> queue = (BlockingQueue<Object>) readField(service, "queue");
            assertEquals(1, queue.size());
        } finally {
            service.stop();
        }
    }

    @Test
    void closeWriterSuppressesFlushErrorsAndClearsWriterReference() throws Exception {
        AsyncLogService service = new AsyncLogService(loggingProperties(tempDir, 4));
        try {
            writeField(service, "writer", new BufferedWriter(new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) {
                }

                @Override
                public void flush() throws IOException {
                    throw new IOException("forced flush failure");
                }

                @Override
                public void close() {
                }
            }));

            invokeNoArg(service, "closeWriter");
            assertNull(readField(service, "writer"));
        } finally {
            service.stop();
        }
    }

    @Test
    void formatEntryHandlesNullTimestampAndMessage() throws Exception {
        AsyncLogService service = new AsyncLogService(loggingProperties(tempDir, 4));
        try {
            Method formatEntry = service.getClass().getDeclaredMethod("formatEntry", LogEntry.class);
            formatEntry.setAccessible(true);

            String line = (String) formatEntry.invoke(service, new LogEntry(null, null));
            assertFalse(line.contains("null"));
            assertTrue(line.endsWith(" "));
        } finally {
            service.stop();
        }
    }

    private AppLoggingProperties loggingProperties(Path directory, int queueCapacity) {
        AppLoggingProperties properties = new AppLoggingProperties();
        properties.setDirectory(directory.toString());
        properties.setFilePrefix("async-log-test");
        properties.setQueueCapacity(queueCapacity);
        properties.setFlushIntervalMs(1000);
        return properties;
    }

    private Object invokeNoArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private void writeField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

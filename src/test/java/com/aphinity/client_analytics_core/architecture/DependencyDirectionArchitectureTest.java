package com.aphinity.client_analytics_core.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyDirectionArchitectureTest {
    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aphinity/client_analytics_core/api");

    @Test
    void persistenceEntitiesDoNotDependOnPlotlyServicesOrWebContracts() throws IOException {
        assertNoImports(
            JAVA_ROOT.resolve("core/entities"),
            List.of(".plotly.", ".services.", ".controllers.", ".requests.", ".response.")
        );
        assertNoImports(
            JAVA_ROOT.resolve("auth/entities"),
            List.of(".services.", ".controllers.", ".requests.", ".response.")
        );
    }

    @Test
    void controllersDoNotReachIntoPersistenceOrPlotlyAdapters() throws IOException {
        assertNoImports(JAVA_ROOT.resolve("core/controllers"), List.of(".repositories.", ".plotly."));
        assertNoImports(JAVA_ROOT.resolve("auth/controllers"), List.of(".repositories.", ".plotly."));
    }

    private void assertNoImports(Path packageRoot, List<String> forbiddenFragments) throws IOException {
        if (!Files.exists(packageRoot)) {
            return;
        }
        try (var files = Files.walk(packageRoot)) {
            for (Path source : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String contents = Files.readString(source);
                for (String forbidden : forbiddenFragments) {
                    assertTrue(
                        contents.lines().noneMatch(line -> line.startsWith("import ") && line.contains(forbidden)),
                        () -> source + " imports forbidden lower-level or outer-layer dependency " + forbidden
                    );
                }
            }
        }
    }
}

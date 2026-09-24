package org.mendrugo.nica.e2e;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mendrugo.nica.cli.Main;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full integration tests: 3 snippets × 3 views = 9 combinations.
 * Exercises the complete pipeline via the CLI entry point.
 */
class IntegrationTest {

    @TempDir
    Path tempDir;

    static Stream<Arguments> snippetsAndViews() {
        return Stream.of(
            // x86 HotSpot × 3 views
            Arguments.of("hotspot-debug-x86.asm", "cheat-sheet", null),
            Arguments.of("hotspot-debug-x86.asm", "literal", null),
            Arguments.of("hotspot-debug-x86.asm", "explained", null),
            // aarch64 HotSpot × 3 views
            Arguments.of("hotspot-debug-aarch64.asm", "cheat-sheet", "aarch64"),
            Arguments.of("hotspot-debug-aarch64.asm", "literal", "aarch64"),
            Arguments.of("hotspot-debug-aarch64.asm", "explained", "aarch64"),
            // GraalVM perf annotate × 3 views
            Arguments.of("perf-annotate-graalvm-x86.asm", "cheat-sheet", null),
            Arguments.of("perf-annotate-graalvm-x86.asm", "literal", null),
            Arguments.of("perf-annotate-graalvm-x86.asm", "explained", null)
        );
    }

    @ParameterizedTest(name = "{0} --view {1}")
    @MethodSource("snippetsAndViews")
    void cliProducesNonEmptyOutput(String resource, String view, String arch) throws IOException {
        var inputFile = writeResource(resource);
        var args = buildArgs(view, arch, inputFile);

        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        int exitCode = Main.run(args, new PrintStream(stdout), new PrintStream(stderr));

        assertEquals(0, exitCode,
            "CLI failed for " + resource + " --view " + view + "\nstderr: " + stderr);
        assertFalse(stdout.toString().isBlank(),
            "Output should not be blank for " + resource + " --view " + view);
    }

    @ParameterizedTest(name = "{0} --view {1} (content check)")
    @MethodSource("snippetsAndViews")
    void cliOutputContainsExpectedContent(String resource, String view, String arch) throws IOException {
        var inputFile = writeResource(resource);
        var args = buildArgs(view, arch, inputFile);

        var stdout = new ByteArrayOutputStream();
        int exitCode = Main.run(args, new PrintStream(stdout), new PrintStream(new ByteArrayOutputStream()));
        assertEquals(0, exitCode);

        String output = stdout.toString();
        switch (view) {
            case "cheat-sheet" -> assertTrue(output.contains("//"),
                "Cheat sheet should have comments: " + resource);
            case "literal" -> {
                assertTrue(output.contains("public class"),
                    "Literal should be a Java class: " + resource);
                assertTrue(output.contains("public static"),
                    "Literal should have static methods: " + resource);
            }
            case "explained" -> {
                assertTrue(output.contains("public class"),
                    "Explained should be a Java class: " + resource);
                // Explained should have some high-level abstraction
                assertTrue(output.contains("signExtByte") || output.contains("Objects.requireNonNull")
                    || output.contains("Vectorized") || output.contains("NEON"),
                    "Explained should contain high-level abstractions: " + resource);
            }
        }
    }

    @ParameterizedTest(name = "{0} --view {1} (file output)")
    @MethodSource("snippetsAndViews")
    void cliWritesToOutputFile(String resource, String view, String arch) throws IOException {
        var inputFile = writeResource(resource);
        var outputFile = tempDir.resolve(resource.replace(".asm", "-" + view + ".out"));
        var args = buildArgs(view, arch, inputFile, outputFile);

        var stdout = new ByteArrayOutputStream();
        int exitCode = Main.run(args, new PrintStream(stdout), new PrintStream(new ByteArrayOutputStream()));

        assertEquals(0, exitCode);
        assertTrue(stdout.toString().isEmpty(), "stdout should be empty with -o");
        assertTrue(Files.exists(outputFile));
        assertTrue(Files.size(outputFile) > 0);
    }

    // --- Helpers ---

    private Path writeResource(String name) throws IOException {
        try (var is = getClass().getResourceAsStream("/" + name)) {
            if (is == null) throw new IOException("Resource not found: " + name);
            var file = tempDir.resolve(name);
            Files.write(file, is.readAllBytes());
            return file;
        }
    }

    private String[] buildArgs(String view, String arch, Path inputFile) {
        var args = new java.util.ArrayList<String>();
        args.add("--view");
        args.add(view);
        if (arch != null) {
            args.add("--arch");
            args.add(arch);
        }
        args.add(inputFile.toString());
        return args.toArray(String[]::new);
    }

    private String[] buildArgs(String view, String arch, Path inputFile, Path outputFile) {
        var args = new java.util.ArrayList<String>();
        args.add("--view");
        args.add(view);
        if (arch != null) {
            args.add("--arch");
            args.add(arch);
        }
        args.add("-o");
        args.add(outputFile.toString());
        args.add(inputFile.toString());
        return args.toArray(String[]::new);
    }
}

package org.mendrugo.nica.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @TempDir
    Path tempDir;

    // --- Help ---

    @Test
    void helpExitsZero() {
        var result = run("--help");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Usage: nica"));
    }

    @Test
    void shortHelpExitsZero() {
        var result = run("-h");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Usage: nica"));
    }

    // --- Missing input ---

    @Test
    void noInputFileShowsError() {
        var result = run();
        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("no input file"));
    }

    @Test
    void missingFileShowsError() {
        var result = run("nonexistent.asm");
        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("cannot read"));
    }

    // --- Unknown options ---

    @Test
    void unknownOptionShowsError() {
        var result = run("--foo");
        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("unknown option"));
    }

    @Test
    void unknownViewShowsError() throws IOException {
        var file = writeTestFile("hotspot-debug-x86.asm");
        var result = run("--view", "invalid", file.toString());
        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("unknown view"));
    }

    // --- Cheat Sheet view ---

    @Test
    void cheatSheetViewX86() throws IOException {
        var file = writeTestFile("hotspot-debug-x86.asm");
        var result = run("--view", "cheat-sheet", file.toString());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("vmovq"));
        assertTrue(result.stdout().contains("//"));
    }

    @Test
    void cheatSheetViewAArch64() throws IOException {
        var file = writeTestFile("hotspot-debug-aarch64.asm");
        var result = run("--view", "cheat-sheet", "--arch", "aarch64", file.toString());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("sshll"));
    }

    @Test
    void cheatSheetViewPerf() throws IOException {
        var file = writeTestFile("perf-annotate-graalvm-x86.asm");
        var result = run("--view", "cheat-sheet", file.toString());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Method:"));
    }

    // --- Literal view ---

    @Test
    void literalViewX86() throws IOException {
        var file = writeTestFile("hotspot-debug-x86.asm");
        var result = run("--view", "literal", file.toString());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("public class"));
        assertTrue(result.stdout().contains("public static int[] run("));
    }

    @Test
    void literalIsDefault() throws IOException {
        var file = writeTestFile("hotspot-debug-x86.asm");
        var result = run(file.toString());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("public class"));
    }

    // --- Explained view ---

    @Test
    void explainedViewX86() throws IOException {
        var file = writeTestFile("hotspot-debug-x86.asm");
        var result = run("--view", "explained", file.toString());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("signExtByte"));
    }

    @Test
    void explainedViewPerf() throws IOException {
        var file = writeTestFile("perf-annotate-graalvm-x86.asm");
        var result = run("--view", "explained", file.toString());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Objects.requireNonNull")
                || result.stdout().contains("Objects.checkIndex"));
    }

    // --- Format auto-detection ---

    @Test
    void detectsHotSpotFormat() {
        assertEquals(Main.Format.HOTSPOT_DEBUG,
            Main.detectFormat("  0x00007f:   vmovq %xmm0, %rsi"));
    }

    @Test
    void detectsPerfFormat() {
        assertEquals(Main.Format.PERF_ANNOTATE,
            Main.detectFormat("   6.36 │      leaq  -0x18(%rsp),%rbx"));
    }

    @Test
    void detectsX86Architecture() {
        assertEquals(org.mendrugo.nica.asm.Architecture.X86_64,
            Main.detectArchitecture("vmovq %xmm0, %rsi"));
    }

    @Test
    void detectsAArch64Architecture() {
        assertEquals(org.mendrugo.nica.asm.Architecture.AARCH64,
            Main.detectArchitecture("sxtw x12, w17"));
    }

    // --- Output to file ---

    @Test
    void outputToFile() throws IOException {
        var inputFile = writeTestFile("hotspot-debug-x86.asm");
        var outputFile = tempDir.resolve("output.java");
        var result = run("--view", "cheat-sheet", "-o", outputFile.toString(), inputFile.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().isEmpty(), "stdout should be empty when writing to file");
        assertTrue(Files.exists(outputFile));
        String content = Files.readString(outputFile);
        assertTrue(content.contains("vmovq"));
    }

    // --- Class name ---

    @Test
    void customClassName() throws IOException {
        var file = writeTestFile("hotspot-debug-x86.asm");
        var result = run("--view", "literal", "--class-name", "MyComputation", file.toString());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("public class MyComputation"));
    }

    @Test
    void classNameDerivedFromFile() throws IOException {
        var file = writeTestFile("hotspot-debug-x86.asm");
        var result = run("--view", "literal", file.toString());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("public class Hotspot_debug_x86"));
    }

    // --- Helpers ---

    private Path writeTestFile(String resourceName) throws IOException {
        String content;
        try (var is = getClass().getResourceAsStream("/" + resourceName)) {
            if (is == null) throw new IOException("Resource not found: " + resourceName);
            content = new String(is.readAllBytes());
        }
        var file = tempDir.resolve(resourceName);
        Files.writeString(file, content);
        return file;
    }

    private Result run(String... args) {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        int exitCode = Main.run(args, new PrintStream(stdout), new PrintStream(stderr));
        return new Result(exitCode, stdout.toString(), stderr.toString());
    }

    record Result(int exitCode, String stdout, String stderr) {}
}

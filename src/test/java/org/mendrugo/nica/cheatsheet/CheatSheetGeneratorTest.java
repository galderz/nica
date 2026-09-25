package org.mendrugo.nica.cheatsheet;

import org.junit.jupiter.api.Test;
import org.mendrugo.nica.asm.Architecture;
import org.mendrugo.nica.parser.HotSpotDebugParser;
import org.mendrugo.nica.parser.PerfAnnotateParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CheatSheetGeneratorTest {

    @Test
    void x86HotSpotCheatSheetContainsBlockHeader() throws IOException {
        var output = generateX86HotSpot();
        assertTrue(output.startsWith("// Block: B22:"));
    }

    @Test
    void x86HotSpotCheatSheetAnnotatesVmovq() throws IOException {
        var output = generateX86HotSpot();
        // Should contain vmovq description
        assertTrue(output.contains("Move 64 bits"));
        assertTrue(output.contains("load8Bytes"));
    }

    @Test
    void x86HotSpotCheatSheetAnnotatesVpmovsxbd() throws IOException {
        var output = generateX86HotSpot();
        assertTrue(output.contains("Sign-extend 8 packed bytes"));
        assertTrue(output.contains("(byte)"));
    }

    @Test
    void x86HotSpotCheatSheetAnnotatesVpmulld() throws IOException {
        var output = generateX86HotSpot();
        assertTrue(output.contains("Multiply 8 packed"));
        assertTrue(output.contains("a[i] * b[i]"));
    }

    @Test
    void x86HotSpotCheatSheetPreservesSourceAnnotation() throws IOException {
        var output = generateX86HotSpot();
        // Source annotation should appear for jl instruction
        assertTrue(output.contains("TestXorByte::testByte"));
        assertTrue(output.contains("line 20"));
    }

    @Test
    void aarch64CheatSheetAnnotatesSshll() throws IOException {
        var output = generateAArch64();
        assertTrue(output.contains("widen"));
    }

    @Test
    void aarch64CheatSheetAnnotatesEor3() throws IOException {
        var output = generateAArch64();
        assertTrue(output.contains("Three-way exclusive OR"));
        assertTrue(output.contains("a ^ b ^ c"));
    }

    @Test
    void aarch64CheatSheetAnnotatesMla() throws IOException {
        var output = generateAArch64();
        assertTrue(output.contains("Multiply-accumulate"));
        assertTrue(output.contains("dst[i] += a[i] * b[i]"));
    }

    @Test
    void perfAnnotateCheatSheetContainsMethodSignature() throws IOException {
        var output = generatePerfAnnotate();
        assertTrue(output.contains("// Method:"));
        assertTrue(output.contains("VanillaByteArrays::vhandleGetLongLE"));
    }

    @Test
    void perfAnnotateCheatSheetShowsPercentages() throws IOException {
        var output = generatePerfAnnotate();
        // movl with 12.61% should show [12.61%]
        assertTrue(output.contains("[12.61%]"));
        assertTrue(output.contains("[6.36%]"));
    }

    @Test
    void perfAnnotateCheatSheetAnnotatesNop() throws IOException {
        var output = generatePerfAnnotate();
        assertTrue(output.contains("No operation"));
    }

    @Test
    void perfAnnotateCheatSheetAnnotatesRetq() throws IOException {
        var output = generatePerfAnnotate();
        assertTrue(output.contains("Return from function"));
    }

    @Test
    void cheatSheetOutputIsNotEmpty() throws IOException {
        assertFalse(generateX86HotSpot().isBlank());
        assertFalse(generateAArch64().isBlank());
        assertFalse(generatePerfAnnotate().isBlank());
    }

    @Test
    void everyLineHasComment() throws IOException {
        var output = generateX86HotSpot();
        for (String line : output.lines().toList()) {
            if (line.isBlank()) continue;
            assertTrue(line.contains("//"), "Line missing comment: " + line);
        }
    }

    // --- Helpers ---

    private String generateX86HotSpot() throws IOException {
        var snippet = HotSpotDebugParser.parse(loadResource("hotspot-debug-x86.asm"), Architecture.X86_64);
        return CheatSheetGenerator.generate(snippet);
    }

    private String generateAArch64() throws IOException {
        var snippet = HotSpotDebugParser.parse(loadResource("hotspot-debug-aarch64.asm"), Architecture.AARCH64);
        return CheatSheetGenerator.generate(snippet);
    }

    private String generatePerfAnnotate() throws IOException {
        var snippet = PerfAnnotateParser.parse(loadResource("perf-annotate-graalvm-x86.asm"));
        return CheatSheetGenerator.generate(snippet);
    }

    private static String loadResource(String name) throws IOException {
        try (var is = CheatSheetGeneratorTest.class.getResourceAsStream("/" + name)) {
            if (is == null) throw new IOException("Resource not found: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

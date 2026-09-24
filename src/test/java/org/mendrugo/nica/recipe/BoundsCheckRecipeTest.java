package org.mendrugo.nica.recipe;

import org.junit.jupiter.api.Test;
import org.mendrugo.nica.asm.Architecture;
import org.mendrugo.nica.parser.PerfAnnotateParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BoundsCheckRecipeTest {

    @Test
    void findsPatternInGraalVMSnippet() throws IOException {
        var snippet = PerfAnnotateParser.parse(loadResource("perf-annotate-graalvm-x86.asm"));
        var matches = BoundsCheckRecipe.find(snippet);

        assertFalse(matches.isEmpty(), "Should find at least one bounds check pattern");
    }

    @Test
    void matchContainsNullCheckExplanation() throws IOException {
        var snippet = PerfAnnotateParser.parse(loadResource("perf-annotate-graalvm-x86.asm"));
        var matches = BoundsCheckRecipe.find(snippet);

        var match = matches.getFirst();
        assertTrue(match.explanation().contains("Null check"));
        assertTrue(match.explanation().contains("bounds check"));
    }

    @Test
    void matchJavaCodeContainsCheckIndex() throws IOException {
        var snippet = PerfAnnotateParser.parse(loadResource("perf-annotate-graalvm-x86.asm"));
        var matches = BoundsCheckRecipe.find(snippet);

        var match = matches.getFirst();
        assertTrue(match.javaCode().contains("Objects.requireNonNull"));
        assertTrue(match.javaCode().contains("Objects.checkIndex"));
    }

    @Test
    void matchExtractsBound() throws IOException {
        var snippet = PerfAnnotateParser.parse(loadResource("perf-annotate-graalvm-x86.asm"));
        var matches = BoundsCheckRecipe.find(snippet);

        var match = matches.getFirst();
        // cmpl $0x8,%edi → bound is 8
        assertTrue(match.javaCode().contains("8"),
            "Should extract bound value from cmpl. Code: " + match.javaCode());
    }

    @Test
    void matchIdentifiesNullCheckInstruction() throws IOException {
        var snippet = PerfAnnotateParser.parse(loadResource("perf-annotate-graalvm-x86.asm"));
        var matches = BoundsCheckRecipe.find(snippet);

        assertEquals("testl", matches.getFirst().nullCheckInsn().mnemonic());
    }

    @Test
    void matchIdentifiesBoundsCheckInstruction() throws IOException {
        var snippet = PerfAnnotateParser.parse(loadResource("perf-annotate-graalvm-x86.asm"));
        var matches = BoundsCheckRecipe.find(snippet);

        assertEquals("cmpl", matches.getFirst().boundsCheckInsn().mnemonic());
    }

    @Test
    void noMatchInHotSpotSnippet() throws IOException {
        // HotSpot x86 snippet doesn't have the GraalVM bounds-check pattern
        var text = loadResource("hotspot-debug-x86.asm");
        var snippet = org.mendrugo.nica.parser.HotSpotDebugParser
            .parse(text, Architecture.X86_64);

        var matches = BoundsCheckRecipe.find(snippet);
        assertTrue(matches.isEmpty());
    }

    private static String loadResource(String name) throws IOException {
        try (var is = BoundsCheckRecipeTest.class.getResourceAsStream("/" + name)) {
            if (is == null) throw new IOException("Resource not found: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

package org.mendrugo.nica.codegen;

import org.junit.jupiter.api.Test;
import org.mendrugo.nica.asm.Architecture;
import org.mendrugo.nica.asm.AssemblySnippet;
import org.mendrugo.nica.asm.Instruction;
import org.mendrugo.nica.asm.Operand;
import org.mendrugo.nica.parser.HotSpotDebugParser;
import org.mendrugo.nica.parser.PerfAnnotateParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LiteralJavaGeneratorTest {

    // --- x86_64 HotSpot ---

    @Test
    void x86GeneratesNonEmptySource() throws IOException {
        var snippet = parseX86();
        String source = LiteralJavaGenerator.generate(snippet, "TestLiteralX86");
        assertFalse(source.isBlank());
    }

    @Test
    void x86GeneratesValidClassStructure() throws IOException {
        var snippet = parseX86();
        String source = LiteralJavaGenerator.generate(snippet, "TestLiteralX86");
        assertTrue(source.contains("public class TestLiteralX86"));
        assertTrue(source.contains("public static int[] run("));
        assertTrue(source.contains("public static void main("));
        assertTrue(source.contains("return "));
    }

    @Test
    void x86ContainsAssemblyComments() throws IOException {
        var snippet = parseX86();
        String source = LiteralJavaGenerator.generate(snippet, "TestLiteralX86");
        assertTrue(source.contains("// ") && source.contains("vmovq"));
        assertTrue(source.contains("vpmovsxbd"));
        assertTrue(source.contains("vpmulld"));
        assertTrue(source.contains("vpxor"));
    }

    @Test
    void x86ContainsSignExtension() throws IOException {
        var snippet = parseX86();
        String source = LiteralJavaGenerator.generate(snippet, "TestLiteralX86");
        // vpmovsxbd sign-extends bytes: should contain (byte) cast
        assertTrue(source.contains("(byte)"), "Should contain byte sign-extension");
    }

    @Test
    void x86ContainsSimdLoops() throws IOException {
        var snippet = parseX86();
        String source = LiteralJavaGenerator.generate(snippet, "TestLiteralX86");
        assertTrue(source.contains("for (int i = 0; i < 8; i++)"));
    }

    @Test
    void x86ContainsArrayOperations() throws IOException {
        var snippet = parseX86();
        String source = LiteralJavaGenerator.generate(snippet, "TestLiteralX86");
        // Should have element-wise operations like ymm12[i] = ymm9[i] * ymm10[i]
        assertTrue(source.contains("[i] *") || source.contains("[i] +") || source.contains("[i] ^"));
    }

    @Test
    void x86ContainsShifts() throws IOException {
        var snippet = parseX86();
        String source = LiteralJavaGenerator.generate(snippet, "TestLiteralX86");
        assertTrue(source.contains("<< 24") || source.contains("<<"));
        assertTrue(source.contains(">> 24") || source.contains(">>"));
    }

    // --- aarch64 HotSpot ---

    @Test
    void aarch64GeneratesNonEmptySource() throws IOException {
        var snippet = parseAArch64();
        String source = LiteralJavaGenerator.generate(snippet, "TestLiteralAArch64");
        assertFalse(source.isBlank());
    }

    @Test
    void aarch64GeneratesValidClassStructure() throws IOException {
        var snippet = parseAArch64();
        String source = LiteralJavaGenerator.generate(snippet, "TestLiteralAArch64");
        assertTrue(source.contains("public class TestLiteralAArch64"));
        assertTrue(source.contains("public static int[] run("));
        assertTrue(source.contains("return "));
    }

    @Test
    void aarch64ContainsSshllSignExtension() throws IOException {
        var snippet = parseAArch64();
        String source = LiteralJavaGenerator.generate(snippet, "TestLiteralAArch64");
        // sshll byte→halfword: (byte) cast
        assertTrue(source.contains("(byte)") || source.contains("(short)"));
    }

    @Test
    void aarch64ContainsMla() throws IOException {
        var snippet = parseAArch64();
        String source = LiteralJavaGenerator.generate(snippet, "TestLiteralAArch64");
        assertTrue(source.contains("+=")); // mla: dst[i] += a[i] * b[i]
    }

    @Test
    void aarch64ContainsEor3() throws IOException {
        var snippet = parseAArch64();
        String source = LiteralJavaGenerator.generate(snippet, "TestLiteralAArch64");
        // eor3: a ^ b ^ c
        assertTrue(source.contains("^ ") || source.contains("^"));
    }

    // --- Small snippet correctness test ---

    @Test
    void smallSnippetGeneratesCorrectCode() {
        var insns = List.of(
            insn("vpmovsxbd", reg("xmm0"), reg("ymm0")),
            insn("vpmovsxbd", reg("xmm1"), reg("ymm1")),
            insn("vpmulld", reg("ymm0"), reg("ymm1"), reg("ymm2")),
            insn("vpslld", imm(0x18), reg("ymm2"), reg("ymm2")),
            insn("vpsrad", imm(0x18), reg("ymm2"), reg("ymm2")),
            insn("vpxor", reg("ymm2"), reg("ymm0"), reg("ymm0"))
        );
        var snippet = new AssemblySnippet(Architecture.X86_64, insns);
        String source = LiteralJavaGenerator.generate(snippet, "SmallTest");

        // Should have byte sign-extension
        assertTrue(source.contains("(byte)"));
        // Should have multiply
        assertTrue(source.contains("[i] *"));
        // Should have shifts
        assertTrue(source.contains("<< 24"));
        assertTrue(source.contains(">> 24"));
        // Should have XOR
        assertTrue(source.contains("[i] ^"));
        // Should return ymm0 (last vpxor destination)
        assertTrue(source.contains("return ymm0;"));
    }

    @Test
    void generatedCodeContainsNicaAttribution() throws IOException {
        var snippet = parseX86();
        String source = LiteralJavaGenerator.generate(snippet, "Test");
        assertTrue(source.contains("Generated by Nica"));
    }

    // --- Helpers ---

    private AssemblySnippet parseX86() throws IOException {
        return HotSpotDebugParser.parse(loadResource("hotspot-debug-x86.asm"), Architecture.X86_64);
    }

    private AssemblySnippet parseAArch64() throws IOException {
        return HotSpotDebugParser.parse(loadResource("hotspot-debug-aarch64.asm"), Architecture.AARCH64);
    }

    private static Instruction insn(String mnemonic, Operand... ops) {
        return new Instruction(-1, mnemonic, List.of(ops), mnemonic + " " +
            String.join(", ", java.util.Arrays.stream(ops).map(Object::toString).toList()));
    }
    private static Operand.Register reg(String name) { return new Operand.Register(name); }
    private static Operand.Immediate imm(long value) { return new Operand.Immediate(value); }

    private static String loadResource(String name) throws IOException {
        try (var is = LiteralJavaGeneratorTest.class.getResourceAsStream("/" + name)) {
            if (is == null) throw new IOException("Resource not found: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

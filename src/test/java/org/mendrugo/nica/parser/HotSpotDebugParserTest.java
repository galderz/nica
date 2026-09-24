package org.mendrugo.nica.parser;

import org.junit.jupiter.api.Test;
import org.mendrugo.nica.asm.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HotSpotDebugParserTest {

    // --- x86_64 tests ---

    @Test
    void parsesX86BlockHeader() throws IOException {
        var snippet = parseX86Resource();
        assertNotNull(snippet.blockInfo());
        assertTrue(snippet.blockInfo().startsWith("B22:"));
        assertTrue(snippet.blockInfo().contains("Loop"));
    }

    @Test
    void parsesX86InstructionCount() throws IOException {
        var snippet = parseX86Resource();
        // Count actual instruction lines in the snippet
        assertEquals(56, snippet.instructions().size());
    }

    @Test
    void parsesX86FirstInstruction() throws IOException {
        var snippet = parseX86Resource();
        var first = snippet.instructions().getFirst();
        assertEquals("vmovq", first.mnemonic());
        assertEquals(0x00007fdf4504b0a8L, first.address());
        assertEquals(2, first.operands().size());

        // %xmm0 → Register("xmm0")
        assertInstanceOf(Operand.Register.class, first.operands().get(0));
        assertEquals("xmm0", ((Operand.Register) first.operands().get(0)).name());

        // %rsi → Register("rsi")
        assertInstanceOf(Operand.Register.class, first.operands().get(1));
        assertEquals("rsi", ((Operand.Register) first.operands().get(1)).name());
    }

    @Test
    void parsesX86MemoryOperand() throws IOException {
        var snippet = parseX86Resource();
        // 0x14(%rsi, %r9) → second instruction
        var insn = snippet.instructions().get(1);
        assertEquals("vmovq", insn.mnemonic());
        assertEquals(2, insn.operands().size());

        var mem = assertInstanceOf(Operand.Memory.class, insn.operands().get(0));
        assertEquals("rsi", mem.base());
        assertEquals("r9", mem.index());
        assertEquals(1, mem.scale());
        assertEquals(0x14, mem.displacement());
    }

    @Test
    void parsesX86ThreeOperandAVX() throws IOException {
        var snippet = parseX86Resource();
        // vpmulld %ymm9, %ymm10, %ymm12 — find it
        var mul = snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("vpmulld"))
            .findFirst().orElseThrow();
        assertEquals(3, mul.operands().size());
        assertEquals("ymm9", ((Operand.Register) mul.operands().get(0)).name());
        assertEquals("ymm10", ((Operand.Register) mul.operands().get(1)).name());
        assertEquals("ymm12", ((Operand.Register) mul.operands().get(2)).name());
    }

    @Test
    void parsesX86ImmediateOperand() throws IOException {
        var snippet = parseX86Resource();
        // vpslld $0x18, %ymm8, %ymm8
        var shl = snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("vpslld"))
            .findFirst().orElseThrow();
        assertEquals(3, shl.operands().size());
        var imm = assertInstanceOf(Operand.Immediate.class, shl.operands().get(0));
        assertEquals(0x18, imm.value());
    }

    @Test
    void parsesX86BranchWithAddress() throws IOException {
        var snippet = parseX86Resource();
        // jl 0x7fdf4504b0a0
        var jl = snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("jl"))
            .findFirst().orElseThrow();
        assertEquals(1, jl.operands().size());
        var addr = assertInstanceOf(Operand.Address.class, jl.operands().get(0));
        assertEquals(0x7fdf4504b0a0L, addr.address());
    }

    @Test
    void parsesX86SourceAnnotation() throws IOException {
        var snippet = parseX86Resource();
        // jl has annotation: TestXorByte::testByte@9 (line 20) on next line
        var jl = snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("jl"))
            .findFirst().orElseThrow();
        assertNotNull(jl.annotation());
        assertEquals("TestXorByte", jl.annotation().className());
        assertEquals("testByte", jl.annotation().methodName());
        assertEquals(9, jl.annotation().bci());
        assertEquals(20, jl.annotation().lineNumber());
    }

    @Test
    void parsesX86Leal() throws IOException {
        var snippet = parseX86Resource();
        // leal 0x20(%r9), %ebp
        var lea = snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("leal"))
            .findFirst().orElseThrow();
        assertEquals(2, lea.operands().size());
        var mem = assertInstanceOf(Operand.Memory.class, lea.operands().get(0));
        assertEquals("r9", mem.base());
        assertEquals(0x20, mem.displacement());
        assertEquals("ebp", ((Operand.Register) lea.operands().get(1)).name());
    }

    @Test
    void x86ArchitectureIsSet() throws IOException {
        var snippet = parseX86Resource();
        assertEquals(Architecture.X86_64, snippet.architecture());
    }

    // --- Operand parsing unit tests ---

    @Test
    void splitOperandsRespectsBrackets() {
        var parts = HotSpotDebugParser.splitOperands("0x14(%rsi, %r9), %xmm10");
        assertEquals(2, parts.size());
        assertEquals("0x14(%rsi, %r9)", parts.get(0).strip());
        assertEquals("%xmm10", parts.get(1).strip());
    }

    @Test
    void parseX86MemoryNoIndex() {
        var operands = HotSpotDebugParser.parseX86Operands("0x20(%r9), %ebp");
        assertEquals(2, operands.size());
        var mem = assertInstanceOf(Operand.Memory.class, operands.get(0));
        assertEquals("r9", mem.base());
        assertNull(mem.index());
        assertEquals(0x20, mem.displacement());
    }

    @Test
    void parseX86RegisterOnly() {
        var operands = HotSpotDebugParser.parseX86Operands("%eax, %ebp");
        assertEquals(2, operands.size());
        assertEquals("eax", ((Operand.Register) operands.get(0)).name());
        assertEquals("ebp", ((Operand.Register) operands.get(1)).name());
    }

    // --- Helper ---

    private AssemblySnippet parseX86Resource() throws IOException {
        String text = loadResource("hotspot-debug-x86.asm");
        return HotSpotDebugParser.parse(text, Architecture.X86_64);
    }

    static String loadResource(String name) throws IOException {
        try (var is = HotSpotDebugParserTest.class.getResourceAsStream("/" + name)) {
            if (is == null) throw new IOException("Resource not found: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

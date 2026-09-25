package org.mendrugo.nica.asm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the assembly instruction model records.
 */
class InstructionModelTest {

    @Test
    void registerOperand() {
        var reg = new Operand.Register("rax");
        assertEquals("rax", reg.name());
        assertNull(reg.arrangement());
        assertFalse(reg.isVector());
    }

    @Test
    void vectorRegisterWithArrangement() {
        var vreg = new Operand.Register("v26", "8b");
        assertEquals("v26", vreg.name());
        assertEquals("8b", vreg.arrangement());
        assertTrue(vreg.isVector());
    }

    @Test
    void immediateOperand() {
        var imm = new Operand.Immediate(0x18);
        assertEquals(0x18, imm.value());
    }

    @Test
    void memoryWithBaseAndDisplacement() {
        // 0x14(%rsi) → base=rsi, disp=0x14
        var mem = new Operand.Memory("rsi", 0x14);
        assertEquals("rsi", mem.base());
        assertNull(mem.index());
        assertEquals(1, mem.scale());
        assertEquals(0x14, mem.displacement());
    }

    @Test
    void memoryWithBaseIndexScaleDisplacement() {
        // 0x4(%r14,%rax,8) → base=r14, index=rax, scale=8, disp=4
        var mem = new Operand.Memory("r14", "rax", 8, 0x4);
        assertEquals("r14", mem.base());
        assertEquals("rax", mem.index());
        assertEquals(8, mem.scale());
        assertEquals(0x4, mem.displacement());
    }

    @Test
    void negativeDisplacement() {
        // -0x18(%rsp) → base=rsp, disp=-24
        var mem = new Operand.Memory("rsp", -0x18);
        assertEquals("rsp", mem.base());
        assertEquals(-0x18, mem.displacement());
    }

    @Test
    void addressOperandNumeric() {
        var addr = new Operand.Address(0x7fdf4504b0a0L);
        assertEquals(0x7fdf4504b0a0L, addr.address());
        assertNull(addr.label());
    }

    @Test
    void addressOperandLabel() {
        var addr = new Operand.Address("43");
        assertEquals(-1, addr.address());
        assertEquals("43", addr.label());
    }

    @Test
    void instructionWithOperands() {
        // vpmulld %ymm9, %ymm10, %ymm12
        var insn = new Instruction(
            0x00007fdf4504b110L,
            "vpmulld",
            List.of(
                new Operand.Register("ymm9"),
                new Operand.Register("ymm10"),
                new Operand.Register("ymm12")
            ),
            "vpmulld %ymm9, %ymm10, %ymm12"
        );
        assertEquals("vpmulld", insn.mnemonic());
        assertEquals(3, insn.operands().size());
        assertNull(insn.annotation());
        assertFalse(insn.hasPerfData());
    }

    @Test
    void instructionWithAnnotation() {
        var annotation = new SourceAnnotation("TestXorByte", "testByte", 9, 20);
        var insn = new Instruction(
            0x00007fdf4504b1c8L,
            "jl",
            List.of(new Operand.Address(0x7fdf4504b0a0L)),
            "jl 0x7fdf4504b0a0",
            annotation
        );
        assertEquals("TestXorByte", insn.annotation().className());
        assertEquals("testByte", insn.annotation().methodName());
        assertEquals(9, insn.annotation().bci());
        assertEquals(20, insn.annotation().lineNumber());
    }

    @Test
    void instructionWithPerfData() {
        var insn = new Instruction(
            -1,
            "movl",
            List.of(
                new Operand.Memory("r14", "rax", 8, 0x4),
                new Operand.Register("edi")
            ),
            "movl 0x4(%r14,%rax,8),%edi",
            null,
            12.61
        );
        assertTrue(insn.hasPerfData());
        assertEquals(12.61, insn.perfPercent(), 0.001);
    }

    @Test
    void assemblySnippetBasic() {
        var insns = List.of(
            new Instruction(0x100, "nop", List.of(), "nop"),
            new Instruction(0x101, "retq", List.of(), "retq")
        );
        var snippet = new AssemblySnippet(Architecture.X86_64, insns);
        assertEquals(Architecture.X86_64, snippet.architecture());
        assertEquals(2, snippet.instructions().size());
        assertNull(snippet.blockInfo());
        assertNull(snippet.methodSignature());
    }

    @Test
    void assemblySnippetWithMetadata() {
        var snippet = new AssemblySnippet(
            Architecture.AARCH64,
            List.of(),
            "B22: out(B21 B23) <- in(B25 B21)",
            null
        );
        assertEquals("B22: out(B21 B23) <- in(B25 B21)", snippet.blockInfo());
    }

    @Test
    void aarch64VectorInstruction() {
        // sshll v26.8h, v26.8b, #0
        var insn = new Instruction(
            0x0000000111e010b0L,
            "sshll",
            List.of(
                new Operand.Register("v26", "8h"),
                new Operand.Register("v26", "8b"),
                new Operand.Immediate(0)
            ),
            "sshll v26.8h, v26.8b, #0"
        );
        assertEquals("sshll", insn.mnemonic());
        assertTrue(((Operand.Register) insn.operands().getFirst()).isVector());
        assertEquals("8h", ((Operand.Register) insn.operands().getFirst()).arrangement());
    }

    @Test
    void eor3FourOperands() {
        // eor3 v16.16b, v16.16b, v18.16b, v31.16b
        var insn = new Instruction(
            0x0000000111e0115cL,
            "eor3",
            List.of(
                new Operand.Register("v16", "16b"),
                new Operand.Register("v16", "16b"),
                new Operand.Register("v18", "16b"),
                new Operand.Register("v31", "16b")
            ),
            "eor3 v16.16b, v16.16b, v18.16b, v31.16b"
        );
        assertEquals(4, insn.operands().size());
    }
}

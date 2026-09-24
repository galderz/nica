package org.mendrugo.nica.semantics.x86;

import org.junit.jupiter.api.Test;
import org.mendrugo.nica.asm.Instruction;
import org.mendrugo.nica.asm.Operand;
import org.mendrugo.nica.semantics.MachineState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScalarX86SemanticsTest {

    @Test
    void movlZeroExtends() {
        var state = new MachineState(64);
        state.setReg64("rax", 0xFFFFFFFF_FFFFFFFFL);
        // movl $5, %eax → rax should be 5 (zero-extended)
        exec("movl", List.of(imm(5), reg("eax")), state);
        assertEquals(5L, state.getReg("rax"));
    }

    @Test
    void movqCopies64Bit() {
        var state = new MachineState(64);
        state.setReg64("rbx", 0xDEADBEEFCAFEBABEL);
        exec("movq", List.of(reg("rbx"), reg("rsp")), state);
        assertEquals(0xDEADBEEFCAFEBABEL, state.getReg("rsp"));
    }

    @Test
    void movqFromMemory() {
        var state = new MachineState(64);
        state.storeLong(8, 0x1234567890ABCDEFL);
        state.setReg64("rdi", 0);
        // movq 0x8(%rdi), %rax
        exec("movq", List.of(mem("rdi", 8), reg("rax")), state);
        assertEquals(0x1234567890ABCDEFL, state.getReg("rax"));
    }

    @Test
    void leaqComputesAddress() {
        var state = new MachineState(64);
        state.setReg64("r14", 100);
        state.setReg64("rax", 5);
        // leaq (%r14,%rax,8),%rcx → rcx = 100 + 5*8 = 140
        exec("leaq", List.of(
            new Operand.Memory("r14", "rax", 8, 0),
            reg("rcx")), state);
        assertEquals(140, state.getReg("rcx"));
    }

    @Test
    void lealComputesAddressAndZeroExtends() {
        var state = new MachineState(64);
        state.setReg64("r9", 16);
        // leal 0x20(%r9), %ebp → ebp = 16 + 32 = 48 (zero-extended)
        exec("leal", List.of(mem("r9", 0x20), reg("ebp")), state);
        assertEquals(48, state.getReg("rbp"));
    }

    @Test
    void addq() {
        var state = new MachineState(64);
        state.setReg64("rsp", 100);
        // addq $0x18, %rsp → rsp = 100 + 24 = 124
        exec("addq", List.of(imm(0x18), reg("rsp")), state);
        assertEquals(124, state.getReg("rsp"));
    }

    @Test
    void cmplAndJlTaken() {
        var state = new MachineState(64);
        state.setReg32("eax", 100);
        state.setReg32("ebp", 50);
        // cmpl %eax, %ebp → flags = compare(ebp, eax) = compare(50, 100) < 0
        exec("cmpl", List.of(reg("eax"), reg("ebp")), state);
        // jl should be taken (50 < 100)
        assertTrue(execBranch("jl", state));
    }

    @Test
    void cmplAndJlNotTaken() {
        var state = new MachineState(64);
        state.setReg32("eax", 50);
        state.setReg32("ebp", 100);
        // cmpl %eax, %ebp → flags = compare(100, 50) > 0
        exec("cmpl", List.of(reg("eax"), reg("ebp")), state);
        assertFalse(execBranch("jl", state));
    }

    @Test
    void cmpqAndJle() {
        var state = new MachineState(64);
        state.setReg64("rbx", 10);
        state.setReg64("r15", 0);
        // cmpq 0x8(%r15), %rbx → compare(rbx, mem[r15+8])
        state.storeLong(8, 10);
        exec("cmpq", List.of(mem("r15", 8), reg("rbx")), state);
        // Equal, so jle should be taken
        assertTrue(execBranch("jle", state));
    }

    @Test
    void testlAndJe() {
        var state = new MachineState(64);
        state.setReg32("eax", 0);
        // testl %eax, %eax → AND(0,0) = 0 → zero flag set
        exec("testl", List.of(reg("eax"), reg("eax")), state);
        assertTrue(execBranch("je", state));
    }

    @Test
    void testlNonZero() {
        var state = new MachineState(64);
        state.setReg32("eax", 42);
        exec("testl", List.of(reg("eax"), reg("eax")), state);
        assertFalse(execBranch("je", state));
    }

    @Test
    void nopDoesNothing() {
        var state = new MachineState(64);
        state.setReg64("rax", 42);
        exec("nop", List.of(), state);
        assertEquals(42, state.getReg("rax"));
    }

    @Test
    void retqReturnsFalse() {
        var state = new MachineState(64);
        assertFalse(exec("retq", List.of(), state));
    }

    @Test
    void sub32BitWriteRegisterAliasing() {
        var state = new MachineState(64);
        // Write 64-bit value, then overwrite with 32-bit
        state.setReg64("rax", 0xFFFFFFFF_12345678L);
        state.setReg32("eax", 1);
        // eax write zero-extends, so upper 32 bits are cleared
        assertEquals(1L, state.getReg("rax"));
    }

    @Test
    void unknownInstructionThrows() {
        var state = new MachineState(64);
        assertThrows(UnsupportedOperationException.class, () ->
            exec("vfmadd231ps", List.of(), state));
    }

    @Test
    void movlFromMemoryWithIndexAndScale() {
        var state = new MachineState(256);
        state.setReg64("r14", 0);
        state.setReg64("rax", 10);
        state.storeInt(4 + 10 * 8, 0x42);
        // movl 0x4(%r14,%rax,8),%edi
        exec("movl", List.of(
            new Operand.Memory("r14", "rax", 8, 4),
            reg("edi")), state);
        assertEquals(0x42, state.getReg("rdi"));
    }

    @Test
    void leaqWithNegativeDisplacement() {
        var state = new MachineState(64);
        state.setReg64("rsp", 100);
        // leaq -0x18(%rsp), %rbx → rbx = 100 - 24 = 76
        exec("leaq", List.of(mem("rsp", -0x18), reg("rbx")), state);
        assertEquals(76, state.getReg("rbx"));
    }

    // --- Helpers ---

    private static boolean exec(String mnemonic, List<Operand> operands, MachineState state) {
        var insn = new Instruction(-1, mnemonic, operands, mnemonic);
        return ScalarX86Semantics.execute(insn, state);
    }

    private static boolean execBranch(String mnemonic, MachineState state) {
        var insn = new Instruction(-1, mnemonic, List.of(new Operand.Address(0)), mnemonic);
        return ScalarX86Semantics.execute(insn, state);
    }

    private static Operand.Register reg(String name) { return new Operand.Register(name); }
    private static Operand.Immediate imm(long value) { return new Operand.Immediate(value); }
    private static Operand.Memory mem(String base, long disp) { return new Operand.Memory(base, disp); }
}

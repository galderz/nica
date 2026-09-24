package org.mendrugo.nica.semantics.aarch64;

import org.junit.jupiter.api.Test;
import org.mendrugo.nica.asm.Instruction;
import org.mendrugo.nica.asm.Operand;
import org.mendrugo.nica.semantics.MachineState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScalarAArch64SemanticsTest {

    @Test
    void sxtwSignExtendsNegative() {
        var state = new MachineState(64);
        // w17 = -1 (0xFFFFFFFF as unsigned 32-bit)
        state.setReg32("w17", -1);
        exec("sxtw", List.of(reg("x12"), reg("w17")), state);
        assertEquals(0xFFFFFFFFFFFFFFFFL, state.getReg("x12"));
    }

    @Test
    void sxtwSignExtendsPositive() {
        var state = new MachineState(64);
        state.setReg32("w17", 42);
        exec("sxtw", List.of(reg("x12"), reg("w17")), state);
        assertEquals(42L, state.getReg("x12"));
    }

    @Test
    void addRegisterForm() {
        var state = new MachineState(64);
        state.setReg64("x2", 100);
        state.setReg64("x12", 50);
        // add x14, x2, x12
        exec("add", List.of(reg("x14"), reg("x2"), reg("x12")), state);
        assertEquals(150L, state.getReg("x14"));
    }

    @Test
    void addImmediateForm() {
        var state = new MachineState(64);
        state.setReg32("w17", 20);
        // add w14, w17, #0x10  →  w14 = 20 + 16 = 36 (zero-extended)
        exec("add", List.of(reg("w14"), reg("w17"), imm(0x10)), state);
        assertEquals(36L, state.getReg("x14"));
    }

    @Test
    void ldrScalarFromMemory() {
        var state = new MachineState(256);
        // Store 4 bytes at address 0x10 from base x14=100: addr = 100 + 0x10 = 116
        byte[] data = {10, 20, (byte) -1, 127};
        state.storeBytes(116, data);
        state.setReg64("x14", 100);

        // ldr s21, [x14, #0x10]
        exec("ldr", List.of(reg("s21"),
            new Operand.Memory("x14", null, 1, 0x10)), state);

        int[] result = state.getSimd("s21", 4);
        assertEquals(10, result[0]);
        assertEquals(20, result[1]);
        assertEquals(0xFF, result[2]); // -1 as unsigned byte
        assertEquals(127, result[3]);
    }

    @Test
    void cmpAndBltTaken() {
        var state = new MachineState(64);
        state.setReg32("w14", 5);
        state.setReg32("w13", 100);
        exec("cmp", List.of(reg("w14"), reg("w13")), state);
        // 5 < 100, so b.lt should be taken
        assertTrue(execBranch("b.lt", state));
    }

    @Test
    void cmpAndBltNotTaken() {
        var state = new MachineState(64);
        state.setReg32("w14", 100);
        state.setReg32("w13", 50);
        exec("cmp", List.of(reg("w14"), reg("w13")), state);
        assertFalse(execBranch("b.lt", state));
    }

    @Test
    void cmpEqual() {
        var state = new MachineState(64);
        state.setReg32("w14", 42);
        state.setReg32("w13", 42);
        exec("cmp", List.of(reg("w14"), reg("w13")), state);
        assertFalse(execBranch("b.lt", state));
    }

    @Test
    void unknownInstructionThrows() {
        var state = new MachineState(64);
        assertThrows(UnsupportedOperationException.class, () ->
            exec("fmov", List.of(), state));
    }

    // --- Helpers ---

    private static void exec(String mnemonic, List<Operand> operands, MachineState state) {
        var insn = new Instruction(-1, mnemonic, operands, mnemonic);
        ScalarAArch64Semantics.execute(insn, state);
    }

    private static boolean execBranch(String mnemonic, MachineState state) {
        var insn = new Instruction(-1, mnemonic,
            List.of(new Operand.Address(0)), mnemonic);
        return ScalarAArch64Semantics.execute(insn, state);
    }

    private static Operand.Register reg(String name) { return new Operand.Register(name); }
    private static Operand.Immediate imm(long value) { return new Operand.Immediate(value); }
}

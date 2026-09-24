package org.mendrugo.nica.semantics.aarch64;

import org.junit.jupiter.api.Test;
import org.mendrugo.nica.asm.Instruction;
import org.mendrugo.nica.asm.Operand;
import org.mendrugo.nica.semantics.MachineState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimdAArch64SemanticsTest {

    @Test
    void sshllBytesToHalfwords() {
        var state = new MachineState(64);
        // 8 bytes: [1, -1, 127, -128, 0, 2, -2, 64]
        state.setSimd("v26", new int[]{1, 0xFF, 127, 0x80, 0, 2, 0xFE, 64});

        // sshll v26.8h, v26.8b, #0
        exec("sshll", List.of(vreg("v26", "8h"), vreg("v26", "8b"), imm(0)), state);

        int[] result = state.getSimd("v26", 8);
        assertArrayEquals(new int[]{1, -1, 127, -128, 0, 2, -2, 64}, result);
    }

    @Test
    void sshllHalfwordsToInts() {
        var state = new MachineState(64);
        // 4 halfwords already sign-extended from bytes
        state.setSimd("v26", new int[]{1, -1, 127, -128});

        // sshll v26.4s, v26.4h, #0
        exec("sshll", List.of(vreg("v26", "4s"), vreg("v26", "4h"), imm(0)), state);

        int[] result = state.getSimd("v26", 4);
        assertArrayEquals(new int[]{1, -1, 127, -128}, result);
    }

    @Test
    void twoStepSshllMatchesVpmovsxbd() {
        var state = new MachineState(64);
        // Same bytes as x86 vpmovsxbd test
        state.setSimd("v26", new int[]{1, 0xFF, 127, 0x80, 0, 2, 0xFE, 64});

        // Two-step sign extension: .8b → .8h → .4s (first 4 elements)
        exec("sshll", List.of(vreg("v26", "8h"), vreg("v26", "8b"), imm(0)), state);
        exec("sshll", List.of(vreg("v26", "4s"), vreg("v26", "4h"), imm(0)), state);

        int[] result = state.getSimd("v26", 4);
        // Should match sign-extended bytes: 1, -1, 127, -128
        assertArrayEquals(new int[]{1, -1, 127, -128}, result);
    }

    @Test
    void mulVector() {
        var state = new MachineState(64);
        state.setSimd("v22", new int[]{2, 3, 4, 5});
        state.setSimd("v21", new int[]{10, 20, 30, 40});

        exec("mul", List.of(vreg("v28", "4s"), vreg("v22", "4s"), vreg("v21", "4s")), state);

        assertArrayEquals(new int[]{20, 60, 120, 200}, state.getSimd("v28", 4));
    }

    @Test
    void mlaMultiplyAccumulate() {
        var state = new MachineState(64);
        state.setSimd("v22", new int[]{100, 200, 300, 400}); // accumulator
        state.setSimd("v20", new int[]{1, 2, 3, 4});
        state.setSimd("v29", new int[]{10, 20, 30, 40});

        // mla v22.4s, v20.4s, v29.4s → v22 += v20 * v29
        exec("mla", List.of(vreg("v22", "4s"), vreg("v20", "4s"), vreg("v29", "4s")), state);

        // 100+1*10=110, 200+2*20=240, 300+3*30=390, 400+4*40=560
        assertArrayEquals(new int[]{110, 240, 390, 560}, state.getSimd("v22", 4));
    }

    @Test
    void mlaIsSameAsSeparateMulAndAdd() {
        var state = new MachineState(64);
        int[] acc = {5, 10, 15, 20};
        int[] a = {2, 3, 4, 5};
        int[] b = {7, 8, 9, 10};

        // MLA approach
        state.setSimd("v1", acc.clone());
        state.setSimd("v2", a);
        state.setSimd("v3", b);
        exec("mla", List.of(vreg("v1", "4s"), vreg("v2", "4s"), vreg("v3", "4s")), state);

        // Separate mul + add approach
        state.setSimd("v4", a);
        state.setSimd("v5", b);
        exec("mul", List.of(vreg("v6", "4s"), vreg("v4", "4s"), vreg("v5", "4s")), state);
        state.setSimd("v7", acc.clone());
        exec("add", List.of(vreg("v7", "4s"), vreg("v7", "4s"), vreg("v6", "4s")), state);

        assertArrayEquals(state.getSimd("v1", 4), state.getSimd("v7", 4));
    }

    @Test
    void addVector() {
        var state = new MachineState(64);
        state.setSimd("v23", new int[]{1, 2, 3, 4});
        state.setSimd("v25", new int[]{10, 20, 30, 40});

        exec("add", List.of(vreg("v27", "4s"), vreg("v23", "4s"), vreg("v25", "4s")), state);

        assertArrayEquals(new int[]{11, 22, 33, 44}, state.getSimd("v27", 4));
    }

    @Test
    void shlVector() {
        var state = new MachineState(64);
        state.setSimd("v28", new int[]{1, -1, 127, -128});

        exec("shl", List.of(vreg("v16", "4s"), vreg("v28", "4s"), imm(0x18)), state);

        int[] result = state.getSimd("v16", 4);
        assertEquals(0x01000000, result[0]);
        assertEquals(0xFF000000, result[1]);
    }

    @Test
    void sshrVector() {
        var state = new MachineState(64);
        state.setSimd("v18", new int[]{0x01000000, 0xFF000000, 0x7F000000, 0x80000000});

        exec("sshr", List.of(vreg("v18", "4s"), vreg("v18", "4s"), imm(0x18)), state);

        int[] result = state.getSimd("v18", 4);
        assertEquals(1, result[0]);
        assertEquals(-1, result[1]);
        assertEquals(127, result[2]);
        assertEquals(-128, result[3]);
    }

    @Test
    void eor3ThreeWayXor() {
        var state = new MachineState(64);
        state.setSimd("v16", new int[]{0xFF, 0x00, 0xAA, 0x55});
        state.setSimd("v18", new int[]{0x0F, 0x0F, 0x55, 0xAA});
        state.setSimd("v31", new int[]{0x01, 0x02, 0x03, 0x04});

        exec("eor3", List.of(
            vreg("v16", "16b"), vreg("v16", "16b"),
            vreg("v18", "16b"), vreg("v31", "16b")), state);

        int[] result = state.getSimd("v16", 4);
        assertEquals(0xFF ^ 0x0F ^ 0x01, result[0]);
        assertEquals(0x00 ^ 0x0F ^ 0x02, result[1]);
        assertEquals(0xAA ^ 0x55 ^ 0x03, result[2]);
        assertEquals(0x55 ^ 0xAA ^ 0x04, result[3]);
    }

    @Test
    void isSimdDetection() {
        assertTrue(SimdAArch64Semantics.isSimd(
            new Instruction(-1, "sshll",
                List.of(vreg("v26", "8h"), vreg("v26", "8b"), imm(0)), "")));
        assertFalse(SimdAArch64Semantics.isSimd(
            new Instruction(-1, "add",
                List.of(reg("x14"), reg("x2"), reg("x12")), "")));
    }

    // --- Helpers ---

    private static void exec(String mnemonic, List<Operand> operands, MachineState state) {
        var insn = new Instruction(-1, mnemonic, operands, mnemonic);
        SimdAArch64Semantics.execute(insn, state);
    }

    private static Operand.Register vreg(String name, String arr) {
        return new Operand.Register(name, arr);
    }
    private static Operand.Register reg(String name) { return new Operand.Register(name); }
    private static Operand.Immediate imm(long value) { return new Operand.Immediate(value); }
}

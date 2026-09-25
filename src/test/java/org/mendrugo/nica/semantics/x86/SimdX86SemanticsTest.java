package org.mendrugo.nica.semantics.x86;

import org.junit.jupiter.api.Test;
import org.mendrugo.nica.asm.Instruction;
import org.mendrugo.nica.asm.Operand;
import org.mendrugo.nica.semantics.MachineState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimdX86SemanticsTest {

    @Test
    void vpmovsxbdSignExtends() {
        var state = new MachineState(64);
        // Store bytes: [1, -1, 127, -128, 0, 2, -2, 64]
        int[] bytes = {1, 0xFF, 127, 0x80, 0, 2, 0xFE, 64};
        state.setSimd("xmm10", bytes);

        exec("vpmovsxbd", List.of(reg("xmm10"), reg("ymm13")), state);

        int[] result = state.getSimd("ymm13", 8);
        assertArrayEquals(new int[]{1, -1, 127, -128, 0, 2, -2, 64}, result);
    }

    @Test
    void vpmulldElementWise() {
        var state = new MachineState(64);
        state.setSimd("ymm9", new int[]{1, 2, 3, 4, 5, 6, 7, 8});
        state.setSimd("ymm10", new int[]{10, 20, 30, 40, 50, 60, 70, 80});

        // vpmulld %ymm9, %ymm10, %ymm12
        exec("vpmulld", List.of(reg("ymm9"), reg("ymm10"), reg("ymm12")), state);

        int[] result = state.getSimd("ymm12", 8);
        assertArrayEquals(new int[]{10, 40, 90, 160, 250, 360, 490, 640}, result);
    }

    @Test
    void vpadddElementWise() {
        var state = new MachineState(64);
        state.setSimd("ymm9", new int[]{1, 2, 3, 4, 5, 6, 7, 8});
        state.setSimd("ymm10", new int[]{10, 20, 30, 40, 50, 60, 70, 80});

        exec("vpaddd", List.of(reg("ymm9"), reg("ymm10"), reg("ymm9")), state);

        int[] result = state.getSimd("ymm9", 8);
        assertArrayEquals(new int[]{11, 22, 33, 44, 55, 66, 77, 88}, result);
    }

    @Test
    void vpslldShiftLeft() {
        var state = new MachineState(64);
        state.setSimd("ymm8", new int[]{1, -1, 0x7F, 0, 0xFF, 2, -128, 64});

        // vpslld $0x18, %ymm8, %ymm8
        exec("vpslld", List.of(imm(0x18), reg("ymm8"), reg("ymm8")), state);

        int[] result = state.getSimd("ymm8", 8);
        // 1 << 24 = 0x01000000
        assertEquals(0x01000000, result[0]);
        // -1 << 24 = 0xFF000000
        assertEquals(0xFF000000, result[1]);
    }

    @Test
    void vpsradArithmeticShiftRight() {
        var state = new MachineState(64);
        // After shift left by 24, shift right by 24 should sign-extend back
        state.setSimd("ymm8", new int[]{0x01000000, 0xFF000000, 0x7F000000, 0x80000000, 0, 0x02000000, 0x80000000, 0x40000000});

        exec("vpsrad", List.of(imm(0x18), reg("ymm8"), reg("ymm7")), state);

        int[] result = state.getSimd("ymm7", 8);
        assertEquals(1, result[0]);
        assertEquals(-1, result[1]);
        assertEquals(127, result[2]);
        assertEquals(-128, result[3]);
        assertEquals(0, result[4]);
        assertEquals(2, result[5]);
        assertEquals(-128, result[6]);
        assertEquals(64, result[7]);
    }

    @Test
    void shiftLeftThenRightIsSignExtendToSByte() {
        var state = new MachineState(64);
        // Value 300 doesn't fit in a signed byte (-128..127)
        // shl 24 then sar 24 should truncate to signed byte range
        state.setSimd("ymm0", new int[]{300, -300, 127, -128, 0, 255, 256, 1});

        exec("vpslld", List.of(imm(24), reg("ymm0"), reg("ymm0")), state);
        exec("vpsrad", List.of(imm(24), reg("ymm0"), reg("ymm0")), state);

        int[] result = state.getSimd("ymm0", 8);
        assertEquals((byte) 300, result[0]);   // 44
        assertEquals((byte) -300, result[1]);  // -44
        assertEquals(127, result[2]);
        assertEquals(-128, result[3]);
        assertEquals(0, result[4]);
        assertEquals(-1, result[5]);           // 255 → (byte)255 = -1
        assertEquals(0, result[6]);            // 256 → (byte)256 = 0
        assertEquals(1, result[7]);
    }

    @Test
    void vpxorElementWise() {
        var state = new MachineState(64);
        state.setSimd("ymm0", new int[]{0xFF, 0x00, 0xAA, 0x55, 1, 2, 3, 4});
        state.setSimd("ymm1", new int[]{0x0F, 0x0F, 0x55, 0xAA, 4, 3, 2, 1});

        exec("vpxor", List.of(reg("ymm0"), reg("ymm1"), reg("ymm0")), state);

        int[] result = state.getSimd("ymm0", 8);
        assertEquals(0xF0, result[0]);
        assertEquals(0x0F, result[1]);
        assertEquals(0xFF, result[2]);
        assertEquals(0xFF, result[3]);
        assertEquals(5, result[4]);
        assertEquals(1, result[5]);
        assertEquals(1, result[6]);
        assertEquals(5, result[7]);
    }

    @Test
    void vmovqFromMemoryToXmm() {
        var state = new MachineState(256);
        // Store 8 bytes at address 20: [10, 20, -1, 127, -128, 0, 1, 2]
        byte[] data = {10, 20, (byte) -1, 127, (byte) -128, 0, 1, 2};
        state.storeBytes(20, data);
        state.setReg64("rsi", 0);
        state.setReg64("r9", 0);

        // vmovq 0x14(%rsi, %r9), %xmm10
        exec("vmovq", List.of(
            new Operand.Memory("rsi", "r9", 1, 0x14),
            reg("xmm10")), state);

        int[] result = state.getSimd("xmm10", 8);
        // Bytes loaded as unsigned values into int slots
        assertEquals(10, result[0]);
        assertEquals(20, result[1]);
        assertEquals(0xFF, result[2]);  // -1 as unsigned byte
        assertEquals(127, result[3]);
        assertEquals(0x80, result[4]);  // -128 as unsigned byte
        assertEquals(0, result[5]);
        assertEquals(1, result[6]);
        assertEquals(2, result[7]);
    }

    @Test
    void vmovqFromXmmToGpRegister() {
        var state = new MachineState(64);
        // Put bytes [0x01, 0x02, 0, 0, 0, 0, 0, 0] in xmm0
        state.setSimd("xmm0", new int[]{0x01, 0x02, 0, 0, 0, 0, 0, 0});

        // vmovq %xmm0, %rsi
        exec("vmovq", List.of(reg("xmm0"), reg("rsi")), state);

        // Low 64 bits reconstructed as little-endian
        assertEquals(0x0201L, state.getReg("rsi"));
    }

    @Test
    void chainVmovqVpmovsxbdVpmulldVpaddd() {
        // Full chain from the example: load → sign-extend → multiply → add
        var state = new MachineState(256);
        byte[] data1 = {1, -1, 2, -2, 3, -3, 4, -4};
        byte[] data2 = {10, 20, 30, 40, 50, 60, 70, 80};
        state.storeBytes(100, data1);
        state.storeBytes(200, data2);
        state.setReg64("rsi", 100);
        state.setReg64("rdx", 200);

        // Load
        exec("vmovq", List.of(mem("rsi", 0), reg("xmm0")), state);
        exec("vmovq", List.of(mem("rdx", 0), reg("xmm1")), state);

        // Sign-extend bytes → ints
        exec("vpmovsxbd", List.of(reg("xmm0"), reg("ymm0")), state);
        exec("vpmovsxbd", List.of(reg("xmm1"), reg("ymm1")), state);

        // Multiply
        exec("vpmulld", List.of(reg("ymm0"), reg("ymm1"), reg("ymm2")), state);

        // Add
        exec("vpaddd", List.of(reg("ymm0"), reg("ymm1"), reg("ymm3")), state);

        int[] mul = state.getSimd("ymm2", 8);
        int[] add = state.getSimd("ymm3", 8);

        // 1*10=10, (-1)*20=-20, 2*30=60, (-2)*40=-80, 3*50=150, (-3)*60=-180, 4*70=280, (-4)*80=-320
        assertArrayEquals(new int[]{10, -20, 60, -80, 150, -180, 280, -320}, mul);
        // 1+10=11, (-1)+20=19, 2+30=32, (-2)+40=38, 3+50=53, (-3)+60=57, 4+70=74, (-4)+80=76
        assertArrayEquals(new int[]{11, 19, 32, 38, 53, 57, 74, 76}, add);
    }

    @Test
    void isSimdDetection() {
        assertTrue(SimdX86Semantics.isSimd("vmovq"));
        assertTrue(SimdX86Semantics.isSimd("vpmovsxbd"));
        assertTrue(SimdX86Semantics.isSimd("vpmulld"));
        assertFalse(SimdX86Semantics.isSimd("movq"));
        assertFalse(SimdX86Semantics.isSimd("cmpl"));
    }

    // --- Helpers ---

    private static void exec(String mnemonic, List<Operand> operands, MachineState state) {
        var insn = new Instruction(-1, mnemonic, operands, mnemonic);
        SimdX86Semantics.execute(insn, state);
    }

    private static Operand.Register reg(String name) { return new Operand.Register(name); }
    private static Operand.Immediate imm(long value) { return new Operand.Immediate(value); }
    private static Operand.Memory mem(String base, long disp) { return new Operand.Memory(base, disp); }
}

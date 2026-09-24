package org.mendrugo.nica.semantics.x86;

import org.mendrugo.nica.asm.Instruction;
import org.mendrugo.nica.asm.Operand;
import org.mendrugo.nica.semantics.MachineState;

import java.util.List;

/**
 * Executes AVX2 SIMD x86_64 instructions against a {@link MachineState}.
 *
 * <p>YMM registers (256-bit) are modeled as {@code int[8]}.
 * XMM registers hold 64-bit values (for {@code vmovq} loads) and are
 * stored in the same SIMD register file with only the low 8 bytes populated.</p>
 *
 * <p>All AVX2 instructions use 3-operand form (AT&T syntax):
 * {@code op src1, src2, dst} — destination is always the last operand.</p>
 */
public final class SimdX86Semantics {

    private SimdX86Semantics() {}

    /**
     * Execute a SIMD x86_64 instruction. Returns true always (SIMD ops don't branch).
     */
    public static boolean execute(Instruction insn, MachineState state) {
        return switch (insn.mnemonic()) {
            case "vmovq" -> { vmovq(insn.operands(), state); yield true; }
            case "vpmovsxbd" -> { vpmovsxbd(insn.operands(), state); yield true; }
            case "vpmulld" -> { vpmulld(insn.operands(), state); yield true; }
            case "vpaddd" -> { vpaddd(insn.operands(), state); yield true; }
            case "vpslld" -> { vpslld(insn.operands(), state); yield true; }
            case "vpsrad" -> { vpsrad(insn.operands(), state); yield true; }
            case "vpxor" -> { vpxor(insn.operands(), state); yield true; }
            default -> throw new UnsupportedOperationException(
                "Unknown SIMD x86 instruction: " + insn.mnemonic());
        };
    }

    /**
     * Check if the mnemonic is a SIMD instruction handled by this class.
     */
    public static boolean isSimd(String mnemonic) {
        return switch (mnemonic) {
            case "vmovq", "vpmovsxbd", "vpmulld", "vpaddd",
                 "vpslld", "vpsrad", "vpxor" -> true;
            default -> false;
        };
    }

    // --- vmovq: Move 64 bits to/from XMM register ---
    // Two forms:
    //   vmovq mem, %xmm  → load 8 bytes from memory into xmm (as raw bytes for later sign-extend)
    //   vmovq %xmm, %reg → move low 64 bits of xmm to a GP register
    private static void vmovq(List<Operand> ops, MachineState state) {
        Operand src = ops.get(0);
        Operand dst = ops.get(1);

        if (src instanceof Operand.Memory m && dst instanceof Operand.Register r) {
            // Load 8 bytes from memory
            long addr = ScalarX86Semantics.computeEffectiveAddress(src, state);
            long value = state.load8Bytes(addr);
            // Store as raw bytes in the SIMD register's int array
            // We pack the 8 bytes into the low portion of an int[8]
            int[] simd = new int[8];
            for (int i = 0; i < 8; i++) {
                simd[i] = (int) ((value >> (i * 8)) & 0xFF);
            }
            state.setSimd(r.name(), simd);
        } else if (src instanceof Operand.Register rs && dst instanceof Operand.Register rd) {
            if (rs.name().startsWith("xmm") || rs.name().startsWith("ymm")) {
                // xmm → GP register: extract the low 64 bits as a long
                int[] simd = state.getSimd(rs.name(), 8);
                long value = 0;
                for (int i = 0; i < 8; i++) {
                    value |= (long) (simd[i] & 0xFF) << (i * 8);
                }
                state.setReg64(rd.name(), value);
            } else {
                // GP register → xmm: store register value as bytes
                long value = state.getReg(rs.name());
                int[] simd = new int[8];
                for (int i = 0; i < 8; i++) {
                    simd[i] = (int) ((value >> (i * 8)) & 0xFF);
                }
                state.setSimd(rd.name(), simd);
            }
        }
    }

    // --- vpmovsxbd: Sign-extend 8 packed bytes → 8 packed 32-bit ints ---
    // vpmovsxbd %xmm_src, %ymm_dst
    private static void vpmovsxbd(List<Operand> ops, MachineState state) {
        String srcName = ((Operand.Register) ops.get(0)).name();
        String dstName = ((Operand.Register) ops.get(1)).name();
        int[] src = state.getSimd(srcName, 8);
        int[] dst = new int[8];
        for (int i = 0; i < 8; i++) {
            // Sign-extend byte to int
            dst[i] = (byte) src[i];
        }
        state.setSimd(dstName, dst);
    }

    // --- vpmulld: Element-wise 32-bit multiply ---
    // vpmulld %ymm_a, %ymm_b, %ymm_dst (AT&T: src1, src2, dst)
    private static void vpmulld(List<Operand> ops, MachineState state) {
        int[] a = getYmm(ops.get(0), state);
        int[] b = getYmm(ops.get(1), state);
        int[] dst = new int[8];
        for (int i = 0; i < 8; i++) {
            dst[i] = a[i] * b[i];
        }
        setYmm(ops.get(2), dst, state);
    }

    // --- vpaddd: Element-wise 32-bit add ---
    private static void vpaddd(List<Operand> ops, MachineState state) {
        int[] a = getYmm(ops.get(0), state);
        int[] b = getYmm(ops.get(1), state);
        int[] dst = new int[8];
        for (int i = 0; i < 8; i++) {
            dst[i] = a[i] + b[i];
        }
        setYmm(ops.get(2), dst, state);
    }

    // --- vpslld: Element-wise left shift by immediate ---
    // vpslld $imm, %ymm_src, %ymm_dst
    private static void vpslld(List<Operand> ops, MachineState state) {
        int shift = (int) ((Operand.Immediate) ops.get(0)).value();
        int[] src = getYmm(ops.get(1), state);
        int[] dst = new int[8];
        for (int i = 0; i < 8; i++) {
            dst[i] = src[i] << shift;
        }
        setYmm(ops.get(2), dst, state);
    }

    // --- vpsrad: Element-wise arithmetic right shift by immediate ---
    private static void vpsrad(List<Operand> ops, MachineState state) {
        int shift = (int) ((Operand.Immediate) ops.get(0)).value();
        int[] src = getYmm(ops.get(1), state);
        int[] dst = new int[8];
        for (int i = 0; i < 8; i++) {
            dst[i] = src[i] >> shift;
        }
        setYmm(ops.get(2), dst, state);
    }

    // --- vpxor: Element-wise XOR ---
    private static void vpxor(List<Operand> ops, MachineState state) {
        int[] a = getYmm(ops.get(0), state);
        int[] b = getYmm(ops.get(1), state);
        int[] dst = new int[8];
        for (int i = 0; i < 8; i++) {
            dst[i] = a[i] ^ b[i];
        }
        setYmm(ops.get(2), dst, state);
    }

    // --- Helpers ---

    private static int[] getYmm(Operand op, MachineState state) {
        return state.getSimd(((Operand.Register) op).name(), 8);
    }

    private static void setYmm(Operand op, int[] values, MachineState state) {
        state.setSimd(((Operand.Register) op).name(), values);
    }
}

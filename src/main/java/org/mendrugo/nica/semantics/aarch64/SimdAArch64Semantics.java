package org.mendrugo.nica.semantics.aarch64;

import org.mendrugo.nica.asm.Instruction;
import org.mendrugo.nica.asm.Operand;
import org.mendrugo.nica.semantics.MachineState;

import java.util.List;

/**
 * Executes NEON SIMD aarch64 instructions against a {@link MachineState}.
 *
 * <p>Vector registers use arrangement specifiers (.8b, .8h, .4s, .16b)
 * to indicate lane count and element width. Internally, all vector data
 * is stored as {@code int[]} arrays in the machine state.</p>
 *
 * <p>Aarch64 syntax: destination first, sources after.</p>
 */
public final class SimdAArch64Semantics {

    private SimdAArch64Semantics() {}

    public static boolean execute(Instruction insn, MachineState state) {
        return switch (insn.mnemonic()) {
            case "sshll" -> { sshll(insn.operands(), state); yield true; }
            case "mul" -> { mulVec(insn.operands(), state); yield true; }
            case "mla" -> { mlaVec(insn.operands(), state); yield true; }
            case "shl" -> { shlVec(insn.operands(), state); yield true; }
            case "sshr" -> { sshrVec(insn.operands(), state); yield true; }
            case "eor3" -> { eor3(insn.operands(), state); yield true; }
            case "add" -> { addVec(insn.operands(), state); yield true; }
            default -> throw new UnsupportedOperationException(
                "Unknown SIMD aarch64 instruction: " + insn.mnemonic());
        };
    }

    /**
     * Check if this is a NEON vector instruction (has vector arrangement operands).
     */
    public static boolean isSimd(Instruction insn) {
        return insn.operands().stream().anyMatch(
            op -> op instanceof Operand.Register r && r.isVector());
    }

    // --- sshll: Signed shift left long (widen) ---
    // sshll v26.8h, v26.8b, #0  → widen 8 bytes to 8 halfwords
    // sshll v26.4s, v26.4h, #0  → widen 4 halfwords to 4 ints
    private static void sshll(List<Operand> ops, MachineState state) {
        var dst = (Operand.Register) ops.get(0);
        var src = (Operand.Register) ops.get(1);
        int shift = (int) ((Operand.Immediate) ops.get(2)).value();

        String srcArr = src.arrangement();
        int[] srcData = state.getSimd(src.name(), lanesFor(srcArr));
        int[] result;

        if (srcArr.endsWith("b")) {
            // .8b → .8h: sign-extend bytes to halfwords (stored as ints), then shift
            int lanes = parseLanes(srcArr);
            result = new int[lanes];
            for (int i = 0; i < lanes; i++) {
                result[i] = ((byte) srcData[i]) << shift;
            }
        } else if (srcArr.endsWith("h")) {
            // .4h → .4s: sign-extend halfwords to ints, then shift
            int lanes = parseLanes(srcArr);
            result = new int[lanes];
            for (int i = 0; i < lanes; i++) {
                result[i] = ((short) srcData[i]) << shift;
            }
        } else {
            throw new UnsupportedOperationException("sshll from arrangement: " + srcArr);
        }

        state.setSimd(dst.name(), result);
    }

    // --- mul: Element-wise vector multiply ---
    // mul v28.4s, v22.4s, v21.4s
    private static void mulVec(List<Operand> ops, MachineState state) {
        var dst = (Operand.Register) ops.get(0);
        var a = (Operand.Register) ops.get(1);
        var b = (Operand.Register) ops.get(2);
        int lanes = parseLanes(dst.arrangement());
        int[] va = state.getSimd(a.name(), lanes);
        int[] vb = state.getSimd(b.name(), lanes);
        int[] result = new int[lanes];
        for (int i = 0; i < lanes; i++) {
            result[i] = va[i] * vb[i];
        }
        state.setSimd(dst.name(), result);
    }

    // --- mla: Multiply-accumulate vector ---
    // mla v22.4s, v20.4s, v29.4s → v22 += v20 * v29
    private static void mlaVec(List<Operand> ops, MachineState state) {
        var dst = (Operand.Register) ops.get(0);
        var a = (Operand.Register) ops.get(1);
        var b = (Operand.Register) ops.get(2);
        int lanes = parseLanes(dst.arrangement());
        int[] vdst = state.getSimd(dst.name(), lanes);
        int[] va = state.getSimd(a.name(), lanes);
        int[] vb = state.getSimd(b.name(), lanes);
        int[] result = new int[lanes];
        for (int i = 0; i < lanes; i++) {
            result[i] = vdst[i] + va[i] * vb[i];
        }
        state.setSimd(dst.name(), result);
    }

    // --- add: Element-wise vector add ---
    // add v27.4s, v23.4s, v25.4s
    private static void addVec(List<Operand> ops, MachineState state) {
        var dst = (Operand.Register) ops.get(0);
        var a = (Operand.Register) ops.get(1);
        var b = (Operand.Register) ops.get(2);
        int lanes = parseLanes(dst.arrangement());
        int[] va = state.getSimd(a.name(), lanes);
        int[] vb = state.getSimd(b.name(), lanes);
        int[] result = new int[lanes];
        for (int i = 0; i < lanes; i++) {
            result[i] = va[i] + vb[i];
        }
        state.setSimd(dst.name(), result);
    }

    // --- shl: Shift left vector by immediate ---
    // shl v16.4s, v28.4s, #0x18
    private static void shlVec(List<Operand> ops, MachineState state) {
        var dst = (Operand.Register) ops.get(0);
        var src = (Operand.Register) ops.get(1);
        int shift = (int) ((Operand.Immediate) ops.get(2)).value();
        int lanes = parseLanes(dst.arrangement());
        int[] vs = state.getSimd(src.name(), lanes);
        int[] result = new int[lanes];
        for (int i = 0; i < lanes; i++) {
            result[i] = vs[i] << shift;
        }
        state.setSimd(dst.name(), result);
    }

    // --- sshr: Signed shift right vector by immediate ---
    // sshr v18.4s, v18.4s, #0x18
    private static void sshrVec(List<Operand> ops, MachineState state) {
        var dst = (Operand.Register) ops.get(0);
        var src = (Operand.Register) ops.get(1);
        int shift = (int) ((Operand.Immediate) ops.get(2)).value();
        int lanes = parseLanes(dst.arrangement());
        int[] vs = state.getSimd(src.name(), lanes);
        int[] result = new int[lanes];
        for (int i = 0; i < lanes; i++) {
            result[i] = vs[i] >> shift;
        }
        state.setSimd(dst.name(), result);
    }

    // --- eor3: Three-way exclusive OR ---
    // eor3 v16.16b, v16.16b, v18.16b, v31.16b → v16 = v16 ^ v18 ^ v31
    private static void eor3(List<Operand> ops, MachineState state) {
        var dst = (Operand.Register) ops.get(0);
        var a = (Operand.Register) ops.get(1);
        var b = (Operand.Register) ops.get(2);
        var c = (Operand.Register) ops.get(3);
        // .16b = 16 bytes, but we store as int[4] (32-bit granularity)
        // XOR at the int level is equivalent since XOR is bitwise
        int lanes = 4; // always operate at 4s granularity for int[]
        int[] va = state.getSimd(a.name(), lanes);
        int[] vb = state.getSimd(b.name(), lanes);
        int[] vc = state.getSimd(c.name(), lanes);
        int[] result = new int[lanes];
        for (int i = 0; i < lanes; i++) {
            result[i] = va[i] ^ vb[i] ^ vc[i];
        }
        state.setSimd(dst.name(), result);
    }

    // --- Helpers ---

    private static int parseLanes(String arrangement) {
        // "4s" → 4, "8b" → 8, "8h" → 8, "16b" → 16
        StringBuilder digits = new StringBuilder();
        for (char c : arrangement.toCharArray()) {
            if (Character.isDigit(c)) digits.append(c);
            else break;
        }
        return Integer.parseInt(digits.toString());
    }

    private static int lanesFor(String arrangement) {
        return parseLanes(arrangement);
    }
}

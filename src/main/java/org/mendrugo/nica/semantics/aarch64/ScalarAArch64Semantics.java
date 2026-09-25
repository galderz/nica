package org.mendrugo.nica.semantics.aarch64;

import org.mendrugo.nica.asm.Instruction;
import org.mendrugo.nica.asm.Operand;
import org.mendrugo.nica.semantics.MachineState;

import java.util.List;

/**
 * Executes scalar aarch64 instructions against a {@link MachineState}.
 *
 * <p>Aarch64 syntax: destination first, sources after.
 * {@code add x14, x2, x12} means x14 = x2 + x12.</p>
 */
public final class ScalarAArch64Semantics {

    private ScalarAArch64Semantics() {}

    /**
     * Execute a scalar aarch64 instruction.
     *
     * @return true to continue, false for taken branch (b.lt returns taken/not-taken)
     */
    public static boolean execute(Instruction insn, MachineState state) {
        return switch (insn.mnemonic()) {
            case "sxtw" -> { sxtw(insn.operands(), state); yield true; }
            case "add" -> { addScalar(insn.operands(), state); yield true; }
            case "ldr" -> { ldrScalar(insn.operands(), state); yield true; }
            case "cmp" -> { cmp(insn.operands(), state); yield true; }
            case "b.lt" -> state.getFlags() < 0;
            default -> throw new UnsupportedOperationException(
                "Unknown scalar aarch64 instruction: " + insn.mnemonic());
        };
    }

    // --- sxtw: Sign-extend 32-bit to 64-bit ---
    // sxtw xN, wN
    private static void sxtw(List<Operand> ops, MachineState state) {
        String src = ((Operand.Register) ops.get(1)).name();
        String dst = ((Operand.Register) ops.get(0)).name();
        int value32 = (int) state.getReg(src);
        state.setReg64(dst, value32); // int → long sign-extends naturally in Java
    }

    // --- add: register or immediate ---
    // add xN, xN, xN  or  add wN, wN, #imm
    private static void addScalar(List<Operand> ops, MachineState state) {
        String dst = ((Operand.Register) ops.get(0)).name();
        long src1 = readOperand(ops.get(1), state);
        long src2 = readOperand(ops.get(2), state);
        if (isWReg(dst)) {
            state.setReg32(dst, (int) (src1 + src2));
        } else {
            state.setReg64(dst, src1 + src2);
        }
    }

    // --- ldr: load from memory ---
    // ldr sN, [base, #disp]  →  load 4 bytes (scalar float reg used as 32-bit container)
    // ldr xN, [base, #disp]  →  load 8 bytes
    private static void ldrScalar(List<Operand> ops, MachineState state) {
        String dst = ((Operand.Register) ops.get(0)).name();
        var mem = (Operand.Memory) ops.get(1);
        long addr = computeAddress(mem, state);

        if (dst.startsWith("s")) {
            // s-register: load 4 bytes, store in SIMD register file
            // The 4 bytes contain packed byte data that will be later sign-extended
            int value = state.load4Bytes(addr);
            // Store bytes individually in an int[8] array (low 4 slots)
            int[] simd = new int[8];
            for (int i = 0; i < 4; i++) {
                simd[i] = (value >> (i * 8)) & 0xFF;
            }
            state.setSimd(dst, simd);
        } else if (isWReg(dst)) {
            state.setReg32(dst, state.loadInt(addr));
        } else {
            state.setReg64(dst, state.loadLong(addr));
        }
    }

    // --- cmp: compare ---
    // cmp wN, wN  or  cmp xN, xN
    private static void cmp(List<Operand> ops, MachineState state) {
        String reg1 = ((Operand.Register) ops.get(0)).name();
        if (isWReg(reg1)) {
            int a = (int) state.getReg(reg1);
            int b = (int) readOperand(ops.get(1), state);
            state.setFlagsFromCmp32(a, b);
        } else {
            long a = state.getReg(reg1);
            long b = readOperand(ops.get(1), state);
            state.setFlagsFromCmp64(a, b);
        }
    }

    // --- Helpers ---

    private static long readOperand(Operand op, MachineState state) {
        return switch (op) {
            case Operand.Register r -> state.getReg(r.name());
            case Operand.Immediate i -> i.value();
            case Operand.Memory m -> state.loadLong(computeAddress(m, state));
            case Operand.Address a -> a.address();
        };
    }

    private static long computeAddress(Operand.Memory m, MachineState state) {
        long addr = m.displacement();
        if (m.base() != null) addr += state.getReg(m.base());
        if (m.index() != null) addr += state.getReg(m.index()) * m.scale();
        return addr;
    }

    private static boolean isWReg(String name) {
        return name.startsWith("w") && name.length() > 1 && Character.isDigit(name.charAt(1));
    }
}

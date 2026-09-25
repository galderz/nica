package org.mendrugo.nica.semantics.x86;

import org.mendrugo.nica.asm.Instruction;
import org.mendrugo.nica.asm.Operand;
import org.mendrugo.nica.semantics.MachineState;

import java.util.List;

/**
 * Executes scalar x86_64 instructions against a {@link MachineState}.
 *
 * <p>AT&T syntax: source operands come first, destination last.</p>
 */
public final class ScalarX86Semantics {

    private ScalarX86Semantics() {}

    /**
     * Execute a scalar x86_64 instruction.
     *
     * @param insn the instruction to execute
     * @param state the machine state to mutate
     * @return true if execution should continue, false if a branch was not taken or retq
     * @throws UnsupportedOperationException for sub-32-bit register writes or unknown instructions
     */
    public static boolean execute(Instruction insn, MachineState state) {
        return switch (insn.mnemonic()) {
            case "movq" -> { movq(insn.operands(), state); yield true; }
            case "movl" -> { movl(insn.operands(), state); yield true; }
            case "leaq" -> { leaq(insn.operands(), state); yield true; }
            case "leal" -> { leal(insn.operands(), state); yield true; }
            case "addq" -> { addq(insn.operands(), state); yield true; }
            case "cmpl" -> { cmpl(insn.operands(), state); yield true; }
            case "cmpq" -> { cmpq(insn.operands(), state); yield true; }
            case "testl" -> { testl(insn.operands(), state); yield true; }
            case "nop" -> true;
            case "retq" -> false;
            case "jl" -> state.getFlags() < 0;
            case "jle" -> state.getFlags() <= 0;
            case "je" -> state.getFlags() == 0;
            case "jb" -> state.getFlags() < 0; // simplified: unsigned less
            case "jbe" -> state.getFlags() <= 0; // simplified: unsigned less-or-equal
            case "callq" -> true; // simplified: just continue
            default -> throw new UnsupportedOperationException(
                "Unknown scalar x86 instruction: " + insn.mnemonic());
        };
    }

    // --- movq: 64-bit move ---
    private static void movq(List<Operand> ops, MachineState state) {
        long value = readOperand64(ops.get(0), state);
        writeOperand64(ops.get(1), value, state);
    }

    // --- movl: 32-bit move, zero-extends to 64 ---
    private static void movl(List<Operand> ops, MachineState state) {
        int value = readOperand32(ops.get(0), state);
        writeOperand32(ops.get(1), value, state);
    }

    // --- leaq: load effective address 64-bit ---
    private static void leaq(List<Operand> ops, MachineState state) {
        long addr = computeEffectiveAddress(ops.get(0), state);
        writeOperand64(ops.get(1), addr, state);
    }

    // --- leal: load effective address 32-bit, zero-extends ---
    private static void leal(List<Operand> ops, MachineState state) {
        long addr = computeEffectiveAddress(ops.get(0), state);
        writeOperand32(ops.get(1), (int) addr, state);
    }

    // --- addq: 64-bit add ---
    private static void addq(List<Operand> ops, MachineState state) {
        long src = readOperand64(ops.get(0), state);
        long dst = readOperand64(ops.get(1), state);
        writeOperand64(ops.get(1), dst + src, state);
    }

    // --- cmpl: 32-bit compare ---
    private static void cmpl(List<Operand> ops, MachineState state) {
        int src = readOperand32(ops.get(0), state);
        int dst = readOperand32(ops.get(1), state);
        // AT&T: cmpl src, dst → flags = dst - src
        state.setFlagsFromCmp32(dst, src);
    }

    // --- cmpq: 64-bit compare ---
    private static void cmpq(List<Operand> ops, MachineState state) {
        long src = readOperand64(ops.get(0), state);
        long dst = readOperand64(ops.get(1), state);
        state.setFlagsFromCmp64(dst, src);
    }

    // --- testl: bitwise AND, set flags ---
    private static void testl(List<Operand> ops, MachineState state) {
        int src = readOperand32(ops.get(0), state);
        int dst = readOperand32(ops.get(1), state);
        state.setFlagsFromTest(dst & src);
    }

    // --- Operand reading/writing helpers ---

    static long readOperand64(Operand op, MachineState state) {
        return switch (op) {
            case Operand.Register r -> state.getReg(r.name());
            case Operand.Immediate i -> i.value();
            case Operand.Memory m -> state.loadLong(computeAddress(m, state));
            case Operand.Address a -> a.address();
        };
    }

    static int readOperand32(Operand op, MachineState state) {
        return switch (op) {
            case Operand.Register r -> (int) state.getReg(r.name());
            case Operand.Immediate i -> (int) i.value();
            case Operand.Memory m -> state.loadInt(computeAddress(m, state));
            case Operand.Address a -> (int) a.address();
        };
    }

    static void writeOperand64(Operand op, long value, MachineState state) {
        switch (op) {
            case Operand.Register r -> state.setReg64(r.name(), value);
            case Operand.Memory m -> state.storeLong(computeAddress(m, state), value);
            default -> throw new IllegalArgumentException("Cannot write to: " + op);
        }
    }

    static void writeOperand32(Operand op, int value, MachineState state) {
        switch (op) {
            case Operand.Register r -> state.setReg32(r.name(), value);
            case Operand.Memory m -> state.storeInt(computeAddress(m, state), value);
            default -> throw new IllegalArgumentException("Cannot write to: " + op);
        }
    }

    /** Compute the effective address of a Memory operand. */
    static long computeEffectiveAddress(Operand op, MachineState state) {
        if (op instanceof Operand.Memory m) {
            return computeAddress(m, state);
        }
        throw new IllegalArgumentException("Expected Memory operand, got: " + op);
    }

    private static long computeAddress(Operand.Memory m, MachineState state) {
        long addr = m.displacement();
        if (m.base() != null) {
            addr += state.getReg(m.base());
        }
        if (m.index() != null) {
            addr += state.getReg(m.index()) * m.scale();
        }
        return addr;
    }
}

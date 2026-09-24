package org.mendrugo.nica.asm;

import java.util.List;

/**
 * A single disassembled instruction.
 *
 * @param address the instruction address, or -1 if not available
 * @param mnemonic the instruction mnemonic (e.g. "vmovq", "add", "b.lt")
 * @param operands the operands in source order (AT&T: src,...,dst for x86;
 *                 dst,src,... for aarch64)
 * @param rawText the original text line from the disassembly
 * @param annotation optional source-level annotation from JIT debug info, or null
 * @param perfPercent percentage from perf annotate, or NaN if not from perf output
 */
public record Instruction(
    long address,
    String mnemonic,
    List<Operand> operands,
    String rawText,
    SourceAnnotation annotation,
    double perfPercent
) {
    public Instruction(long address, String mnemonic, List<Operand> operands, String rawText) {
        this(address, mnemonic, operands, rawText, null, Double.NaN);
    }

    public Instruction(long address, String mnemonic, List<Operand> operands, String rawText,
                       SourceAnnotation annotation) {
        this(address, mnemonic, operands, rawText, annotation, Double.NaN);
    }

    /** True if this instruction has perf sampling data. */
    public boolean hasPerfData() {
        return !Double.isNaN(perfPercent);
    }
}

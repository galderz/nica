package org.mendrugo.nica.asm;

import java.util.List;

/**
 * A parsed block of disassembled instructions with metadata.
 *
 * @param architecture the target architecture
 * @param instructions the instructions in program order
 * @param blockInfo optional block header info (e.g. "B22: out(B21 B23) <- in(B25 B21)"), or null
 * @param methodSignature optional method signature (from perf annotate header), or null
 */
public record AssemblySnippet(
    Architecture architecture,
    List<Instruction> instructions,
    String blockInfo,
    String methodSignature
) {
    public AssemblySnippet(Architecture architecture, List<Instruction> instructions) {
        this(architecture, instructions, null, null);
    }
}

package org.mendrugo.nica.cheatsheet;

/**
 * Human-readable description of an assembly instruction.
 *
 * @param summary short English description of what the instruction does
 * @param javaEquivalent one-line Java pseudocode equivalent
 */
public record InstructionDescription(String summary, String javaEquivalent) {

    /** Sentinel for unsupported/unknown instructions. */
    public static final InstructionDescription UNSUPPORTED =
        new InstructionDescription("UNSUPPORTED", "// UNSUPPORTED");

    public boolean isUnsupported() {
        return this == UNSUPPORTED;
    }
}

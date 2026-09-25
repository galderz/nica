package org.mendrugo.nica.asm;

/**
 * An operand in a disassembled instruction.
 *
 * <p>Covers the operand forms found in x86_64 (AT&T syntax) and aarch64:
 * registers, immediates, memory references, and branch targets.</p>
 */
public sealed interface Operand {

    /**
     * A register operand.
     *
     * @param name the register name without prefix (e.g. "rax", "xmm0", "x12", "v26")
     * @param arrangement optional NEON vector arrangement specifier (e.g. "8b", "4s", "16b"),
     *                    null for scalar registers
     */
    record Register(String name, String arrangement) implements Operand {
        public Register(String name) {
            this(name, null);
        }

        /** True if this is a NEON/SIMD vector register with an arrangement specifier. */
        public boolean isVector() {
            return arrangement != null;
        }
    }

    /**
     * An immediate (constant) value.
     *
     * @param value the numeric value
     */
    record Immediate(long value) implements Operand {}

    /**
     * A memory reference: displacement(base, index, scale).
     *
     * <p>Represents both x86_64 AT&T syntax {@code disp(%base, %index, scale)}
     * and aarch64 syntax {@code [base, #disp]} / {@code [base, index]}.</p>
     *
     * @param base base register name, or null if absent
     * @param index index register name, or null if absent
     * @param scale index scale factor (1, 2, 4, or 8), meaningful only when index is present
     * @param displacement signed byte offset
     */
    record Memory(String base, String index, int scale, long displacement) implements Operand {
        public Memory {
            // If index is != null, scale must be > 1
            assert(index != null || scale == 1);
        }

        public Memory(String base, long displacement) {
            this(base, null, 1, displacement);
        }
    }

    /**
     * A branch or call target address, or a label name.
     *
     * @param address the absolute target address, or -1 if only a label is known
     * @param label the symbolic label (e.g. "43"), or null if only an address is known
     */
    record Address(long address, String label) implements Operand {
        public Address(long address) {
            this(address, null);
        }

        public Address(String label) {
            this(-1, label);
        }
    }
}

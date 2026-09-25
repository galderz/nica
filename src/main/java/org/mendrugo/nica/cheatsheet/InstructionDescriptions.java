package org.mendrugo.nica.cheatsheet;

import org.mendrugo.nica.asm.Architecture;

import java.util.Map;

/**
 * Registry of human-readable descriptions for assembly instruction mnemonics.
 *
 * <p>Architecture-aware: the same mnemonic (e.g. {@code add}) can have
 * different descriptions for x86_64 and aarch64.</p>
 */
public final class InstructionDescriptions {

    private InstructionDescriptions() {}

    // --- x86_64 descriptions ---

    private static final Map<String, InstructionDescription> X86_64 = Map.ofEntries(
        // Scalar data movement
        entry("movq",  "Move 64-bit value",
                        "dst = src"),
        entry("movl",  "Move 32-bit value (zero-extends to 64-bit)",
                        "rXX = (int) src  // upper 32 bits zeroed"),
        entry("leaq",  "Load effective address (64-bit)",
                        "dst = base + index * scale + disp  // no memory access"),
        entry("leal",  "Load effective address (32-bit, zero-extends)",
                        "dst = (int)(base + index * scale + disp)"),

        // Arithmetic
        entry("addq",  "Add 64-bit",
                        "dst += src"),

        // Comparison and test
        entry("cmpl",  "Compare 32-bit (sets flags, no result stored)",
                        "flags = Integer.compare(dst, src)"),
        entry("cmpq",  "Compare 64-bit (sets flags, no result stored)",
                        "flags = Long.compare(dst, src)"),
        entry("testl", "Bitwise AND 32-bit (sets flags, no result stored)",
                        "flags = (dst & src) == 0 ? ZERO : ..."),

        // Branches
        entry("jl",    "Jump if less (signed)",
                        "if (flags < 0) goto target"),
        entry("jle",   "Jump if less or equal (signed)",
                        "if (flags <= 0) goto target"),
        entry("je",    "Jump if equal (zero flag set)",
                        "if (flags == 0) goto target"),
        entry("jb",    "Jump if below (unsigned less than)",
                        "if (unsignedLess) goto target"),
        entry("jbe",   "Jump if below or equal (unsigned)",
                        "if (unsignedLessOrEqual) goto target"),

        // Control flow
        entry("callq", "Call function",
                        "push returnAddr; goto target"),
        entry("retq",  "Return from function",
                        "return  // pop return address and jump"),
        entry("nop",   "No operation",
                        "// do nothing"),

        // AVX2 SIMD
        entry("vmovq", "Move 64 bits (8 bytes) to/from XMM register",
                        "xmm = Memory.load8Bytes(addr)  // or reg-to-reg 64-bit move"),
        entry("vpmovsxbd", "Sign-extend 8 packed bytes → 8 packed 32-bit ints",
                        "for (i < 8) dst[i] = (int)(byte) src[i]"),
        entry("vpmulld", "Multiply 8 packed 32-bit ints (element-wise)",
                        "for (i < 8) dst[i] = a[i] * b[i]"),
        entry("vpaddd", "Add 8 packed 32-bit ints (element-wise)",
                        "for (i < 8) dst[i] = a[i] + b[i]"),
        entry("vpslld", "Shift left 8 packed 32-bit ints by immediate",
                        "for (i < 8) dst[i] = src[i] << imm"),
        entry("vpsrad", "Arithmetic shift right 8 packed 32-bit ints by immediate",
                        "for (i < 8) dst[i] = src[i] >> imm  // sign-extending"),
        entry("vpxor",  "XOR 8 packed 32-bit ints (element-wise)",
                        "for (i < 8) dst[i] = a[i] ^ b[i]")
    );

    // --- aarch64 descriptions ---

    private static final Map<String, InstructionDescription> AARCH64 = Map.ofEntries(
        // Scalar
        entry("sxtw",  "Sign-extend 32-bit to 64-bit",
                        "xN = (long)(int) wN"),
        entry("add",   "Add (register or immediate)",
                        "dst = src1 + src2"),
        entry("ldr",   "Load register from memory",
                        "reg = memory[base + offset]"),
        entry("cmp",   "Compare (sets flags, no result stored)",
                        "flags = Integer.compare(a, b)"),
        entry("b.lt",  "Branch if less than (signed)",
                        "if (flags < 0) goto target"),

        // NEON SIMD
        entry("sshll", "Signed shift left long — widen elements and shift",
                        "for each lane: dst[i] = (wider) src[i] << imm  // e.g. 8b→8h or 4h→4s"),
        entry("mul",   "Multiply vector (element-wise)",
                        "for each lane: dst[i] = a[i] * b[i]"),
        entry("mla",   "Multiply-accumulate vector (element-wise)",
                        "for each lane: dst[i] += a[i] * b[i]"),
        entry("shl",   "Shift left vector by immediate",
                        "for each lane: dst[i] = src[i] << imm"),
        entry("sshr",  "Signed shift right vector by immediate",
                        "for each lane: dst[i] = src[i] >> imm  // sign-extending"),
        entry("eor3",  "Three-way exclusive OR",
                        "dst = a ^ b ^ c")
    );

    /**
     * Look up the description for a mnemonic on the given architecture.
     *
     * @param mnemonic the instruction mnemonic (e.g. "vmovq", "sshll")
     * @param architecture the target architecture
     * @return the description, or {@link InstructionDescription#UNSUPPORTED} if unknown
     */
    public static InstructionDescription lookup(String mnemonic, Architecture architecture) {
        var map = switch (architecture) {
            case X86_64 -> X86_64;
            case AARCH64 -> AARCH64;
        };
        return map.getOrDefault(mnemonic, InstructionDescription.UNSUPPORTED);
    }

    private static Map.Entry<String, InstructionDescription> entry(
            String mnemonic, String summary, String javaEquivalent) {
        return Map.entry(mnemonic, new InstructionDescription(summary, javaEquivalent));
    }
}

package org.mendrugo.nica.codegen;

import org.mendrugo.nica.asm.*;

import java.util.List;

/**
 * Generates a runnable Java class that faithfully reproduces the computation
 * of an assembly snippet at the register level.
 *
 * <p>Each register maps to a Java variable. SIMD registers map to {@code int[]}
 * arrays. Each instruction maps to one or more Java statements, commented with
 * the original assembly. The generated class has a {@code run()} method that
 * takes input arrays and returns the result.</p>
 *
 * <p>This is the "Literal" view — tedious but exact.</p>
 */
public final class LiteralJavaGenerator {

    private LiteralJavaGenerator() {}

    /**
     * Generate a runnable Java source file from a parsed assembly snippet.
     *
     * @param snippet the parsed assembly
     * @param className the name for the generated class
     * @return the Java source code
     */
    public static String generate(AssemblySnippet snippet, String className) {
        return switch (snippet.architecture()) {
            case X86_64 -> generateX86(snippet, className);
            case AARCH64 -> generateAArch64(snippet, className);
        };
    }

    // --- x86_64 code generation ---

    private static String generateX86(AssemblySnippet snippet, String className) {
        var sb = new StringBuilder();
        emitHeader(sb, className, snippet);

        // Emit run method signature
        sb.append("    /**\n");
        sb.append("     * Execute the assembly computation.\n");
        sb.append("     * SIMD registers are modeled as int[8] (256-bit AVX2 ymm).\n");
        sb.append("     * Each int holds a 32-bit lane value.\n");
        sb.append("     */\n");
        sb.append("    public static int[] run(int[] xmm0_bytes, int[] xmm1_bytes");

        // Collect all xmm source registers from vmovq memory loads
        var loadRegs = snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("vmovq")
                && i.operands().getFirst() instanceof Operand.Memory)
            .map(i -> ((Operand.Register) i.operands().get(1)).name())
            .distinct()
            .toList();

        // If more than 2 input registers, add extra parameters
        for (int i = 2; i < loadRegs.size(); i++) {
            sb.append(", int[] ").append(loadRegs.get(i)).append("_bytes");
        }
        sb.append(") {\n");

        // Emit register declarations for all xmm/ymm registers used
        var allRegs = collectSimdRegisters(snippet);
        for (var reg : allRegs) {
            sb.append("        int[] ").append(sanitize(reg)).append(" = new int[8];\n");
        }
        sb.append('\n');

        // Map input parameters to registers
        for (int i = 0; i < Math.min(loadRegs.size(), 2); i++) {
            sb.append("        // Input: ").append(loadRegs.get(i)).append(" loaded from memory\n");
            sb.append("        System.arraycopy(");
            sb.append(i == 0 ? "xmm0_bytes" : "xmm1_bytes");
            sb.append(", 0, ").append(sanitize(loadRegs.get(i))).append(", 0, 8);\n");
        }
        sb.append('\n');

        // Track which vmovq load we've seen (to map to input params)
        int loadIndex = 0;

        // Emit each instruction
        for (var insn : snippet.instructions()) {
            switch (insn.mnemonic()) {
                case "vmovq" -> {
                    if (insn.operands().getFirst() instanceof Operand.Memory) {
                        String dst = regName(insn.operands().get(1));
                        if (loadIndex >= 2 && loadIndex < loadRegs.size()) {
                            emitComment(sb, insn);
                            sb.append("        System.arraycopy(")
                              .append(loadRegs.get(loadIndex)).append("_bytes, 0, ")
                              .append(sanitize(dst)).append(", 0, 8);\n");
                        } else if (loadIndex < 2) {
                            emitComment(sb, insn);
                            sb.append("        // (already loaded from parameter)\n");
                        }
                        loadIndex++;
                    } else {
                        // Register-to-register vmovq
                        emitComment(sb, insn);
                        String src = regName(insn.operands().get(0));
                        String dst = regName(insn.operands().get(1));
                        sb.append("        System.arraycopy(")
                          .append(sanitize(src)).append(", 0, ")
                          .append(sanitize(dst)).append(", 0, 8);\n");
                    }
                }
                case "vpmovsxbd" -> {
                    emitComment(sb, insn);
                    String src = regName(insn.operands().get(0));
                    String dst = regName(insn.operands().get(1));
                    sb.append("        for (int i = 0; i < 8; i++) ")
                      .append(sanitize(dst)).append("[i] = (byte) ")
                      .append(sanitize(src)).append("[i];\n");
                }
                case "vpmulld" -> emitSimdBinop(sb, insn, "*");
                case "vpaddd" -> emitSimdBinop(sb, insn, "+");
                case "vpxor" -> emitSimdBinop(sb, insn, "^");
                case "vpslld" -> emitSimdShift(sb, insn, "<<");
                case "vpsrad" -> emitSimdShift(sb, insn, ">>");
                case "leal", "cmpl", "jl" -> {
                    emitComment(sb, insn);
                    sb.append("        // (control flow — not modeled in literal view)\n");
                }
                default -> {
                    if (!insn.mnemonic().equals("nop")) {
                        emitComment(sb, insn);
                        sb.append("        // UNSUPPORTED: ").append(insn.mnemonic()).append('\n');
                    }
                }
            }
        }

        // Find result register (last vpxor destination)
        String resultReg = findLastXorDest(snippet);
        sb.append('\n');
        sb.append("        return ").append(sanitize(resultReg)).append(";\n");
        sb.append("    }\n");

        emitMainMethod(sb, className);
        sb.append("}\n");
        return sb.toString();
    }

    // --- aarch64 code generation ---

    private static String generateAArch64(AssemblySnippet snippet, String className) {
        var sb = new StringBuilder();
        emitHeader(sb, className, snippet);

        sb.append("    /**\n");
        sb.append("     * Execute the assembly computation.\n");
        sb.append("     * NEON vector registers are modeled as int[4] (.4s arrangement).\n");
        sb.append("     * Bytes are loaded via s-registers (4 bytes each).\n");
        sb.append("     */\n");

        // Collect s-register loads as parameters
        var loadRegs = snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("ldr")
                && i.operands().getFirst() instanceof Operand.Register r
                && r.name().startsWith("s"))
            .map(i -> ((Operand.Register) i.operands().getFirst()).name())
            .distinct()
            .toList();

        sb.append("    public static int[] run(");
        for (int i = 0; i < loadRegs.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("int[] ").append(loadRegs.get(i)).append("_bytes");
        }
        sb.append(") {\n");

        // Declare vector registers
        var vecRegs = collectVectorRegisters(snippet);
        for (var reg : vecRegs) {
            sb.append("        int[] ").append(sanitize(reg)).append(" = new int[8];\n");
        }
        sb.append('\n');

        // Copy inputs
        for (var reg : loadRegs) {
            sb.append("        System.arraycopy(").append(reg).append("_bytes, 0, ")
              .append(sanitize(reg)).append(", 0, 4);\n");
        }
        sb.append('\n');

        // Emit instructions
        for (var insn : snippet.instructions()) {
            boolean isVec = insn.operands().stream().anyMatch(
                op -> op instanceof Operand.Register r && r.isVector());

            switch (insn.mnemonic()) {
                case "ldr" -> {
                    emitComment(sb, insn);
                    sb.append("        // (loaded from parameter)\n");
                }
                case "sshll" -> {
                    emitComment(sb, insn);
                    var dst = (Operand.Register) insn.operands().get(0);
                    var src = (Operand.Register) insn.operands().get(1);
                    String srcArr = src.arrangement();
                    int lanes = parseLanes(dst.arrangement());
                    if (srcArr.endsWith("b")) {
                        sb.append("        for (int i = 0; i < ").append(lanes).append("; i++) ")
                          .append(sanitize(dst.name())).append("[i] = (byte) ")
                          .append(sanitize(src.name())).append("[i];\n");
                    } else if (srcArr.endsWith("h")) {
                        sb.append("        for (int i = 0; i < ").append(lanes).append("; i++) ")
                          .append(sanitize(dst.name())).append("[i] = (short) ")
                          .append(sanitize(src.name())).append("[i];\n");
                    }
                }
                case "mul" -> { if (isVec) emitVecBinop(sb, insn, "*"); }
                case "mla" -> {
                    if (isVec) {
                        emitComment(sb, insn);
                        var dst = (Operand.Register) insn.operands().get(0);
                        var a = (Operand.Register) insn.operands().get(1);
                        var b = (Operand.Register) insn.operands().get(2);
                        int lanes = parseLanes(dst.arrangement());
                        sb.append("        for (int i = 0; i < ").append(lanes).append("; i++) ")
                          .append(sanitize(dst.name())).append("[i] += ")
                          .append(sanitize(a.name())).append("[i] * ")
                          .append(sanitize(b.name())).append("[i];\n");
                    }
                }
                case "add" -> { if (isVec) emitVecBinop(sb, insn, "+"); }
                case "shl" -> { if (isVec) emitVecShift(sb, insn, "<<"); }
                case "sshr" -> { if (isVec) emitVecShift(sb, insn, ">>"); }
                case "eor3" -> {
                    if (isVec) {
                        emitComment(sb, insn);
                        var dst = (Operand.Register) insn.operands().get(0);
                        var a = (Operand.Register) insn.operands().get(1);
                        var b = (Operand.Register) insn.operands().get(2);
                        var c = (Operand.Register) insn.operands().get(3);
                        sb.append("        for (int i = 0; i < 4; i++) ")
                          .append(sanitize(dst.name())).append("[i] = ")
                          .append(sanitize(a.name())).append("[i] ^ ")
                          .append(sanitize(b.name())).append("[i] ^ ")
                          .append(sanitize(c.name())).append("[i];\n");
                    }
                }
                case "sxtw", "cmp", "b.lt" -> {
                    emitComment(sb, insn);
                    sb.append("        // (control flow — not modeled in literal view)\n");
                }
                default -> {
                    if (isVec || !insn.mnemonic().equals("nop")) {
                        emitComment(sb, insn);
                        if (!insn.mnemonic().equals("add")) // scalar add is control flow
                            sb.append("        // UNSUPPORTED: ").append(insn.mnemonic()).append('\n');
                        else
                            sb.append("        // (scalar add — control flow)\n");
                    }
                }
            }
        }

        String resultReg = findLastXorDestAArch64(snippet);
        sb.append('\n');
        sb.append("        return ").append(sanitize(resultReg)).append(";\n");
        sb.append("    }\n");

        emitMainMethod(sb, className);
        sb.append("}\n");
        return sb.toString();
    }

    // --- Shared helpers ---

    private static void emitHeader(StringBuilder sb, String className, AssemblySnippet snippet) {
        sb.append("/**\n");
        sb.append(" * Literal register-level Java translation of ").append(snippet.architecture()).append(" assembly.\n");
        if (snippet.blockInfo() != null)
            sb.append(" * Block: ").append(snippet.blockInfo()).append('\n');
        if (snippet.methodSignature() != null)
            sb.append(" * Method: ").append(snippet.methodSignature()).append('\n');
        sb.append(" *\n");
        sb.append(" * Generated by Nica — https://github.com/mendrugo/nica\n");
        sb.append(" */\n");
        sb.append("public class ").append(className).append(" {\n\n");
    }

    private static void emitComment(StringBuilder sb, Instruction insn) {
        sb.append("        // ").append(insn.rawText()).append('\n');
    }

    private static void emitSimdBinop(StringBuilder sb, Instruction insn, String op) {
        emitComment(sb, insn);
        String a = regName(insn.operands().get(0));
        String b = regName(insn.operands().get(1));
        String dst = regName(insn.operands().get(2));
        sb.append("        for (int i = 0; i < 8; i++) ")
          .append(sanitize(dst)).append("[i] = ")
          .append(sanitize(a)).append("[i] ").append(op).append(' ')
          .append(sanitize(b)).append("[i];\n");
    }

    private static void emitSimdShift(StringBuilder sb, Instruction insn, String op) {
        emitComment(sb, insn);
        int shift = (int) ((Operand.Immediate) insn.operands().get(0)).value();
        String src = regName(insn.operands().get(1));
        String dst = regName(insn.operands().get(2));
        sb.append("        for (int i = 0; i < 8; i++) ")
          .append(sanitize(dst)).append("[i] = ")
          .append(sanitize(src)).append("[i] ").append(op).append(' ').append(shift).append(";\n");
    }

    private static void emitVecBinop(StringBuilder sb, Instruction insn, String op) {
        emitComment(sb, insn);
        var dst = (Operand.Register) insn.operands().get(0);
        var a = (Operand.Register) insn.operands().get(1);
        var b = (Operand.Register) insn.operands().get(2);
        int lanes = parseLanes(dst.arrangement());
        sb.append("        for (int i = 0; i < ").append(lanes).append("; i++) ")
          .append(sanitize(dst.name())).append("[i] = ")
          .append(sanitize(a.name())).append("[i] ").append(op).append(' ')
          .append(sanitize(b.name())).append("[i];\n");
    }

    private static void emitVecShift(StringBuilder sb, Instruction insn, String op) {
        emitComment(sb, insn);
        var dst = (Operand.Register) insn.operands().get(0);
        var src = (Operand.Register) insn.operands().get(1);
        int shift = (int) ((Operand.Immediate) insn.operands().get(2)).value();
        int lanes = parseLanes(dst.arrangement());
        sb.append("        for (int i = 0; i < ").append(lanes).append("; i++) ")
          .append(sanitize(dst.name())).append("[i] = ")
          .append(sanitize(src.name())).append("[i] ").append(op).append(' ').append(shift).append(";\n");
    }

    private static void emitMainMethod(StringBuilder sb, String className) {
        sb.append('\n');
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        // Example: run with sample input data\n");
        sb.append("        int[] a = {1, 255, 127, 128, 0, 2, 254, 64}; // unsigned bytes\n");
        sb.append("        int[] b = {10, 20, 30, 40, 50, 60, 70, 80};\n");
        sb.append("        int[] result = run(a, b);\n");
        sb.append("        System.out.print(\"Result: [\");\n");
        sb.append("        for (int i = 0; i < result.length; i++) {\n");
        sb.append("            if (i > 0) System.out.print(\", \");\n");
        sb.append("            System.out.print(result[i]);\n");
        sb.append("        }\n");
        sb.append("        System.out.println(\"]\");\n");
        sb.append("    }\n");
    }

    private static String regName(Operand op) {
        return ((Operand.Register) op).name();
    }

    /** Sanitize register names to valid Java identifiers. */
    private static String sanitize(String regName) {
        // Registers like "r9" or "xmm0" are valid Java identifiers already
        return regName;
    }

    private static List<String> collectSimdRegisters(AssemblySnippet snippet) {
        return snippet.instructions().stream()
            .flatMap(i -> i.operands().stream())
            .filter(op -> op instanceof Operand.Register r
                && (r.name().startsWith("xmm") || r.name().startsWith("ymm")))
            .map(op -> ((Operand.Register) op).name())
            .distinct()
            .sorted()
            .toList();
    }

    private static List<String> collectVectorRegisters(AssemblySnippet snippet) {
        return snippet.instructions().stream()
            .flatMap(i -> i.operands().stream())
            .filter(op -> op instanceof Operand.Register r
                && (r.name().startsWith("v") || r.name().startsWith("s")))
            .map(op -> ((Operand.Register) op).name())
            .distinct()
            .sorted()
            .toList();
    }

    private static String findLastXorDest(AssemblySnippet snippet) {
        return snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("vpxor"))
            .reduce((a, b) -> b) // last one
            .map(i -> regName(i.operands().get(2)))
            .orElse("ymm0");
    }

    private static String findLastXorDestAArch64(AssemblySnippet snippet) {
        return snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("eor3"))
            .reduce((a, b) -> b)
            .map(i -> ((Operand.Register) i.operands().get(0)).name())
            .orElse("v0");
    }

    private static int parseLanes(String arrangement) {
        var sb = new StringBuilder();
        for (char c : arrangement.toCharArray()) {
            if (Character.isDigit(c)) sb.append(c);
            else break;
        }
        return Integer.parseInt(sb.toString());
    }
}

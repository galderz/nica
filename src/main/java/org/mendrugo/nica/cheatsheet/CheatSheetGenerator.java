package org.mendrugo.nica.cheatsheet;

import org.mendrugo.nica.asm.AssemblySnippet;
import org.mendrugo.nica.asm.Instruction;

/**
 * Generates a cheat sheet: the original assembly annotated with
 * human-readable comments explaining each instruction.
 *
 * <p>Preserves original formatting, addresses, and existing JIT comments.
 * Appends {@code //} comments with the instruction description and
 * Java-equivalent pseudocode.</p>
 */
public final class CheatSheetGenerator {

    private CheatSheetGenerator() {}

    /**
     * Generate annotated assembly text from a parsed snippet.
     *
     * @param snippet the parsed assembly snippet
     * @return the annotated assembly text
     */
    public static String generate(AssemblySnippet snippet) {
        var sb = new StringBuilder();

        // Emit block info or method signature as a header comment
        if (snippet.blockInfo() != null) {
            sb.append("// Block: ").append(snippet.blockInfo()).append('\n');
        }
        if (snippet.methodSignature() != null) {
            sb.append("// Method: ").append(snippet.methodSignature()).append('\n');
        }

        // Find the maximum raw text length for alignment
        int maxLen = snippet.instructions().stream()
            .mapToInt(i -> stripExistingComment(i.rawText()).length())
            .max().orElse(0);
        // Cap alignment to avoid excessively wide lines
        int alignCol = Math.min(maxLen + 2, 80);

        for (var insn : snippet.instructions()) {
            appendAnnotatedInstruction(sb, insn, snippet, alignCol);
        }

        return sb.toString();
    }

    private static void appendAnnotatedInstruction(
            StringBuilder sb, Instruction insn, AssemblySnippet snippet, int alignCol) {
        String rawClean = stripExistingComment(insn.rawText());
        var desc = InstructionDescriptions.lookup(insn.mnemonic(), snippet.architecture());

        // Write the instruction text
        sb.append(rawClean);

        // Pad to alignment column
        int padding = Math.max(2, alignCol - rawClean.length());
        sb.append(" ".repeat(padding));

        // Append description comment
        if (desc.isUnsupported()) {
            sb.append("// UNSUPPORTED: ").append(insn.mnemonic());
        } else {
            sb.append("// ").append(desc.summary());
            sb.append("  →  ").append(desc.javaEquivalent());
        }

        // Append perf percentage if present
        if (insn.hasPerfData()) {
            sb.append("  [").append(formatPercent(insn.perfPercent())).append(']');
        }

        // Append source annotation if present
        if (insn.annotation() != null) {
            var ann = insn.annotation();
            sb.append('\n');
            sb.append(" ".repeat(alignCol));
            sb.append("//   ← ").append(ann.className()).append("::").append(ann.methodName());
            if (ann.lineNumber() >= 0) {
                sb.append(" (line ").append(ann.lineNumber()).append(')');
            }
        }

        sb.append('\n');
    }

    /**
     * Strip existing inline comments from raw text.
     * Handles both {@code ;} (HotSpot) and trailing comments.
     */
    private static String stripExistingComment(String text) {
        int idx = text.indexOf(';');
        if (idx >= 0) {
            text = text.substring(0, idx);
        }
        return text.stripTrailing();
    }

    private static String formatPercent(double pct) {
        return isWholeNumber(pct)
            ? (int) pct + "%"
            : String.format("%.2f%%", pct);
    }

    private static boolean isWholeNumber(double number) {
        // if the modulus(remainder of the division) of the argument(number) with 1 is 0 then return true otherwise false.
        return number % 1 == 0;
    }
}

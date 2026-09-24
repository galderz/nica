package org.mendrugo.nica.parser;

import org.mendrugo.nica.asm.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses {@code perf annotate} output (x86_64, AT&T syntax) into an {@link AssemblySnippet}.
 *
 * <p>Handles the perf-specific format: percentage column, bar separator,
 * branch arrows (→ ↓ ↑ ←), label definitions, and method signature headers.</p>
 */
public final class PerfAnnotateParser {

    // Instruction line after the │ separator:
    // [percent] │ [arrows] [label:] mnemonic operands...
    private static final Pattern PERF_LINE = Pattern.compile(
        "^\\s*([\\d.]+)?\\s*│\\s*([→↓↑←])?\\s*(?:(\\w+):\\s*([→↓↑←])?\\s*)?(.+)$"
    );

    // Branches to symbolic targets: "jbe void com.oracle.svm..."
    // The mnemonic is followed by a qualified method signature
    private static final Pattern BRANCH_TO_SYMBOL = Pattern.compile(
        "^(\\S+)\\s+((?:void|int|long|byte|short|char|float|double|boolean|[a-zA-Z][\\w$.]*\\*?)\\s+[a-zA-Z][\\w$.]*::.*)$"
    );

    private PerfAnnotateParser() {}

    /**
     * Parse {@code perf annotate} output into an {@link AssemblySnippet}.
     *
     * @param text the raw perf annotate text
     * @return the parsed snippet
     */
    public static AssemblySnippet parse(String text) {
        var lines = text.lines().toList();
        var instructions = new ArrayList<Instruction>();
        String methodSignature = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            // First non-empty line without │ is the method signature header
            if (methodSignature == null && !line.isBlank() && !line.contains("│")) {
                methodSignature = line.strip();
                continue;
            }

            var m = PERF_LINE.matcher(line);
            if (!m.matches()) continue;

            String percentStr = m.group(1);
            // group(2) = arrow before label/mnemonic
            String label = m.group(3);
            // group(4) = arrow after label
            String rest = m.group(5).strip();

            if (rest.isEmpty()) continue;

            double perfPercent = percentStr != null ? Double.parseDouble(percentStr) : Double.NaN;

            // Parse mnemonic and operands from rest
            String mnemonic;
            String operandsText;

            // Check if this is a branch to a symbolic target
            var branchMatch = BRANCH_TO_SYMBOL.matcher(rest);
            if (branchMatch.matches()) {
                mnemonic = branchMatch.group(1);
                String symbol = branchMatch.group(2).strip();
                var operands = List.<Operand>of(new Operand.Address(symbol));
                instructions.add(new Instruction(-1, mnemonic, operands, line.strip(), null, perfPercent));
                continue;
            }

            // Split into mnemonic + operands
            int spaceIdx = rest.indexOf(' ');
            if (spaceIdx < 0) {
                mnemonic = rest;
                operandsText = "";
            } else {
                mnemonic = rest.substring(0, spaceIdx);
                operandsText = rest.substring(spaceIdx + 1).strip();
            }

            // For branches to labels (je 43, jb 48), parse as Address with label
            List<Operand> operands;
            if (isBranch(mnemonic) && !operandsText.isEmpty()
                && !operandsText.startsWith("%") && !operandsText.startsWith("$")
                && !operandsText.startsWith("0x") && !operandsText.contains("(")) {
                operands = List.of(new Operand.Address(operandsText.strip()));
            } else {
                operands = HotSpotDebugParser.parseX86Operands(operandsText);
            }

            instructions.add(new Instruction(-1, mnemonic, operands, line.strip(), null, perfPercent));
        }

        return new AssemblySnippet(Architecture.X86_64, instructions, null, methodSignature);
    }

    private static boolean isBranch(String mnemonic) {
        return mnemonic.startsWith("j") || mnemonic.equals("callq") || mnemonic.equals("call");
    }
}

package org.mendrugo.nica.parser;

import org.mendrugo.nica.asm.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses HotSpot fast-debug disassembly output into an {@link AssemblySnippet}.
 *
 * <p>Handles both x86_64 (AT&T syntax) and aarch64 instruction formats.
 * Extracts block headers, source annotations, and instruction operands.</p>
 */
public final class HotSpotDebugParser {

    // Block header: ;; B22: #    out( B21 B23 ) <- in( B25 B21 ) ...
    private static final Pattern BLOCK_HEADER = Pattern.compile(
        "^\\s*;; (B\\d+:.*)$"
    );

    // Instruction line: 0x00007fdf4504b0a8:   vmovq   %xmm0, %rsi
    private static final Pattern INSTRUCTION_LINE = Pattern.compile(
        "^\\s*0x([0-9a-fA-F]+):\\s+(\\S+)\\s*(.*?)\\s*$"
    );

    // Source annotation: ; - TestXorByte::testByte@9 (line 20)
    private static final Pattern SOURCE_ANNOTATION = Pattern.compile(
        ";\\s*-\\s*(\\w[\\w$.]*)::(\\w+)@(\\d+)\\s*\\(line\\s+(\\d+)\\)"
    );

    // Continuation line (annotation on next line after instruction)
    private static final Pattern CONTINUATION = Pattern.compile(
        "^\\s*;.*$"
    );

    private HotSpotDebugParser() {}

    /**
     * Parse HotSpot fast-debug disassembly text into an {@link AssemblySnippet}.
     *
     * @param text the raw disassembly text
     * @param architecture the target architecture
     * @return the parsed snippet
     */
    public static AssemblySnippet parse(String text, Architecture architecture) {
        var lines = text.lines().toList();
        var instructions = new ArrayList<Instruction>();
        String blockInfo = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            // Try block header
            var blockMatch = BLOCK_HEADER.matcher(line);
            if (blockMatch.matches()) {
                blockInfo = blockMatch.group(1);
                continue;
            }

            // Try instruction line
            var insnMatch = INSTRUCTION_LINE.matcher(line);
            if (insnMatch.matches()) {
                long address = Long.parseUnsignedLong(insnMatch.group(1), 16);
                String mnemonic = insnMatch.group(2);
                String operandsText = insnMatch.group(3);

                // Check for inline source annotation (on same line after ;)
                SourceAnnotation annotation = extractAnnotation(operandsText);

                // Strip comments from operands text
                operandsText = stripComments(operandsText);

                // Check next lines for continuation annotations
                if (annotation == null) {
                    annotation = lookAheadForAnnotation(lines, i + 1);
                }

                List<Operand> operands = switch (architecture) {
                    case X86_64 -> parseX86Operands(operandsText);
                    case AARCH64 -> parseAArch64Operands(operandsText);
                };

                instructions.add(new Instruction(address, mnemonic, operands, line.strip(), annotation));
                continue;
            }

            // Skip comment-only and blank lines
        }

        return new AssemblySnippet(architecture, instructions, blockInfo, null);
    }

    private static SourceAnnotation extractAnnotation(String text) {
        var m = SOURCE_ANNOTATION.matcher(text);
        if (m.find()) {
            return new SourceAnnotation(
                m.group(1),
                m.group(2),
                Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4))
            );
        }
        return null;
    }

    private static SourceAnnotation lookAheadForAnnotation(List<String> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!CONTINUATION.matcher(line).matches()) break;
            var annotation = extractAnnotation(line);
            if (annotation != null) return annotation;
        }
        return null;
    }

    private static String stripComments(String text) {
        // Remove everything from first ; onwards (but not inside operands)
        int idx = text.indexOf(';');
        if (idx >= 0) {
            text = text.substring(0, idx);
        }
        return text.strip();
    }

    // --- x86_64 AT&T operand parsing ---

    // Memory reference: disp(%base, %index, scale) or disp(%base, %index) or disp(%base) or (%base, %index, scale)
    private static final Pattern X86_MEMORY = Pattern.compile(
        "(-?0x[0-9a-fA-F]+|\\d+)?\\(%([\\w]+)(?:,\\s*%([\\w]+)(?:,\\s*(\\d+))?)?\\)"
    );

    static List<Operand> parseX86Operands(String text) {
        if (text.isEmpty()) return List.of();

        var operands = new ArrayList<Operand>();
        for (String part : splitOperands(text)) {
            part = part.strip();
            if (part.isEmpty()) continue;
            operands.add(parseX86Operand(part));
        }
        return List.copyOf(operands);
    }

    private static Operand parseX86Operand(String text) {
        // Immediate: $0x18 or $0x0
        if (text.startsWith("$")) {
            return new Operand.Immediate(parseLong(text.substring(1)));
        }

        // Memory reference: disp(%base, %index, scale)
        var memMatch = X86_MEMORY.matcher(text);
        if (memMatch.matches()) {
            long disp = memMatch.group(1) != null ? parseLong(memMatch.group(1)) : 0;
            String base = memMatch.group(2);
            String index = memMatch.group(3);
            int scale = memMatch.group(4) != null ? Integer.parseInt(memMatch.group(4)) : 1;
            return new Operand.Memory(base, index, scale, disp);
        }

        // Register: %rax, %xmm0, %ymm13
        if (text.startsWith("%")) {
            return new Operand.Register(text.substring(1));
        }

        // Bare address: 0x7fdf4504b0a0
        if (text.startsWith("0x") || text.matches("\\d+")) {
            return new Operand.Address(parseLong(text));
        }

        // Label or symbol
        return new Operand.Address(text);
    }

    // --- aarch64 operand parsing ---

    // Memory reference: [base, #disp] or [base, index] or [base]
    private static final Pattern AARCH64_MEMORY = Pattern.compile(
        "\\[(\\w+)(?:,\\s*#?(0x[0-9a-fA-F]+|\\d+|\\w+))?]"
    );

    // Vector register: v26.8h
    private static final Pattern AARCH64_VECTOR_REG = Pattern.compile(
        "(v\\d+)\\.(\\d+[bhsdBHSD])"
    );

    static List<Operand> parseAArch64Operands(String text) {
        if (text.isEmpty()) return List.of();

        var operands = new ArrayList<Operand>();
        for (String part : splitOperands(text)) {
            part = part.strip();
            if (part.isEmpty()) continue;
            operands.add(parseAArch64Operand(part));
        }
        return List.copyOf(operands);
    }

    private static Operand parseAArch64Operand(String text) {
        // Immediate: #0x10 or #0
        if (text.startsWith("#")) {
            return new Operand.Immediate(parseLong(text.substring(1)));
        }

        // Memory reference: [base, #disp]
        var memMatch = AARCH64_MEMORY.matcher(text);
        if (memMatch.matches()) {
            String base = memMatch.group(1);
            long disp = 0;
            String index = null;
            if (memMatch.group(2) != null) {
                String offsetStr = memMatch.group(2);
                // Could be a register (index) or an immediate (displacement)
                if (offsetStr.matches("0x[0-9a-fA-F]+|\\d+")) {
                    disp = parseLong(offsetStr);
                } else {
                    index = offsetStr;
                }
            }
            return new Operand.Memory(base, index, 1, disp);
        }

        // Vector register with arrangement: v26.8h
        var vecMatch = AARCH64_VECTOR_REG.matcher(text);
        if (vecMatch.matches()) {
            return new Operand.Register(vecMatch.group(1), vecMatch.group(2));
        }

        // Address: #0x111e01070
        if (text.startsWith("0x") || text.matches("\\d+")) {
            return new Operand.Address(parseLong(text));
        }

        // Plain register: x12, w17, s21
        if (text.matches("[xwsdbhqXWSDBHQ]\\d+|sp|lr|xzr|wzr")) {
            return new Operand.Register(text);
        }

        // Fallback: treat as label/symbol
        return new Operand.Address(text);
    }

    // --- Utilities ---

    /**
     * Split a comma-separated operand string, respecting parentheses and brackets.
     */
    static List<String> splitOperands(String text) {
        var parts = new ArrayList<String>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            parts.add(text.substring(start));
        }
        return parts;
    }

    private static long parseLong(String text) {
        text = text.strip();
        if (text.startsWith("-0x") || text.startsWith("-0X")) {
            return -Long.parseUnsignedLong(text.substring(3), 16);
        }
        if (text.startsWith("0x") || text.startsWith("0X")) {
            return Long.parseUnsignedLong(text.substring(2), 16);
        }
        return Long.parseLong(text);
    }
}

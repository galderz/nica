package org.mendrugo.nica.recipe;

import org.mendrugo.nica.asm.*;

import java.util.*;

/**
 * Recognizes the GraalVM native image bounds-check pattern in assembly:
 *
 * <pre>
 *   testl %eax,%eax          ; null check
 *   je    throwNPE            ; branch to NPE on null
 *   movl  length,%edi         ; load array length
 *   cmpl  $bound,%edi         ; compare against bound
 *   jb    throwOOB            ; branch to OOB on failure
 *   movq  data,%rax           ; load the actual data (happy path)
 * </pre>
 *
 * <p>This recipe operates on the assembly instruction level (not IR nodes)
 * because the GraalVM snippet's structure is more naturally recognized
 * as an instruction sequence pattern than as an IR graph pattern.</p>
 */
public final class BoundsCheckRecipe {

    private BoundsCheckRecipe() {}

    /**
     * A recognized bounds-check pattern in a GraalVM assembly snippet.
     *
     * @param nullCheckInsn the testl/je pair for null checking
     * @param boundsCheckInsn the cmpl/jb pair for bounds checking
     * @param happyPathInsn the load instruction on the happy path
     * @param explanation human-readable description
     * @param javaCode equivalent Java code
     */
    public record BoundsCheckMatch(
        Instruction nullCheckInsn,
        Instruction boundsCheckInsn,
        Instruction happyPathInsn,
        String explanation,
        String javaCode
    ) {}

    /**
     * Scan an assembly snippet for the GraalVM bounds-check pattern.
     *
     * @param snippet the parsed assembly
     * @return matches found, or empty list
     */
    public static List<BoundsCheckMatch> find(AssemblySnippet snippet) {
        var matches = new ArrayList<BoundsCheckMatch>();
        var insns = snippet.instructions();

        for (int i = 0; i < insns.size(); i++) {
            var match = tryMatch(insns, i);
            if (match != null) {
                matches.add(match);
            }
        }

        return matches;
    }

    private static BoundsCheckMatch tryMatch(List<Instruction> insns, int start) {
        // Look for: testl %reg,%reg followed by je (null check)
        if (start + 1 >= insns.size()) return null;

        var testInsn = insns.get(start);
        if (!testInsn.mnemonic().equals("testl")) return null;
        // testl %reg, %reg — same register (null check idiom)
        if (testInsn.operands().size() != 2) return null;
        if (!(testInsn.operands().get(0) instanceof Operand.Register r1
            && testInsn.operands().get(1) instanceof Operand.Register r2
            && r1.name().equals(r2.name()))) return null;

        // Next should be je (jump if zero = null)
        var jeInsn = insns.get(start + 1);
        if (!jeInsn.mnemonic().equals("je")) return null;

        // Look for cmpl followed by jb within the next few instructions
        Instruction cmplInsn = null;
        Instruction jbInsn = null;
        Instruction loadInsn = null;

        for (int j = start + 2; j < Math.min(start + 6, insns.size()); j++) {
            var insn = insns.get(j);
            if (insn.mnemonic().equals("cmpl") && cmplInsn == null) {
                cmplInsn = insn;
            } else if ((insn.mnemonic().equals("jb") || insn.mnemonic().equals("jbe"))
                       && cmplInsn != null && jbInsn == null) {
                jbInsn = insn;
            } else if ((insn.mnemonic().equals("movq") || insn.mnemonic().equals("movl"))
                       && jbInsn != null && loadInsn == null) {
                loadInsn = insn;
            }
        }

        if (cmplInsn == null || jbInsn == null) return null;

        // Check if je target points to NPE and jb target points to OOB
        boolean jeToNpe = isNpeTarget(jeInsn);
        boolean jbToOob = isOobTarget(jbInsn);

        String bound = extractBound(cmplInsn);

        return new BoundsCheckMatch(
            testInsn,
            cmplInsn,
            loadInsn,
            "Null check + bounds check guard (GraalVM native image pattern). " +
            "Null → NullPointerException" + (jeToNpe ? " ✓" : "") +
            ", out-of-bounds → IndexOutOfBoundsException" + (jbToOob ? " ✓" : ""),
            "// GraalVM null + bounds check\n" +
            "Objects.requireNonNull(array);\n" +
            "Objects.checkIndex(index, " + bound + ");\n" +
            "long result = array[index]; // happy path"
        );
    }

    private static boolean isNpeTarget(Instruction je) {
        if (je.operands().isEmpty()) return false;
        var target = je.operands().getFirst();
        if (target instanceof Operand.Address addr && addr.label() != null) {
            return addr.label().contains("NullPointerException")
                || addr.label().contains("throwNewNull");
        }
        return false;
    }

    private static boolean isOobTarget(Instruction jb) {
        if (jb.operands().isEmpty()) return false;
        var target = jb.operands().getFirst();
        if (target instanceof Operand.Address addr && addr.label() != null) {
            return addr.label().contains("outOfBounds")
                || addr.label().contains("OutOfBounds");
        }
        return false;
    }

    private static String extractBound(Instruction cmpl) {
        for (var op : cmpl.operands()) {
            if (op instanceof Operand.Immediate imm) {
                return String.valueOf(imm.value());
            }
        }
        return "length";
    }
}

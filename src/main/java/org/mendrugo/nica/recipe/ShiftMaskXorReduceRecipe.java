package org.mendrugo.nica.recipe;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.*;

/**
 * Recognizes the shift-mask-xor-reduce pattern:
 *
 * <pre>
 *   xor(sar(shl(value, 24), 24), accumulator)
 * </pre>
 *
 * <p>This pattern truncates a value to a signed byte (via shl+sar) then
 * XORs it into an accumulator. In the assembly, this is the tail of the
 * XorByte computation where per-group results are XOR-combined.</p>
 *
 * <p>Also matches chained XORs: xor(xor(...), ...) representing the
 * accumulation of multiple truncated results.</p>
 */
public final class ShiftMaskXorReduceRecipe implements Recipe {

    @Override
    public String name() { return "Shift-Mask XOR Reduce"; }

    @Override
    public Optional<Match> match(Node root) {
        return switch (root) {
            // Primary pattern: xor(sar(shl(v, c), c), other) — truncate-and-xor
            case XorNode xor
                when isSignExtend(xor.in(1)) || isSignExtend(xor.in(2))
                -> {
                    int bits = extractBits(xor);
                    var matched = collectXorChain(xor);
                    int depth = countChainDepth(xor);
                    yield Optional.of(new Match(this, root, matched,
                        generateCode(bits, depth),
                        "XOR-reduce: truncate to signed " + bits + "-bit and XOR into accumulator" +
                        (depth > 1 ? " (" + depth + " values chained)" : "")));
                }

            default -> Optional.empty();
        };
    }

    private static boolean isSignExtend(Node node) {
        return MultiplyAccumulateRecipe.isSignExtend(node);
    }

    /** Extract the bit width from the sign-extension shift amount. */
    private static int extractBits(XorNode xor) {
        Node se = isSignExtend(xor.in(1)) ? xor.in(1) : xor.in(2);
        if (se instanceof SarNode sar && sar.in(2) instanceof ConstantNode c
            && c._type instanceof TypeInteger ti && ti.isConstant()) {
            return 64 - (int) ti.value();
        }
        return 8; // default to byte
    }

    /** Count the depth of chained XOR nodes. */
    private static int countChainDepth(XorNode xor) {
        int depth = 1;
        for (int i = 1; i <= 2; i++) {
            if (xor.in(i) instanceof XorNode inner) {
                depth += countChainDepth(inner);
            }
        }
        return depth;
    }

    /** Collect all nodes in the XOR chain, including sign-extend subgraphs. */
    private static Set<Node> collectXorChain(XorNode root) {
        var nodes = new LinkedHashSet<Node>();
        collectXorRecursive(root, nodes);
        return nodes;
    }

    private static void collectXorRecursive(Node node, Set<Node> collected) {
        if (node == null) return;
        for (var n : collected) if (n == node) return;
        if (node instanceof ParamNode || node instanceof ConstantNode) return;

        collected.add(node);

        // Follow XOR chains and sign-extend subgraphs
        if (node instanceof XorNode) {
            collectXorRecursive(node.in(1), collected);
            collectXorRecursive(node.in(2), collected);
        } else if (node instanceof SarNode || node instanceof ShlNode) {
            collectXorRecursive(node.in(1), collected);
        }
    }

    private static String generateCode(int bits, int depth) {
        String type = switch (bits) {
            case 8 -> "byte";
            case 16 -> "short";
            default -> bits + "-bit";
        };
        if (depth > 1) {
            return "// XOR-reduce: combine " + depth + " truncated values\n" +
                   "// For each SIMD lane:\n" +
                   "//   accumulator ^= (" + type + ") value1;\n" +
                   "//   accumulator ^= (" + type + ") value2;\n" +
                   "//   ... (" + depth + " values total)\n" +
                   "result[i] = (" + type + ") val1 ^ (" + type + ") val2 ^ ... ^ (" + type + ") valN;";
        }
        return "// Truncate to signed " + type + " and XOR into accumulator\n" +
               "result[i] ^= (" + type + ") value;";
    }
}

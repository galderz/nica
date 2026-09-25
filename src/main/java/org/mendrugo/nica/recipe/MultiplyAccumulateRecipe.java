package org.mendrugo.nica.recipe;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.*;

/**
 * Recognizes the vectorized sign-extend + multiply-accumulate pattern:
 *
 * <pre>
 *   sign_extend(a) * sign_extend(b) + sign_extend(a) * sign_extend(c)
 * </pre>
 *
 * <p>In the IR this appears as: SarNode(ShlNode(param, shift), shift) feeding
 * into MulNode, then AddNode accumulating multiple products. This is the core
 * computation of the HotSpot XorByte example.</p>
 *
 * <p>Matches on AddNode that accumulates two MulNode results where at least
 * one MulNode input is a sign-extended value (SarNode(ShlNode(...))).</p>
 */
public final class MultiplyAccumulateRecipe implements Recipe {

    @Override
    public String name() { return "Vectorized Multiply-Accumulate"; }

    @Override
    public Optional<Match> match(Node root) {
        return switch (root) {
            // Pattern: add(mul(sign_ext(a), sign_ext(b)), mul(...))
            case AddNode add
                when add.in(1) instanceof MulNode mul1
                  && add.in(2) instanceof MulNode mul2
                  && hasSignExtendInput(mul1)
                  && hasSignExtendInput(mul2)
                -> {
                    var matched = collectNodes(add);
                    yield Optional.of(new Match(this, root, matched,
                        generateCode(add),
                        "Multiply-accumulate of sign-extended byte values: " +
                        "result = a[i] * b[i] + c[i] * d[i]"));
                }

            // Pattern: add(mul(sign_ext(a), sign_ext(b)), other)
            // where at least one side is a multiply of sign-extended values
            case AddNode add
                when (add.in(1) instanceof MulNode mul && hasSignExtendInput(mul))
                  || (add.in(2) instanceof MulNode mul2 && hasSignExtendInput(mul2))
                -> {
                    var matched = collectNodes(add);
                    yield Optional.of(new Match(this, root, matched,
                        generateCode(add),
                        "Accumulate product of sign-extended byte values"));
                }

            default -> Optional.empty();
        };
    }

    /** Check if a MulNode has at least one sign-extended input (SarNode(ShlNode(...))). */
    private static boolean hasSignExtendInput(MulNode mul) {
        return isSignExtend(mul.in(1)) || isSignExtend(mul.in(2));
    }

    /** Check if a node is a sign-extension: SarNode(ShlNode(x, c), c) with same shift. */
    static boolean isSignExtend(Node node) {
        return switch (node) {
            case SarNode sar
                when sar.in(1) instanceof ShlNode shl
                  && sar.in(2) instanceof ConstantNode c1
                  && shl.in(2) instanceof ConstantNode c2
                  && c1._type instanceof TypeInteger t1 && t1.isConstant()
                  && c2._type instanceof TypeInteger t2 && t2.isConstant()
                  && t1.value() == t2.value()
                -> true;
            default -> false;
        };
    }

    /** Collect all nodes in the matched subgraph. */
    private static Set<Node> collectNodes(AddNode root) {
        var nodes = new LinkedHashSet<Node>();
        collectRecursive(root, nodes);
        return nodes;
    }

    private static void collectRecursive(Node node, Set<Node> collected) {
        if (node == null) return;
        // Use identity check for already-visited (Node.equals is structural)
        for (var n : collected) if (n == node) return;
        if (node instanceof ParamNode || node instanceof ConstantNode) return;
        collected.add(node);
        for (int i = 0; i < node.nIns(); i++) {
            collectRecursive(node.in(i), collected);
        }
    }

    /** Generate high-level Java code for the matched pattern. */
    private static String generateCode(AddNode root) {
        return "// Vectorized multiply-accumulate of sign-extended bytes\n" +
               "// For each SIMD lane:\n" +
               "//   result[i] = (byte)a[i] * (byte)b[i] + (byte)c[i] * (byte)d[i]\n" +
               "result[i] = signExtByte(a[i]) * signExtByte(b[i])\n" +
               "          + signExtByte(c[i]) * signExtByte(d[i]);";
    }
}

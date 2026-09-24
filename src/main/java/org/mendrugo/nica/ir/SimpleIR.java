package org.mendrugo.nica.ir;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StartNode;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

/**
 * Minimal bootstrap for using Simple's IR nodes without a full parser/compiler.
 *
 * <p>Simple's nodes require {@link Parser#START} to be initialized (used as
 * a parent for {@link ConstantNode}). This class provides a one-time
 * initialization and factory methods for creating nodes.</p>
 *
 * <p>Uses chapter 14 of Simple, which provides all the node types Nica needs
 * (Add, Mul, Xor, Shl, Sar, Load, If, Loop, Bool, Constant) without the
 * heavy CodeGen machinery of later chapters.</p>
 */
public final class SimpleIR {

    private static boolean initialized = false;

    private SimpleIR() {}

    /**
     * Initialize Simple's static state for manual node construction.
     * Safe to call multiple times; only the first call has effect.
     */
    public static synchronized void bootstrap() {
        if (initialized) return;
        Node.reset();
        Node._disablePeephole = true; // Prevent constant folding from rewriting our graph
        Parser.START = new StartNode(new Type[]{ Type.CONTROL, TypeInteger.BOT });
        Parser.START._type = Parser.START.compute();
        initialized = true;
    }

    /** Create a constant integer node. */
    public static ConstantNode constant(long value) {
        bootstrap();
        var c = new ConstantNode(TypeInteger.constant(value));
        c._type = c.compute();
        return c;
    }

    /** Create a node and compute its type. */
    public static <N extends Node> N typed(N node) {
        node._type = node.compute();
        return node;
    }

    /**
     * Reset Simple's state. Useful between tests to avoid stale node IDs.
     */
    public static synchronized void reset() {
        initialized = false;
        Node.reset();
    }
}

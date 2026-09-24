package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.BitSet;

/**
 * A parameter node representing a value loaded from memory (e.g., a byte
 * from a byte array). Unlike Simple's ConstantNode, the value can be
 * bound after construction for evaluation.
 *
 * <p>Before binding, the type is {@code TypeInteger.BOT} (unknown integer).
 * After binding via {@link #bind(long)}, the type becomes a constant.</p>
 */
public class ParamNode extends Node {

    private final String _name;
    private long _value;
    private boolean _bound;

    public ParamNode(String name) {
        super(Parser.START); // ConstantNode convention: START as input
        _name = name;
        _bound = false;
        _type = TypeInteger.BOT;
    }

    /** Bind this parameter to a concrete value. */
    public void bind(long value) {
        _value = value;
        _bound = true;
        _type = TypeInteger.constant(value);
    }

    /** Unbind this parameter (reset to unknown). */
    public void unbind() {
        _bound = false;
        _type = TypeInteger.BOT;
    }

    public boolean isBound() { return _bound; }
    public long value() { return _value; }
    public String paramName() { return _name; }

    @Override public String label() { return _bound ? "#" + _value : "?" + _name; }
    @Override public String uniqueName() { return "Param_" + _name + "_" + _nid; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append(_bound ? String.valueOf(_value) : "?" + _name);
    }

    @Override public Type compute() {
        return _bound ? TypeInteger.constant(_value) : TypeInteger.BOT;
    }

    @Override public Node idealize() { return null; }
}

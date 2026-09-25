package org.mendrugo.nica.ir;

import com.seaofnodes.simple.IRPrinter;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.*;

/**
 * A graph of Simple IR {@link Node}s representing the data-flow of an assembly snippet.
 *
 * <p>The graph stores nodes per-lane: SIMD operations with N lanes produce
 * N parallel data-flow graphs (one per lane). This models SIMD as N scalar
 * operations, which is the MVP strategy from the design doc.</p>
 *
 * <p>Uses Simple chapter 14 nodes directly, giving access to Simple's full
 * debugging toolkit: IRPrinter, deep print, node walking, type computation.</p>
 */
public final class NicaGraph {

    /** Result nodes per lane (the final output of each SIMD lane). */
    private final List<Node> laneResults = new ArrayList<>();

    /** Named parameter nodes that must be bound before evaluation. */
    private final Map<String, List<ParamNode>> params = new LinkedHashMap<>();

    /** All nodes in this graph, for iteration. */
    private final List<Node> allNodes = new ArrayList<>();

    /** Track a node in the graph. */
    public <N extends Node> N add(N node) {
        allNodes.add(node);
        return node;
    }

    /** Set the result node for a specific lane. */
    public void setLaneResult(int lane, Node node) {
        while (laneResults.size() <= lane) laneResults.add(null);
        laneResults.set(lane, node);
    }

    /** Get the result node for a specific lane. */
    public Node getLaneResult(int lane) { return laneResults.get(lane); }

    /** Number of SIMD lanes in this graph. */
    public int laneCount() { return laneResults.size(); }

    /**
     * Register a named parameter.
     */
    public ParamNode addParam(String name, int lane) {
        var param = add(new ParamNode(name + "[" + lane + "]"));
        params.computeIfAbsent(name, _ -> new ArrayList<>()).add(param);
        return param;
    }

    /** Get all registered param nodes for a given name. */
    public List<ParamNode> getParams(String name) {
        return params.getOrDefault(name, List.of());
    }

    /** All parameter names. */
    public Set<String> paramNames() { return params.keySet(); }

    /** All nodes in the graph. */
    public List<Node> allNodes() { return Collections.unmodifiableList(allNodes); }

    /**
     * Bind parameter values and evaluate the graph for a single lane.
     *
     * @param lane the lane index
     * @param paramValues map from param ParamNode to its concrete value
     * @return the result value for this lane
     */
    public int evaluate(int lane, Map<? extends ParamNode, Integer> paramValues) {
        // Bind parameter values
        for (var entry : paramValues.entrySet()) {
            entry.getKey().bind(entry.getValue());
        }
        // Recompute types through the graph (bottom-up)
        recomputeTypes();
        // Read the result
        Node result = getLaneResult(lane);
        if (result._type instanceof TypeInteger ti && ti.isConstant()) {
            // Unbind params for reuse
            paramValues.keySet().forEach(ParamNode::unbind);
            recomputeTypes();
            return (int) ti.value();
        }
        paramValues.keySet().forEach(ParamNode::unbind);
        recomputeTypes();
        throw new IllegalStateException(
            "Result node " + result._nid + " did not evaluate to a constant: " + result._type);
    }

    /** Recompute types for all nodes in topological order. */
    private void recomputeTypes() {
        // Multiple passes to handle any ordering issues.
        // Simple's type lattice is finite so this always converges.
        for (int pass = 0; pass < 3; pass++) {
            boolean changed = false;
            for (var node : allNodes) {
                var old = node._type;
                node._type = node.compute();
                if (node._type != old) changed = true;
            }
            if (!changed) break;
        }
    }

    /**
     * Print the graph using Simple's IRPrinter (columnar format).
     */
    public String prettyPrint() {
        var sb = new StringBuilder();
        for (var node : allNodes) {
            IRPrinter._printLine(node, sb);
        }
        sb.append("--- Lane results ---\n");
        for (int i = 0; i < laneResults.size(); i++) {
            var r = laneResults.get(i);
            if (r != null) {
                sb.append("  lane[").append(i).append("] = ");
                sb.append(r.print()).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Print the graph using Simple's LLVM-style format.
     */
    public String prettyPrintLlvm() {
        var sb = new StringBuilder();
        for (var node : allNodes) {
            IRPrinter._printLineLlvmFormat(node, sb);
        }
        return sb.toString();
    }

    /**
     * Print a single lane's result as a deep expression string.
     * Uses Simple's recursive print (e.g. "((a+b)^c)").
     */
    public String printLaneExpression(int lane) {
        return getLaneResult(lane).print();
    }
}

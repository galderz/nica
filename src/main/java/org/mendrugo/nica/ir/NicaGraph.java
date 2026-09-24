package org.mendrugo.nica.ir;

import java.util.*;

/**
 * A graph of {@link NicaNode}s representing the data-flow of an assembly snippet.
 *
 * <p>The graph stores nodes per-lane: SIMD operations with N lanes produce
 * N parallel data-flow graphs (one per lane). This models SIMD as N scalar
 * operations, which is the MVP strategy from the design doc.</p>
 */
public final class NicaGraph {

    private int nextId = 1;
    private final List<NicaNode> allNodes = new ArrayList<>();

    /** Result nodes per lane (the final output of each SIMD lane). */
    private final List<NicaNode> laneResults = new ArrayList<>();

    /** Named parameters that must be bound before evaluation. */
    private final Map<String, List<NicaNode.Param>> params = new LinkedHashMap<>();

    /** Allocate a unique node ID. */
    public int nextId() { return nextId++; }

    /** Register a node in the graph. */
    public <T extends NicaNode> T add(T node) {
        allNodes.add(node);
        return node;
    }

    /** Set the result node for a specific lane. */
    public void setLaneResult(int lane, NicaNode node) {
        while (laneResults.size() <= lane) laneResults.add(null);
        laneResults.set(lane, node);
    }

    /** Get the result node for a specific lane. */
    public NicaNode getLaneResult(int lane) { return laneResults.get(lane); }

    /** Number of SIMD lanes in this graph. */
    public int laneCount() { return laneResults.size(); }

    /** Register a named parameter. */
    public NicaNode.Param addParam(String name, int lane) {
        var param = add(new NicaNode.Param(nextId(), name + "[" + lane + "]"));
        params.computeIfAbsent(name, _ -> new ArrayList<>()).add(param);
        return param;
    }

    /** Get all registered params for a given name. */
    public List<NicaNode.Param> getParams(String name) {
        return params.getOrDefault(name, List.of());
    }

    /** All parameter names. */
    public Set<String> paramNames() { return params.keySet(); }

    /** All nodes in the graph. */
    public List<NicaNode> allNodes() { return Collections.unmodifiableList(allNodes); }

    /**
     * Evaluate the graph for a single lane with the given parameter bindings.
     *
     * @param lane the lane index
     * @param paramValues map from param node to its concrete value
     * @return the result value for this lane
     */
    public int evaluate(int lane, Map<NicaNode, Integer> paramValues) {
        return evaluateNode(getLaneResult(lane), paramValues);
    }

    private int evaluateNode(NicaNode node, Map<NicaNode, Integer> paramValues) {
        if (node instanceof NicaNode.Param) {
            Integer val = paramValues.get(node);
            if (val == null) throw new IllegalStateException("Unbound param: " + ((NicaNode.Param) node).name());
            return val;
        }
        if (node instanceof NicaNode.Constant c) {
            return c.value();
        }
        // Evaluate inputs recursively
        var inputs = node.inputs();
        int[] inputValues = new int[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            inputValues[i] = evaluateNode(inputs.get(i), paramValues);
        }
        return node.evaluate(inputValues);
    }

    /**
     * Print the graph in a Simple-compatible columnar format for debugging.
     */
    public String prettyPrint() {
        var sb = new StringBuilder();
        for (var node : allNodes) {
            sb.append(String.format("%4d %-20s", node.id(), node.label()));
            for (var input : node.inputs()) {
                sb.append(String.format(" %4d", input.id()));
            }
            sb.append('\n');
        }
        sb.append("--- Lane results ---\n");
        for (int i = 0; i < laneResults.size(); i++) {
            var r = laneResults.get(i);
            if (r != null) sb.append(String.format("  lane[%d] = node %d\n", i, r.id()));
        }
        return sb.toString();
    }
}

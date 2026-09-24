package org.mendrugo.nica.recipe;

import com.seaofnodes.simple.node.Node;

import java.util.Set;

/**
 * A successful pattern match from a {@link Recipe}.
 *
 * @param recipe the recipe that matched
 * @param root the root node of the matched subgraph
 * @param matchedNodes all nodes consumed by this match (used to avoid
 *                     overlapping matches and to mark nodes as "explained")
 * @param javaCode the high-level Java code fragment that replaces the
 *                 literal translation of the matched subgraph
 * @param explanation human-readable description of what the pattern does
 */
public record Match(
    Recipe recipe,
    Node root,
    Set<Node> matchedNodes,
    String javaCode,
    String explanation
) {}

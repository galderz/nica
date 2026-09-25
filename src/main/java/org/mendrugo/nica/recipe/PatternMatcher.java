package org.mendrugo.nica.recipe;

import com.seaofnodes.simple.node.Node;
import org.mendrugo.nica.ir.NicaGraph;

import java.util.*;

/**
 * Walks an IR graph and collects pattern matches from registered recipes.
 *
 * <p>Matches are collected bottom-up (leaf nodes first). When multiple
 * recipes match the same nodes, the longest match wins (most nodes consumed).
 * Overlapping matches are resolved by discarding the smaller one.</p>
 */
public final class PatternMatcher {

    private final List<Recipe> recipes;

    public PatternMatcher(List<Recipe> recipes) {
        this.recipes = List.copyOf(recipes);
    }

    public PatternMatcher(Recipe... recipes) {
        this(List.of(recipes));
    }

    /**
     * Find all non-overlapping matches in the graph.
     *
     * @param graph the IR graph to search
     * @return matches ordered by their root node position in the graph,
     *         with overlaps resolved (longest match wins)
     */
    public List<Match> findMatches(NicaGraph graph) {
        var allMatches = new ArrayList<Match>();

        // Try each recipe against each node (bottom-up order since allNodes is topological)
        for (var node : graph.allNodes()) {
            for (var recipe : recipes) {
                recipe.match(node).ifPresent(allMatches::add);
            }
        }

        // Resolve overlaps: longest match wins
        return resolveOverlaps(allMatches);
    }

    /**
     * Find all non-overlapping matches against a flat list of nodes.
     * Useful for testing with manually constructed node lists.
     */
    public List<Match> findMatches(List<Node> nodes) {
        var allMatches = new ArrayList<Match>();
        for (var node : nodes) {
            for (var recipe : recipes) {
                recipe.match(node).ifPresent(allMatches::add);
            }
        }
        return resolveOverlaps(allMatches);
    }

    /**
     * Resolve overlapping matches: when two matches share nodes,
     * keep the one that covers more nodes.
     */
    static List<Match> resolveOverlaps(List<Match> matches) {
        // Sort by match size descending (largest first)
        var sorted = new ArrayList<>(matches);
        sorted.sort(Comparator.comparingInt((Match m) -> m.matchedNodes().size()).reversed());

        var claimed = new IdentityHashSet<Node>();
        var resolved = new ArrayList<Match>();

        for (var match : sorted) {
            // Check if any of this match's nodes are already claimed
            boolean overlaps = false;
            for (var node : match.matchedNodes()) {
                if (claimed.contains(node)) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                resolved.add(match);
                claimed.addAll(match.matchedNodes());
            }
        }

        // Return in original graph order (by root node ID)
        resolved.sort(Comparator.comparingInt(m -> m.root()._nid));
        return resolved;
    }

    /**
     * Check if a node is covered by any of the given matches.
     */
    public static boolean isCovered(Node node, List<Match> matches) {
        for (var match : matches) {
            if (match.matchedNodes().contains(node)) return true;
        }
        return false;
    }

    /**
     * Find the match that covers a given node, if any.
     */
    public static Optional<Match> findCoveringMatch(Node node, List<Match> matches) {
        for (var match : matches) {
            if (match.root() == node) return Optional.of(match);
        }
        return Optional.empty();
    }

    /**
     * Identity-based HashSet for Node (since Node overrides equals/hashCode for GVN).
     */
    static final class IdentityHashSet<T> {
        private final IdentityHashMap<T, Boolean> map = new IdentityHashMap<>();

        void addAll(Set<T> items) { for (var item : items) map.put(item, Boolean.TRUE); }
        boolean contains(T item) { return map.containsKey(item); }
    }
}

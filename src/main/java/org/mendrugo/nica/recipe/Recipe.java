package org.mendrugo.nica.recipe;

import com.seaofnodes.simple.node.Node;

import java.util.Optional;

/**
 * A recipe recognizes a known IR graph pattern and produces a high-level
 * Java code fragment explaining what the pattern does.
 *
 * <p>Recipes are the mechanism for the "Explained" view: when a subgraph
 * matches a known compiler idiom (vectorized multiply-accumulate, shift-mask
 * truncation, bounds check), the recipe replaces the literal register-level
 * code with a readable, intent-revealing alternative.</p>
 *
 * <p>Implementations use Java 27 {@code switch} pattern matching with guards
 * over Simple's Node class hierarchy. No visitor pattern needed.</p>
 */
public interface Recipe {

    /** Human-readable name of this recipe (e.g. "Vectorized Multiply-Accumulate"). */
    String name();

    /**
     * Try to match this recipe against a root node.
     *
     * @param root the node to try matching from (typically walked bottom-up)
     * @return a Match if the pattern is recognized, empty otherwise
     */
    Optional<Match> match(Node root);
}

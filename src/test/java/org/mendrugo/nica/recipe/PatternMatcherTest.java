package org.mendrugo.nica.recipe;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mendrugo.nica.ir.SimpleIR;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PatternMatcherTest {

    @BeforeEach
    void reset() { SimpleIR.reset(); }

    @Test
    void trivialRecipeMatches() {
        SimpleIR.bootstrap();
        var c = SimpleIR.constant(42);
        var nodes = List.<Node>of(c);

        // Recipe that matches any ConstantNode
        Recipe constRecipe = new Recipe() {
            @Override public String name() { return "Constant"; }
            @Override public Optional<Match> match(Node root) {
                return switch (root) {
                    case ConstantNode cn when cn._type instanceof TypeInteger ti && ti.isConstant()
                        -> Optional.of(new Match(this, root, Set.of(root),
                            "int val = " + ti.value() + ";",
                            "Constant value " + ti.value()));
                    default -> Optional.empty();
                };
            }
        };

        var matcher = new PatternMatcher(constRecipe);
        var matches = matcher.findMatches(nodes);

        assertEquals(1, matches.size());
        assertEquals("Constant", matches.getFirst().recipe().name());
        assertTrue(matches.getFirst().javaCode().contains("42"));
    }

    @Test
    void noMatchReturnsEmpty() {
        SimpleIR.bootstrap();
        var c = SimpleIR.constant(10);
        var nodes = List.<Node>of(c);

        // Recipe that never matches
        Recipe neverRecipe = new Recipe() {
            @Override public String name() { return "Never"; }
            @Override public Optional<Match> match(Node root) { return Optional.empty(); }
        };

        var matches = new PatternMatcher(neverRecipe).findMatches(nodes);
        assertTrue(matches.isEmpty());
    }

    @Test
    void signExtendPatternMatches() {
        SimpleIR.bootstrap();
        // Build the shl/sar sign-extend pattern: (x << 56) >> 56
        var param = new ParamNode("x");
        SimpleIR.typed(param);
        var c56 = SimpleIR.constant(56);
        var shl = SimpleIR.typed(new ShlNode(param, c56));
        var sar = SimpleIR.typed(new SarNode(shl, c56));

        // Recipe that matches SarNode(ShlNode(x, c), c) with same shift
        Recipe signExtendRecipe = new Recipe() {
            @Override public String name() { return "SignExtend"; }
            @Override public Optional<Match> match(Node root) {
                return switch (root) {
                    case SarNode sarNode
                        when sarNode.in(1) instanceof ShlNode shlNode
                          && sarNode.in(2) instanceof ConstantNode c1
                          && shlNode.in(2) instanceof ConstantNode c2
                          && c1._type instanceof TypeInteger t1 && t1.isConstant()
                          && c2._type instanceof TypeInteger t2 && t2.isConstant()
                          && t1.value() == t2.value()
                        -> {
                            int bits = 64 - (int) t1.value();
                            var matched = Set.<Node>of(sarNode, shlNode, c1);
                            yield Optional.of(new Match(this, root, matched,
                                "int val = (byte) x;",
                                "Sign-extend from " + bits + " bits"));
                        }
                    default -> Optional.empty();
                };
            }
        };

        var matches = new PatternMatcher(signExtendRecipe)
            .findMatches(List.of(param, c56, shl, sar));

        assertEquals(1, matches.size());
        assertEquals("SignExtend", matches.getFirst().recipe().name());
        assertTrue(matches.getFirst().explanation().contains("8 bits"));
    }

    @Test
    void longestMatchWinsOverlap() {
        SimpleIR.bootstrap();
        var p = new ParamNode("x");
        SimpleIR.typed(p);
        var c56 = SimpleIR.constant(56);
        var shl = SimpleIR.typed(new ShlNode(p, c56));
        var sar = SimpleIR.typed(new SarNode(shl, c56));

        // Small recipe: matches just SarNode
        Recipe smallRecipe = new Recipe() {
            @Override public String name() { return "Small"; }
            @Override public Optional<Match> match(Node root) {
                if (root instanceof SarNode) {
                    return Optional.of(new Match(this, root, Set.of(root),
                        "// small", "Small match"));
                }
                return Optional.empty();
            }
        };

        // Large recipe: matches SarNode + ShlNode (2 nodes)
        Recipe largeRecipe = new Recipe() {
            @Override public String name() { return "Large"; }
            @Override public Optional<Match> match(Node root) {
                if (root instanceof SarNode sarNode && sarNode.in(1) instanceof ShlNode shlNode) {
                    return Optional.of(new Match(this, root, Set.of(root, shlNode),
                        "// large", "Large match"));
                }
                return Optional.empty();
            }
        };

        var matches = new PatternMatcher(smallRecipe, largeRecipe)
            .findMatches(List.of(p, c56, shl, sar));

        assertEquals(1, matches.size());
        assertEquals("Large", matches.getFirst().recipe().name(),
            "Longest match should win");
    }

    @Test
    void isCoveredChecksCorrectly() {
        SimpleIR.bootstrap();
        var c1 = SimpleIR.constant(1);
        var c2 = SimpleIR.constant(2);
        var add = SimpleIR.typed(new AddNode(c1, c2));

        var match = new Match(null, add, Set.of(add, c1), "", "");
        var matches = List.of(match);

        assertTrue(PatternMatcher.isCovered(add, matches));
        assertTrue(PatternMatcher.isCovered(c1, matches));
        assertFalse(PatternMatcher.isCovered(c2, matches));
    }

    @Test
    void findCoveringMatchByRoot() {
        SimpleIR.bootstrap();
        var c1 = SimpleIR.constant(1);
        var c2 = SimpleIR.constant(2);
        var add = SimpleIR.typed(new AddNode(c1, c2));

        var match = new Match(null, add, Set.of(add), "", "");
        var matches = List.of(match);

        assertTrue(PatternMatcher.findCoveringMatch(add, matches).isPresent());
        assertTrue(PatternMatcher.findCoveringMatch(c1, matches).isEmpty());
    }

    @Test
    void multipleNonOverlappingMatchesKept() {
        SimpleIR.bootstrap();
        var c1 = SimpleIR.constant(1);
        var c2 = SimpleIR.constant(2);

        Recipe constRecipe = new Recipe() {
            @Override public String name() { return "Const"; }
            @Override public Optional<Match> match(Node root) {
                if (root instanceof ConstantNode cn
                    && cn._type instanceof TypeInteger ti && ti.isConstant()) {
                    return Optional.of(new Match(this, root, Set.of(root),
                        "// const", "Constant " + ti.value()));
                }
                return Optional.empty();
            }
        };

        var matches = new PatternMatcher(constRecipe).findMatches(List.of(c1, c2));
        assertEquals(2, matches.size());
    }
}

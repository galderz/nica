package org.mendrugo.nica.recipe;

import com.seaofnodes.simple.node.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mendrugo.nica.ir.SimpleIR;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MultiplyAccumulateRecipeTest {

    @BeforeEach
    void reset() { SimpleIR.reset(); }

    @Test
    void matchesMultiplyOfSignExtendedValues() {
        SimpleIR.bootstrap();
        // Build: add(mul(sar(shl(a,56),56), sar(shl(b,56),56)), mul(...))
        var a = new ParamNode("a"); SimpleIR.typed(a);
        var b = new ParamNode("b"); SimpleIR.typed(b);
        var c = new ParamNode("c"); SimpleIR.typed(c);
        var d = new ParamNode("d"); SimpleIR.typed(d);
        var c56 = SimpleIR.constant(56);

        var shlA = SimpleIR.typed(new ShlNode(a, c56));
        var sarA = SimpleIR.typed(new SarNode(shlA, c56));
        var shlB = SimpleIR.typed(new ShlNode(b, c56));
        var sarB = SimpleIR.typed(new SarNode(shlB, c56));
        var mul1 = SimpleIR.typed(new MulNode(sarA, sarB));

        var shlC = SimpleIR.typed(new ShlNode(c, c56));
        var sarC = SimpleIR.typed(new SarNode(shlC, c56));
        var shlD = SimpleIR.typed(new ShlNode(d, c56));
        var sarD = SimpleIR.typed(new SarNode(shlD, c56));
        var mul2 = SimpleIR.typed(new MulNode(sarC, sarD));

        var add = SimpleIR.typed(new AddNode(mul1, mul2));

        var recipe = new MultiplyAccumulateRecipe();
        var match = recipe.match(add);

        assertTrue(match.isPresent());
        assertEquals("Vectorized Multiply-Accumulate", match.get().recipe().name());
        assertTrue(match.get().javaCode().contains("signExtByte"));
        assertTrue(match.get().matchedNodes().contains(add));
        assertTrue(match.get().matchedNodes().contains(mul1));
        assertTrue(match.get().matchedNodes().contains(mul2));
    }

    @Test
    void matchesSingleMulPlusOther() {
        SimpleIR.bootstrap();
        var a = new ParamNode("a"); SimpleIR.typed(a);
        var b = new ParamNode("b"); SimpleIR.typed(b);
        var c56 = SimpleIR.constant(56);

        var shlA = SimpleIR.typed(new ShlNode(a, c56));
        var sarA = SimpleIR.typed(new SarNode(shlA, c56));
        var shlB = SimpleIR.typed(new ShlNode(b, c56));
        var sarB = SimpleIR.typed(new SarNode(shlB, c56));
        var mul = SimpleIR.typed(new MulNode(sarA, sarB));

        var other = SimpleIR.constant(100);
        var add = SimpleIR.typed(new AddNode(mul, other));

        var match = new MultiplyAccumulateRecipe().match(add);
        assertTrue(match.isPresent());
    }

    @Test
    void doesNotMatchPlainAdd() {
        SimpleIR.bootstrap();
        var c1 = SimpleIR.constant(10);
        var c2 = SimpleIR.constant(20);
        var add = SimpleIR.typed(new AddNode(c1, c2));

        var match = new MultiplyAccumulateRecipe().match(add);
        assertFalse(match.isPresent());
    }

    @Test
    void doesNotMatchMulWithoutSignExtend() {
        SimpleIR.bootstrap();
        var c1 = SimpleIR.constant(10);
        var c2 = SimpleIR.constant(20);
        var mul = SimpleIR.typed(new MulNode(c1, c2));
        var c3 = SimpleIR.constant(30);
        var add = SimpleIR.typed(new AddNode(mul, c3));

        var match = new MultiplyAccumulateRecipe().match(add);
        assertFalse(match.isPresent());
    }

    @Test
    void isSignExtendDetectsPattern() {
        SimpleIR.bootstrap();
        var p = new ParamNode("x"); SimpleIR.typed(p);
        var c56 = SimpleIR.constant(56);
        var shl = SimpleIR.typed(new ShlNode(p, c56));
        var sar = SimpleIR.typed(new SarNode(shl, c56));

        assertTrue(MultiplyAccumulateRecipe.isSignExtend(sar));
    }

    @Test
    void isSignExtendRejectsDifferentShifts() {
        SimpleIR.bootstrap();
        var p = new ParamNode("x"); SimpleIR.typed(p);
        var c56 = SimpleIR.constant(56);
        var c48 = SimpleIR.constant(48);
        var shl = SimpleIR.typed(new ShlNode(p, c56));
        var sar = SimpleIR.typed(new SarNode(shl, c48));

        assertFalse(MultiplyAccumulateRecipe.isSignExtend(sar));
    }

    @Test
    void matchedViaPatternMatcher() {
        SimpleIR.bootstrap();
        var a = new ParamNode("a"); SimpleIR.typed(a);
        var b = new ParamNode("b"); SimpleIR.typed(b);
        var c56 = SimpleIR.constant(56);
        var shlA = SimpleIR.typed(new ShlNode(a, c56));
        var sarA = SimpleIR.typed(new SarNode(shlA, c56));
        var shlB = SimpleIR.typed(new ShlNode(b, c56));
        var sarB = SimpleIR.typed(new SarNode(shlB, c56));
        var mul = SimpleIR.typed(new MulNode(sarA, sarB));
        var other = SimpleIR.constant(0);
        var add = SimpleIR.typed(new AddNode(mul, other));

        var matcher = new PatternMatcher(new MultiplyAccumulateRecipe());
        var matches = matcher.findMatches(
            List.of(a, b, c56, shlA, sarA, shlB, sarB, mul, other, add));

        assertEquals(1, matches.size());
    }
}

package org.mendrugo.nica.recipe;

import com.seaofnodes.simple.node.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mendrugo.nica.ir.SimpleIR;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShiftMaskXorReduceRecipeTest {

    @BeforeEach
    void reset() { SimpleIR.reset(); }

    @Test
    void matchesTruncateAndXor() {
        SimpleIR.bootstrap();
        // xor(sar(shl(value, 56), 56), accumulator)
        var value = new ParamNode("value"); SimpleIR.typed(value);
        var acc = new ParamNode("acc"); SimpleIR.typed(acc);
        var c56 = SimpleIR.constant(56);
        var shl = SimpleIR.typed(new ShlNode(value, c56));
        var sar = SimpleIR.typed(new SarNode(shl, c56));
        var xor = SimpleIR.typed(new XorNode(sar, acc));

        var match = new ShiftMaskXorReduceRecipe().match(xor);

        assertTrue(match.isPresent());
        assertEquals("Shift-Mask XOR Reduce", match.get().recipe().name());
        assertTrue(match.get().explanation().contains("8-bit"));
        assertTrue(match.get().javaCode().contains("byte"));
    }

    @Test
    void matchesChainedXors() {
        SimpleIR.bootstrap();
        // xor(xor(sar(shl(v1, 56), 56), acc), sar(shl(v2, 56), 56))
        var v1 = new ParamNode("v1"); SimpleIR.typed(v1);
        var v2 = new ParamNode("v2"); SimpleIR.typed(v2);
        var acc = new ParamNode("acc"); SimpleIR.typed(acc);
        var c56 = SimpleIR.constant(56);

        var shl1 = SimpleIR.typed(new ShlNode(v1, c56));
        var sar1 = SimpleIR.typed(new SarNode(shl1, c56));
        var xor1 = SimpleIR.typed(new XorNode(sar1, acc));

        var shl2 = SimpleIR.typed(new ShlNode(v2, c56));
        var sar2 = SimpleIR.typed(new SarNode(shl2, c56));
        var xor2 = SimpleIR.typed(new XorNode(xor1, sar2));

        var match = new ShiftMaskXorReduceRecipe().match(xor2);

        assertTrue(match.isPresent());
        assertTrue(match.get().explanation().contains("chained"));
        assertTrue(match.get().matchedNodes().size() > 3);
    }

    @Test
    void doesNotMatchPlainXor() {
        SimpleIR.bootstrap();
        var c1 = SimpleIR.constant(10);
        var c2 = SimpleIR.constant(20);
        var xor = SimpleIR.typed(new XorNode(c1, c2));

        assertFalse(new ShiftMaskXorReduceRecipe().match(xor).isPresent());
    }

    @Test
    void detectsBitWidth16() {
        SimpleIR.bootstrap();
        var value = new ParamNode("value"); SimpleIR.typed(value);
        var acc = new ParamNode("acc"); SimpleIR.typed(acc);
        var c48 = SimpleIR.constant(48); // 64-48 = 16 bits = short
        var shl = SimpleIR.typed(new ShlNode(value, c48));
        var sar = SimpleIR.typed(new SarNode(shl, c48));
        var xor = SimpleIR.typed(new XorNode(sar, acc));

        var match = new ShiftMaskXorReduceRecipe().match(xor);

        assertTrue(match.isPresent());
        assertTrue(match.get().explanation().contains("16-bit"));
        assertTrue(match.get().javaCode().contains("short"));
    }

    @Test
    void integratesWithPatternMatcher() {
        SimpleIR.bootstrap();
        var value = new ParamNode("value"); SimpleIR.typed(value);
        var acc = new ParamNode("acc"); SimpleIR.typed(acc);
        var c56 = SimpleIR.constant(56);
        var shl = SimpleIR.typed(new ShlNode(value, c56));
        var sar = SimpleIR.typed(new SarNode(shl, c56));
        var xor = SimpleIR.typed(new XorNode(sar, acc));

        var matcher = new PatternMatcher(new ShiftMaskXorReduceRecipe());
        var matches = matcher.findMatches(List.of(value, acc, c56, shl, sar, xor));

        assertEquals(1, matches.size());
    }
}

package org.mendrugo.nica.parser;

import org.junit.jupiter.api.Test;
import org.mendrugo.nica.asm.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PerfAnnotateParserTest {

    @Test
    void parsesMethodSignature() throws IOException {
        var snippet = parseResource();
        assertNotNull(snippet.methodSignature());
        assertTrue(snippet.methodSignature().contains("VanillaByteArrays::vhandleGetLongLE"));
    }

    @Test
    void parsesInstructionCount() throws IOException {
        var snippet = parseResource();
        // Count: leaq, cmpq, jbe, movq, movl, leaq, nop, testl, je, movl, cmpl, jb,
        //        movq, addq, cmpl, jle, retq, callq(43), movq, leaq, leal, movq, movl,
        //        nop, callq(preconditions), leaq, movq, callq(unwind), nop
        assertEquals(29, snippet.instructions().size());
    }

    @Test
    void parsesPercentage() throws IOException {
        var snippet = parseResource();
        // First instruction: leaq with 6.36%
        var first = snippet.instructions().getFirst();
        assertEquals("leaq", first.mnemonic());
        assertTrue(first.hasPerfData());
        assertEquals(6.36, first.perfPercent(), 0.001);
    }

    @Test
    void parsesInstructionWithoutPercentage() throws IOException {
        var snippet = parseResource();
        // jbe has no percentage
        var jbe = snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("jbe"))
            .findFirst().orElseThrow();
        assertFalse(jbe.hasPerfData());
    }

    @Test
    void parsesHighPercentageInstruction() throws IOException {
        var snippet = parseResource();
        // movl 0x4(%r14,%rax,8),%edi with 12.61%
        var hotInsn = snippet.instructions().stream()
            .filter(i -> i.hasPerfData() && Math.abs(i.perfPercent() - 12.61) < 0.01)
            .findFirst().orElseThrow();
        assertEquals("movl", hotInsn.mnemonic());
    }

    @Test
    void parsesMemoryOperandWithIndexAndScale() throws IOException {
        var snippet = parseResource();
        // leaq (%r14,%rax,8),%rcx
        var leaq = snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("leaq")
                && i.operands().size() == 2
                && i.operands().get(0) instanceof Operand.Memory mem
                && "r14".equals(mem.base()))
            .findFirst().orElseThrow();
        var mem = (Operand.Memory) leaq.operands().get(0);
        assertEquals("r14", mem.base());
        assertEquals("rax", mem.index());
        assertEquals(8, mem.scale());
        assertEquals(0, mem.displacement());
    }

    @Test
    void parsesBranchToLabel() throws IOException {
        var snippet = parseResource();
        // je 43
        var je = snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("je"))
            .findFirst().orElseThrow();
        assertEquals(1, je.operands().size());
        var addr = assertInstanceOf(Operand.Address.class, je.operands().get(0));
        assertEquals("43", addr.label());
    }

    @Test
    void parsesBranchToSymbol() throws IOException {
        var snippet = parseResource();
        // jbe void com.oracle.svm.core.graal.snippets.StackOverflowCheckImpl::throwNewStackOverflowError()
        var jbe = snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("jbe"))
            .findFirst().orElseThrow();
        assertEquals(1, jbe.operands().size());
        var addr = assertInstanceOf(Operand.Address.class, jbe.operands().get(0));
        assertNotNull(addr.label());
        assertTrue(addr.label().contains("StackOverflowCheckImpl"));
    }

    @Test
    void parsesCallToSymbol() throws IOException {
        var snippet = parseResource();
        // callq void com.oracle.svm.core.snippets.ImplicitExceptions::throwNewNullPointerException()
        var calls = snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("callq"))
            .toList();
        assertTrue(calls.size() >= 3);
        var npeCall = calls.getFirst();
        var addr = assertInstanceOf(Operand.Address.class, npeCall.operands().get(0));
        assertTrue(addr.label().contains("throwNewNullPointerException"));
    }

    @Test
    void parsesRetq() throws IOException {
        var snippet = parseResource();
        var retq = snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("retq"))
            .findFirst().orElseThrow();
        assertTrue(retq.operands().isEmpty());
        assertTrue(retq.hasPerfData());
        assertEquals(6.26, retq.perfPercent(), 0.01);
    }

    @Test
    void parsesNop() throws IOException {
        var snippet = parseResource();
        var nops = snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("nop"))
            .toList();
        assertFalse(nops.isEmpty());
    }

    @Test
    void parsesImmediateToMemory() throws IOException {
        var snippet = parseResource();
        // cmpl $0x0,0x10(%r15)
        var cmp = snippet.instructions().stream()
            .filter(i -> i.mnemonic().equals("cmpl")
                && i.operands().size() == 2
                && i.operands().get(1) instanceof Operand.Memory)
            .findFirst().orElseThrow();
        var imm = assertInstanceOf(Operand.Immediate.class, cmp.operands().get(0));
        assertEquals(0, imm.value());
        var mem = assertInstanceOf(Operand.Memory.class, cmp.operands().get(1));
        assertEquals("r15", mem.base());
        assertEquals(0x10, mem.displacement());
    }

    @Test
    void architectureIsX86() throws IOException {
        var snippet = parseResource();
        assertEquals(Architecture.X86_64, snippet.architecture());
    }

    // --- Helper ---

    private AssemblySnippet parseResource() throws IOException {
        String text = HotSpotDebugParserTest.loadResource("perf-annotate-graalvm-x86.asm");
        return PerfAnnotateParser.parse(text);
    }
}

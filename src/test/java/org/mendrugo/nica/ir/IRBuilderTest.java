package org.mendrugo.nica.ir;

import com.seaofnodes.simple.node.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mendrugo.nica.asm.*;
import org.mendrugo.nica.parser.HotSpotDebugParser;
import org.mendrugo.nica.semantics.MachineState;
import org.mendrugo.nica.semantics.x86.SimdX86Semantics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IRBuilderTest {

    @BeforeEach
    void resetSimple() {
        SimpleIR.reset();
    }

    // --- x86_64 full snippet tests ---

    @Test
    void x86IRGraphBuildsSuccessfully() throws IOException {
        var snippet = parseX86();
        var state = setupX86State();
        executeVmovqOnly(snippet, state);

        var graph = IRBuilder.build(snippet, state);

        assertNotNull(graph);
        assertTrue(graph.laneCount() > 0);
        assertFalse(graph.allNodes().isEmpty());
    }

    @Test
    void x86IRGraphHasCorrectLaneCount() throws IOException {
        var snippet = parseX86();
        var state = setupX86State();
        executeVmovqOnly(snippet, state);

        var graph = IRBuilder.build(snippet, state);
        assertEquals(8, graph.laneCount());
    }

    @Test
    void x86IRGraphContainsExpectedNodeTypes() throws IOException {
        var snippet = parseX86();
        var state = setupX86State();
        executeVmovqOnly(snippet, state);

        var graph = IRBuilder.build(snippet, state);

        assertTrue(graph.allNodes().stream().anyMatch(n -> n instanceof ShlNode));
        assertTrue(graph.allNodes().stream().anyMatch(n -> n instanceof SarNode));
        assertTrue(graph.allNodes().stream().anyMatch(n -> n instanceof AddNode));
        assertTrue(graph.allNodes().stream().anyMatch(n -> n instanceof MulNode));
        assertTrue(graph.allNodes().stream().anyMatch(n -> n instanceof XorNode));
        assertTrue(graph.allNodes().stream().anyMatch(n -> n instanceof ConstantNode));
    }

    // --- Small snippet correctness test ---

    @Test
    void smallX86SnippetEvaluatesCorrectly() {
        // vpmovsxbd %xmm0, %ymm0
        // vpmovsxbd %xmm1, %ymm1
        // vpmulld %ymm0, %ymm1, %ymm2
        // vpslld $0x18, %ymm2, %ymm2
        // vpsrad $0x18, %ymm2, %ymm2
        // vpxor %ymm2, %ymm0, %ymm0
        var insns = List.of(
            insn("vpmovsxbd", reg("xmm0"), reg("ymm0")),
            insn("vpmovsxbd", reg("xmm1"), reg("ymm1")),
            insn("vpmulld", reg("ymm0"), reg("ymm1"), reg("ymm2")),
            insn("vpslld", imm(0x18), reg("ymm2"), reg("ymm2")),
            insn("vpsrad", imm(0x18), reg("ymm2"), reg("ymm2")),
            insn("vpxor", reg("ymm2"), reg("ymm0"), reg("ymm0"))
        );
        var snippet = new AssemblySnippet(Architecture.X86_64, insns);

        // Expected: for each lane, (byte)(a[i]*b[i]) ^ a[i]
        byte[] a = {1, -1, 127, -128, 0, 2, -2, 64};
        byte[] b = {10, 20, 30, 40, 50, 60, 70, 80};

        // Execute on machine state
        var state = new MachineState(64);
        state.setSimd("xmm0", bytesToUnsignedInts(a));
        state.setSimd("xmm1", bytesToUnsignedInts(b));
        for (var i : insns) SimdX86Semantics.execute(i, state);
        int[] expected = state.getSimd("ymm0", 8);

        // Build IR and evaluate
        var loadState = new MachineState(64);
        loadState.setSimd("xmm0", bytesToUnsignedInts(a));
        loadState.setSimd("xmm1", bytesToUnsignedInts(b));
        var graph = IRBuilder.build(snippet, loadState);

        // Debug: check param names
        assertEquals(2, graph.paramNames().size(), "Should have params for xmm0 and xmm1. Got: " + graph.paramNames());

        for (int lane = 0; lane < 8; lane++) {
            var paramValues = bindParams(graph, loadState, lane);
            assertEquals(2, paramValues.size(), "Lane " + lane + " should bind 2 params, got " + paramValues.size());
            int actual = graph.evaluate(lane, paramValues);
            assertEquals(expected[lane], actual, "Lane " + lane + " mismatch");
        }
    }

    @Test
    void smallX86SnippetManualVerification() {
        byte[] a = {1, -1, 127, -128, 0, 2, -2, 64};
        byte[] b = {10, 20, 30, 40, 50, 60, 70, 80};
        int[] expected = new int[8];
        for (int i = 0; i < 8; i++) {
            int product = a[i] * b[i];
            int truncated = (byte) product;
            expected[i] = truncated ^ a[i];
        }

        var state = new MachineState(64);
        state.setSimd("xmm0", bytesToUnsignedInts(a));
        state.setSimd("xmm1", bytesToUnsignedInts(b));
        var insns = List.of(
            insn("vpmovsxbd", reg("xmm0"), reg("ymm0")),
            insn("vpmovsxbd", reg("xmm1"), reg("ymm1")),
            insn("vpmulld", reg("ymm0"), reg("ymm1"), reg("ymm2")),
            insn("vpslld", imm(0x18), reg("ymm2"), reg("ymm2")),
            insn("vpsrad", imm(0x18), reg("ymm2"), reg("ymm2")),
            insn("vpxor", reg("ymm2"), reg("ymm0"), reg("ymm0"))
        );
        for (var i : insns) SimdX86Semantics.execute(i, state);
        assertArrayEquals(expected, state.getSimd("ymm0", 8));
    }

    // --- Simple IR features ---

    @Test
    void prettyPrintContainsNodeInfo() {
        var insns = List.of(
            insn("vpmovsxbd", reg("xmm0"), reg("ymm0")),
            insn("vpmovsxbd", reg("xmm1"), reg("ymm1")),
            insn("vpmulld", reg("ymm0"), reg("ymm1"), reg("ymm2")),
            insn("vpxor", reg("ymm2"), reg("ymm0"), reg("ymm0"))
        );
        var snippet = new AssemblySnippet(Architecture.X86_64, insns);
        var state = new MachineState(64);
        state.setSimd("xmm0", new int[8]);
        state.setSimd("xmm1", new int[8]);

        var graph = IRBuilder.build(snippet, state);
        String printed = graph.prettyPrint();

        assertFalse(printed.isBlank());
        assertTrue(printed.contains("Mul"));
        assertTrue(printed.contains("Xor"));
        assertTrue(printed.contains("Shl")); // vpmovsxbd uses shl/sar
        assertTrue(printed.contains("Lane results"));
    }

    @Test
    void llvmFormatPrintWorks() {
        var insns = List.of(
            insn("vpmovsxbd", reg("xmm0"), reg("ymm0")),
            insn("vpxor", reg("ymm0"), reg("ymm0"), reg("ymm0"))
        );
        var snippet = new AssemblySnippet(Architecture.X86_64, insns);
        var state = new MachineState(64);
        state.setSimd("xmm0", new int[8]);

        var graph = IRBuilder.build(snippet, state);
        String llvm = graph.prettyPrintLlvm();

        assertFalse(llvm.isBlank());
        assertTrue(llvm.contains("%")); // LLVM-style node references
    }

    @Test
    void deepPrintShowsExpression() {
        var insns = List.of(
            insn("vpmovsxbd", reg("xmm0"), reg("ymm0")),
            insn("vpmovsxbd", reg("xmm1"), reg("ymm1")),
            insn("vpmulld", reg("ymm0"), reg("ymm1"), reg("ymm2")),
            insn("vpxor", reg("ymm2"), reg("ymm0"), reg("ymm0"))
        );
        var snippet = new AssemblySnippet(Architecture.X86_64, insns);
        var state = new MachineState(64);
        state.setSimd("xmm0", new int[8]);
        state.setSimd("xmm1", new int[8]);

        var graph = IRBuilder.build(snippet, state);
        String expr = graph.printLaneExpression(0);

        assertFalse(expr.isBlank());
        // Should show a nested expression involving shifts, multiplies, and xor
        assertTrue(expr.contains("^") || expr.contains("Xor"),
            "Expression should contain XOR: " + expr);
    }

    // --- aarch64 tests ---

    @Test
    void aarch64IRGraphBuilds() throws IOException {
        var snippet = parseAArch64();
        var state = new MachineState(4096);
        var graph = IRBuilder.build(snippet, state);

        assertNotNull(graph);
        assertTrue(graph.laneCount() > 0);
    }

    @Test
    void aarch64IRGraphHasCorrectLaneCount() throws IOException {
        var snippet = parseAArch64();
        var graph = IRBuilder.build(snippet, new MachineState(4096));
        assertEquals(4, graph.laneCount());
    }

    // --- Helpers ---

    private static int[] bytesToUnsignedInts(byte[] bytes) {
        int[] result = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) result[i] = bytes[i] & 0xFF;
        return result;
    }

    private Map<ParamNode, Integer> bindParams(NicaGraph graph, MachineState state, int lane) {
        var bindings = new IdentityHashMap<ParamNode, Integer>();
        for (var paramName : graph.paramNames()) {
            var params = graph.getParams(paramName);
            if (lane < params.size()) {
                var param = params.get(lane);
                int[] regData = state.getSimd(paramName, 8);
                bindings.put(param, regData[lane]);
            }
        }
        return bindings;
    }

    private AssemblySnippet parseX86() throws IOException {
        return HotSpotDebugParser.parse(loadResource("hotspot-debug-x86.asm"), Architecture.X86_64);
    }

    private AssemblySnippet parseAArch64() throws IOException {
        return HotSpotDebugParser.parse(loadResource("hotspot-debug-aarch64.asm"), Architecture.AARCH64);
    }

    private MachineState setupX86State() {
        var state = new MachineState(4096);
        state.setReg64("rsi", 100);
        state.setReg64("rdx", 200);
        state.setReg64("rcx", 300);
        state.setReg64("r9", 0);
        byte[] data = {1, -1, 2, -2, 3, -3, 4, -4};
        for (long base : new long[]{100, 200, 300}) {
            for (int off : new int[]{0xc, 0x14, 0x1c, 0x24}) {
                state.storeBytes(base + off, data);
            }
        }
        int[] xmm0 = new int[8];
        for (int i = 0; i < 8; i++) xmm0[i] = (int) ((100L >> (i * 8)) & 0xFF);
        state.setSimd("xmm0", xmm0);
        state.setSimd("ymm11", new int[8]);
        return state;
    }

    private void executeVmovqOnly(AssemblySnippet snippet, MachineState state) {
        for (var insn : snippet.instructions()) {
            if (insn.mnemonic().equals("vmovq")) {
                SimdX86Semantics.execute(insn, state);
            }
        }
    }

    private static Instruction insn(String mnemonic, Operand... ops) {
        return new Instruction(-1, mnemonic, List.of(ops), mnemonic);
    }
    private static Operand.Register reg(String name) { return new Operand.Register(name); }
    private static Operand.Immediate imm(long value) { return new Operand.Immediate(value); }

    private static String loadResource(String name) throws IOException {
        try (var is = IRBuilderTest.class.getResourceAsStream("/" + name)) {
            if (is == null) throw new IOException("Resource not found: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

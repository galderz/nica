package org.mendrugo.nica.ir;

import org.junit.jupiter.api.Test;
import org.mendrugo.nica.asm.*;
import org.mendrugo.nica.parser.HotSpotDebugParser;
import org.mendrugo.nica.semantics.MachineState;
import org.mendrugo.nica.semantics.x86.ScalarX86Semantics;
import org.mendrugo.nica.semantics.x86.SimdX86Semantics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IRBuilderTest {

    // --- Seed data: three byte arrays for the XOR computation ---
    static final byte[] ARRAY_A = {1, -1, 2, -2, 3, -3, 4, -4};
    static final byte[] ARRAY_B = {10, 20, 30, 40, 50, 60, 70, 80};
    static final byte[] ARRAY_C = {5, 15, 25, 35, 45, 55, 65, 75};

    @Test
    void x86IRGraphBuildsSuccessfully() throws IOException {
        var snippet = parseX86();
        var state = setupX86State();
        executeX86SimdInstructions(snippet, state);

        var graph = IRBuilder.build(snippet, state);

        assertNotNull(graph);
        assertTrue(graph.laneCount() > 0, "Graph should have lane results");
        assertFalse(graph.allNodes().isEmpty(), "Graph should have nodes");
    }

    @Test
    void x86IRGraphHasCorrectLaneCount() throws IOException {
        var snippet = parseX86();
        var state = setupX86State();
        executeX86SimdInstructions(snippet, state);

        var graph = IRBuilder.build(snippet, state);

        assertEquals(8, graph.laneCount(), "x86 AVX2 ymm has 8 int lanes");
    }

    @Test
    void smallX86SnippetEvaluatesCorrectly() {
        // Minimal snippet: load 2 registers, sign-extend, multiply, XOR results
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

        // Set up xmm registers with known byte values
        var state = new MachineState(64);
        state.setSimd("xmm0", new int[]{1, 0xFF, 127, 0x80, 0, 2, 0xFE, 64});
        state.setSimd("xmm1", new int[]{10, 20, 30, 40, 50, 60, 70, 80});

        // Execute on machine state for expected result
        for (var insn : insns) SimdX86Semantics.execute(insn, state);
        int[] expected = state.getSimd("ymm0", 8);

        // Build IR and evaluate — capture pre-execution register values
        var loadState = new MachineState(64);
        loadState.setSimd("xmm0", new int[]{1, 0xFF, 127, 0x80, 0, 2, 0xFE, 64});
        loadState.setSimd("xmm1", new int[]{10, 20, 30, 40, 50, 60, 70, 80});

        var graph = IRBuilder.build(snippet, loadState);

        for (int lane = 0; lane < 8; lane++) {
            var paramValues = bindX86Params(graph, loadState, lane);
            int actual = graph.evaluate(lane, paramValues);
            assertEquals(expected[lane], actual, "Lane " + lane + " mismatch");
        }
    }

    @Test
    void smallX86SnippetExpectedValues() {
        // Verify the expected computation manually:
        // xmm0 bytes: [1, -1, 127, -128, 0, 2, -2, 64]
        // xmm1 bytes: [10, 20, 30, 40, 50, 60, 70, 80]
        // vpmovsxbd: sign-extend → ymm0=[1,-1,127,-128,0,2,-2,64], ymm1=[10,20,30,40,50,60,70,80]
        // vpmulld: ymm2 = ymm0 * ymm1 = [10,-20,3810,-5120,0,120,-140,5120]
        // vpslld $24: ymm2[i] <<= 24
        // vpsrad $24: ymm2[i] >>= 24 (sign-extend to byte range)
        // vpxor: ymm0 = ymm2 ^ ymm0

        byte[] a = {1, -1, 127, -128, 0, 2, -2, 64};
        byte[] b = {10, 20, 30, 40, 50, 60, 70, 80};
        int[] expected = new int[8];
        for (int i = 0; i < 8; i++) {
            int product = a[i] * b[i];
            int truncated = (byte) product; // shl 24 then sar 24 = truncate to signed byte
            expected[i] = truncated ^ a[i];
        }

        // Verify via machine state
        var state = new MachineState(64);
        state.setSimd("xmm0", new int[]{1, 0xFF, 127, 0x80, 0, 2, 0xFE, 64});
        state.setSimd("xmm1", new int[]{10, 20, 30, 40, 50, 60, 70, 80});
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

    @Test
    void aarch64IRGraphBuildsSuccessfully() throws IOException {
        var snippet = parseAArch64();
        var state = setupAArch64State();

        var graph = IRBuilder.build(snippet, state);

        assertNotNull(graph);
        assertTrue(graph.laneCount() > 0);
    }

    @Test
    void aarch64IRGraphHasCorrectLaneCount() throws IOException {
        var snippet = parseAArch64();
        var state = setupAArch64State();

        var graph = IRBuilder.build(snippet, state);

        assertEquals(4, graph.laneCount(), "aarch64 NEON .4s has 4 int lanes");
    }

    @Test
    void x86IRGraphPrettyPrints() throws IOException {
        var snippet = parseX86();
        var state = setupX86State();
        executeX86SimdInstructions(snippet, state);

        var graph = IRBuilder.build(snippet, state);
        String printed = graph.prettyPrint();

        assertFalse(printed.isBlank());
        assertTrue(printed.contains("Add"));
        assertTrue(printed.contains("Mul"));
        assertTrue(printed.contains("Xor"));
        assertTrue(printed.contains("Convert"));
        assertTrue(printed.contains("Lane results"));
    }

    @Test
    void x86IRGraphContainsExpectedNodeTypes() throws IOException {
        var snippet = parseX86();
        var state = setupX86State();
        executeX86SimdInstructions(snippet, state);

        var graph = IRBuilder.build(snippet, state);

        // The example has: vmovq (params), vpmovsxbd (Convert), vpmulld (Mul),
        // vpaddd (Add), vpslld (Shl), vpsrad (Sar), vpxor (Xor)
        assertTrue(graph.allNodes().stream().anyMatch(n -> n instanceof NicaNode.Convert));
        assertTrue(graph.allNodes().stream().anyMatch(n -> n instanceof NicaNode.Add));
        assertTrue(graph.allNodes().stream().anyMatch(n -> n instanceof NicaNode.Mul));
        assertTrue(graph.allNodes().stream().anyMatch(n -> n instanceof NicaNode.Xor));
        assertTrue(graph.allNodes().stream().anyMatch(n -> n instanceof NicaNode.Shl));
        assertTrue(graph.allNodes().stream().anyMatch(n -> n instanceof NicaNode.Sar));
        assertTrue(graph.allNodes().stream().anyMatch(n -> n instanceof NicaNode.Param));
    }

    // --- Helpers ---

    private AssemblySnippet parseX86() throws IOException {
        return HotSpotDebugParser.parse(loadResource("hotspot-debug-x86.asm"), Architecture.X86_64);
    }

    private AssemblySnippet parseAArch64() throws IOException {
        return HotSpotDebugParser.parse(loadResource("hotspot-debug-aarch64.asm"), Architecture.AARCH64);
    }

    /**
     * Set up machine state with three byte arrays at known addresses,
     * simulating the memory layout the JIT snippet expects.
     */
    private MachineState setupX86State() {
        var state = new MachineState(4096);
        // rsi, rdx, rcx point to three "arrays" (base addresses)
        // r9 is the loop index offset (0 for first iteration)
        long baseA = 100, baseB = 200, baseC = 300;
        state.setReg64("rsi", baseA);  // array a
        state.setReg64("rdx", baseB);  // array b
        state.setReg64("rcx", baseC);  // array c
        state.setReg64("r9", 0);       // offset = 0

        // Store test data at the offsets used by the snippet:
        // 0xc=12, 0x14=20, 0x1c=28, 0x24=36
        // Each vmovq loads 8 bytes from base+offset+r9
        storeTestBytes(state, baseA, ARRAY_A);
        storeTestBytes(state, baseB, ARRAY_B);
        storeTestBytes(state, baseC, ARRAY_C);

        // Pre-initialize xmm0 with rsi value (spill slot for loop backedge).
        // The snippet starts with "vmovq %xmm0, %rsi" which restores rsi from xmm0.
        int[] xmm0 = new int[8];
        for (int i = 0; i < 8; i++) xmm0[i] = (int) ((baseA >> (i * 8)) & 0xFF);
        state.setSimd("xmm0", xmm0);

        // ymm11 is the loop accumulator, starts at 0 on first iteration
        state.setSimd("ymm11", new int[8]);

        return state;
    }

    private MachineState setupAArch64State() {
        var state = new MachineState(4096);
        // x2, x3 (via x0), x11 point to arrays; w17 is the loop index
        long baseA = 100, baseB = 200, baseC = 300;
        state.setReg64("x2", baseA);
        state.setReg64("x3", baseB);
        state.setReg64("x11", baseC);
        state.setReg32("w17", 0);

        storeTestBytesAArch64(state, baseA);
        storeTestBytesAArch64(state, baseB);
        storeTestBytesAArch64(state, baseC);
        return state;
    }

    private void storeTestBytes(MachineState state, long base, byte[] pattern) {
        // The x86 snippet loads from offsets 0xc, 0x14, 0x1c, 0x24
        // Each load is 8 bytes. We use the same pattern for each group.
        for (int offset : new int[]{0xc, 0x14, 0x1c, 0x24}) {
            state.storeBytes(base + offset, pattern);
        }
    }

    private void storeTestBytesAArch64(MachineState state, long base) {
        // aarch64 loads from offsets 0xc, 0x10, 0x14, 0x18 (4 bytes each via ldr s-reg)
        byte[] data = ARRAY_A; // simplified: same data
        for (int offset : new int[]{0xc, 0x10, 0x14, 0x18}) {
            state.storeBytes(base + offset, data);
        }
    }

    /**
     * Execute the SIMD instructions on the machine state to populate registers.
     * This is needed so vmovq loads put actual byte values into xmm registers.
     */
    private void executeX86SimdInstructions(AssemblySnippet snippet, MachineState state) {
        for (var insn : snippet.instructions()) {
            if (SimdX86Semantics.isSimd(insn.mnemonic())) {
                SimdX86Semantics.execute(insn, state);
            } else {
                try { ScalarX86Semantics.execute(insn, state); } catch (Exception _) {}
            }
        }
    }

    /**
     * Compute the expected XOR result by reading the final ymm11 register
     * (the last vpxor destination in the x86 example).
     */
    private int[] computeExpectedXorResult(MachineState state) {
        return state.getSimd("ymm11", 8);
    }

    /**
     * Bind IR graph parameters to the actual byte values from the machine state.
     */
    private Map<NicaNode, Integer> bindX86Params(NicaGraph graph, MachineState state, int lane) {
        var bindings = new HashMap<NicaNode, Integer>();
        for (var paramName : graph.paramNames()) {
            var params = graph.getParams(paramName);
            if (lane < params.size()) {
                var param = params.get(lane);
                // Get the actual byte value from the register in machine state
                int[] regData = state.getSimd(paramName, 8);
                bindings.put(param, regData[lane]);
            }
        }
        return bindings;
    }

    // --- Instruction construction helpers ---

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

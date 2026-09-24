package org.mendrugo.nica.ir;

import org.mendrugo.nica.asm.*;
import org.mendrugo.nica.semantics.MachineState;

import java.util.*;

/**
 * Builds a {@link NicaGraph} from a parsed {@link AssemblySnippet}.
 *
 * <p>Translates assembly instructions into architecture-agnostic IR nodes.
 * SIMD instructions are expanded into N scalar data-flow graphs (one per lane).
 * Scalar instructions that feed SIMD pipelines are modeled as parameters.</p>
 *
 * <p>The builder works by symbolically executing the snippet: each register
 * is mapped to the IR node that last wrote to it, and each instruction
 * creates new nodes that reference their input nodes.</p>
 */
public final class IRBuilder {

    private IRBuilder() {}

    /**
     * Build an IR graph from the data-flow portion of an assembly snippet.
     *
     * <p>Currently handles the vectorized computation core of the example
     * snippets. Control flow (loops, branches) is not modeled in the IR —
     * the graph represents a single iteration's data-flow.</p>
     *
     * @param snippet the parsed assembly
     * @param state a pre-configured machine state for resolving memory loads
     * @return the constructed graph
     */
    public static NicaGraph build(AssemblySnippet snippet, MachineState state) {
        return switch (snippet.architecture()) {
            case X86_64 -> buildX86(snippet, state);
            case AARCH64 -> buildAArch64(snippet, state);
        };
    }

    // --- x86_64 IR construction ---

    private static NicaGraph buildX86(AssemblySnippet snippet, MachineState state) {
        var graph = new NicaGraph();
        // Track which IR node each SIMD register lane maps to
        // Key: "ymm0[3]" → the NicaNode for lane 3 of ymm0
        var regNodes = new HashMap<String, NicaNode>();

        for (var insn : snippet.instructions()) {
            switch (insn.mnemonic()) {
                case "vmovq" -> buildVmovqX86(insn, graph, regNodes, state);
                case "vpmovsxbd" -> buildVpmovsxbd(insn, graph, regNodes);
                case "vpmulld" -> buildVpmulld(insn, graph, regNodes);
                case "vpaddd" -> buildVpaddd(insn, graph, regNodes);
                case "vpslld" -> buildVpslld(insn, graph, regNodes);
                case "vpsrad" -> buildVpsrad(insn, graph, regNodes);
                case "vpxor" -> buildVpxor(insn, graph, regNodes);
                // Scalar instructions (leal, cmpl, jl) are control flow — skip for data-flow IR
                case "leal", "cmpl", "jl" -> {}
                default -> {} // Skip unknown
            }
        }

        // Find the final result — look for the last vpxor destination
        setResultFromLastXor(graph, regNodes, 8);
        return graph;
    }

    private static void buildVmovqX86(Instruction insn, NicaGraph graph,
                                       Map<String, NicaNode> regNodes, MachineState state) {
        var ops = insn.operands();
        if (ops.get(0) instanceof Operand.Memory && ops.get(1) instanceof Operand.Register dst) {
            // Memory load → xmm: create parameter nodes for the 8 bytes loaded
            String name = dst.name();
            // Execute the instruction on the real machine state to get actual bytes
            int[] bytes = state.getSimd(name, 8);
            for (int i = 0; i < 8; i++) {
                var param = graph.addParam(name, i);
                regNodes.put(name + "[" + i + "]", param);
            }
        } else if (ops.get(0) instanceof Operand.Register src
                && ops.get(1) instanceof Operand.Register dst) {
            // Register-to-register: copy the node mappings
            for (int i = 0; i < 8; i++) {
                var node = regNodes.get(src.name() + "[" + i + "]");
                if (node != null) regNodes.put(dst.name() + "[" + i + "]", node);
            }
        }
    }

    private static void buildVpmovsxbd(Instruction insn, NicaGraph graph,
                                        Map<String, NicaNode> regNodes) {
        String src = ((Operand.Register) insn.operands().get(0)).name();
        String dst = ((Operand.Register) insn.operands().get(1)).name();
        var c8 = graph.add(new NicaNode.Constant(graph.nextId(), 8));
        for (int i = 0; i < 8; i++) {
            var srcNode = getOrCreateParam(graph, regNodes, src, i);
            var convert = graph.add(new NicaNode.Convert(graph.nextId(), 8, 32, srcNode));
            regNodes.put(dst + "[" + i + "]", convert);
        }
    }

    private static void buildVpmulld(Instruction insn, NicaGraph graph,
                                      Map<String, NicaNode> regNodes) {
        String a = ((Operand.Register) insn.operands().get(0)).name();
        String b = ((Operand.Register) insn.operands().get(1)).name();
        String dst = ((Operand.Register) insn.operands().get(2)).name();
        for (int i = 0; i < 8; i++) {
            var nodeA = getOrCreateParam(graph, regNodes, a, i);
            var nodeB = getOrCreateParam(graph, regNodes, b, i);
            var mul = graph.add(new NicaNode.Mul(graph.nextId(), nodeA, nodeB));
            regNodes.put(dst + "[" + i + "]", mul);
        }
    }

    private static void buildVpaddd(Instruction insn, NicaGraph graph,
                                     Map<String, NicaNode> regNodes) {
        String a = ((Operand.Register) insn.operands().get(0)).name();
        String b = ((Operand.Register) insn.operands().get(1)).name();
        String dst = ((Operand.Register) insn.operands().get(2)).name();
        for (int i = 0; i < 8; i++) {
            var nodeA = getOrCreateParam(graph, regNodes, a, i);
            var nodeB = getOrCreateParam(graph, regNodes, b, i);
            var add = graph.add(new NicaNode.Add(graph.nextId(), nodeA, nodeB));
            regNodes.put(dst + "[" + i + "]", add);
        }
    }

    private static void buildVpslld(Instruction insn, NicaGraph graph,
                                     Map<String, NicaNode> regNodes) {
        int shift = (int) ((Operand.Immediate) insn.operands().get(0)).value();
        String src = ((Operand.Register) insn.operands().get(1)).name();
        String dst = ((Operand.Register) insn.operands().get(2)).name();
        var shiftConst = graph.add(new NicaNode.Constant(graph.nextId(), shift));
        for (int i = 0; i < 8; i++) {
            var srcNode = getOrCreateParam(graph, regNodes, src, i);
            var shl = graph.add(new NicaNode.Shl(graph.nextId(), srcNode, shiftConst));
            regNodes.put(dst + "[" + i + "]", shl);
        }
    }

    private static void buildVpsrad(Instruction insn, NicaGraph graph,
                                     Map<String, NicaNode> regNodes) {
        int shift = (int) ((Operand.Immediate) insn.operands().get(0)).value();
        String src = ((Operand.Register) insn.operands().get(1)).name();
        String dst = ((Operand.Register) insn.operands().get(2)).name();
        var shiftConst = graph.add(new NicaNode.Constant(graph.nextId(), shift));
        for (int i = 0; i < 8; i++) {
            var srcNode = getOrCreateParam(graph, regNodes, src, i);
            var sar = graph.add(new NicaNode.Sar(graph.nextId(), srcNode, shiftConst));
            regNodes.put(dst + "[" + i + "]", sar);
        }
    }

    private static void buildVpxor(Instruction insn, NicaGraph graph,
                                    Map<String, NicaNode> regNodes) {
        String a = ((Operand.Register) insn.operands().get(0)).name();
        String b = ((Operand.Register) insn.operands().get(1)).name();
        String dst = ((Operand.Register) insn.operands().get(2)).name();
        for (int i = 0; i < 8; i++) {
            var nodeA = getOrCreateParam(graph, regNodes, a, i);
            var nodeB = getOrCreateParam(graph, regNodes, b, i);
            var xor = graph.add(new NicaNode.Xor(graph.nextId(), nodeA, nodeB));
            regNodes.put(dst + "[" + i + "]", xor);
        }
    }

    // --- aarch64 IR construction ---

    private static NicaGraph buildAArch64(AssemblySnippet snippet, MachineState state) {
        var graph = new NicaGraph();
        var regNodes = new HashMap<String, NicaNode>();

        for (var insn : snippet.instructions()) {
            boolean isVec = insn.operands().stream().anyMatch(
                op -> op instanceof Operand.Register r && r.isVector());

            switch (insn.mnemonic()) {
                case "ldr" -> buildLdrAArch64(insn, graph, regNodes, state);
                case "sshll" -> buildSshll(insn, graph, regNodes);
                case "mul" -> { if (isVec) buildMulVec(insn, graph, regNodes); }
                case "mla" -> { if (isVec) buildMlaVec(insn, graph, regNodes); }
                case "add" -> { if (isVec) buildAddVec(insn, graph, regNodes); }
                case "shl" -> { if (isVec) buildShlVec(insn, graph, regNodes); }
                case "sshr" -> { if (isVec) buildSshrVec(insn, graph, regNodes); }
                case "eor3" -> buildEor3(insn, graph, regNodes);
                // Scalar: sxtw, add (scalar), cmp, b.lt — control flow, skip
                default -> {}
            }
        }

        setResultFromLastXor(graph, regNodes, 4);
        return graph;
    }

    private static void buildLdrAArch64(Instruction insn, NicaGraph graph,
                                         Map<String, NicaNode> regNodes, MachineState state) {
        var dst = (Operand.Register) insn.operands().get(0);
        if (dst.name().startsWith("s")) {
            // s-register: 4 bytes loaded, create params for 4 bytes
            for (int i = 0; i < 4; i++) {
                var param = graph.addParam(dst.name(), i);
                regNodes.put(dst.name() + "[" + i + "]", param);
            }
        }
    }

    private static void buildSshll(Instruction insn, NicaGraph graph,
                                    Map<String, NicaNode> regNodes) {
        var dst = (Operand.Register) insn.operands().get(0);
        var src = (Operand.Register) insn.operands().get(1);
        String srcArr = src.arrangement();
        String dstArr = dst.arrangement();
        int srcLanes = parseLanes(srcArr);

        if (srcArr.endsWith("b")) {
            // .8b → .8h: sign-extend bytes → halfwords, stored as Convert(8→16)
            for (int i = 0; i < srcLanes; i++) {
                var srcNode = getOrCreateParam(graph, regNodes, src.name(), i);
                var convert = graph.add(new NicaNode.Convert(graph.nextId(), 8, 16, srcNode));
                regNodes.put(dst.name() + "[" + i + "]", convert);
            }
        } else if (srcArr.endsWith("h")) {
            // .4h → .4s: sign-extend halfwords → ints, Convert(16→32)
            int lanes = parseLanes(srcArr);
            for (int i = 0; i < lanes; i++) {
                var srcNode = getOrCreateParam(graph, regNodes, src.name(), i);
                var convert = graph.add(new NicaNode.Convert(graph.nextId(), 16, 32, srcNode));
                regNodes.put(dst.name() + "[" + i + "]", convert);
            }
        }
    }

    private static void buildMulVec(Instruction insn, NicaGraph graph,
                                     Map<String, NicaNode> regNodes) {
        var dst = (Operand.Register) insn.operands().get(0);
        var a = (Operand.Register) insn.operands().get(1);
        var b = (Operand.Register) insn.operands().get(2);
        int lanes = parseLanes(dst.arrangement());
        for (int i = 0; i < lanes; i++) {
            var nodeA = getOrCreateParam(graph, regNodes, a.name(), i);
            var nodeB = getOrCreateParam(graph, regNodes, b.name(), i);
            var mul = graph.add(new NicaNode.Mul(graph.nextId(), nodeA, nodeB));
            regNodes.put(dst.name() + "[" + i + "]", mul);
        }
    }

    private static void buildMlaVec(Instruction insn, NicaGraph graph,
                                     Map<String, NicaNode> regNodes) {
        var dst = (Operand.Register) insn.operands().get(0);
        var a = (Operand.Register) insn.operands().get(1);
        var b = (Operand.Register) insn.operands().get(2);
        int lanes = parseLanes(dst.arrangement());
        for (int i = 0; i < lanes; i++) {
            var acc = getOrCreateParam(graph, regNodes, dst.name(), i);
            var nodeA = getOrCreateParam(graph, regNodes, a.name(), i);
            var nodeB = getOrCreateParam(graph, regNodes, b.name(), i);
            var mla = graph.add(new NicaNode.Mla(graph.nextId(), acc, nodeA, nodeB));
            regNodes.put(dst.name() + "[" + i + "]", mla);
        }
    }

    private static void buildAddVec(Instruction insn, NicaGraph graph,
                                     Map<String, NicaNode> regNodes) {
        var dst = (Operand.Register) insn.operands().get(0);
        var a = (Operand.Register) insn.operands().get(1);
        var b = (Operand.Register) insn.operands().get(2);
        int lanes = parseLanes(dst.arrangement());
        for (int i = 0; i < lanes; i++) {
            var nodeA = getOrCreateParam(graph, regNodes, a.name(), i);
            var nodeB = getOrCreateParam(graph, regNodes, b.name(), i);
            var add = graph.add(new NicaNode.Add(graph.nextId(), nodeA, nodeB));
            regNodes.put(dst.name() + "[" + i + "]", add);
        }
    }

    private static void buildShlVec(Instruction insn, NicaGraph graph,
                                     Map<String, NicaNode> regNodes) {
        var dst = (Operand.Register) insn.operands().get(0);
        var src = (Operand.Register) insn.operands().get(1);
        int shift = (int) ((Operand.Immediate) insn.operands().get(2)).value();
        int lanes = parseLanes(dst.arrangement());
        var shiftConst = graph.add(new NicaNode.Constant(graph.nextId(), shift));
        for (int i = 0; i < lanes; i++) {
            var srcNode = getOrCreateParam(graph, regNodes, src.name(), i);
            var shl = graph.add(new NicaNode.Shl(graph.nextId(), srcNode, shiftConst));
            regNodes.put(dst.name() + "[" + i + "]", shl);
        }
    }

    private static void buildSshrVec(Instruction insn, NicaGraph graph,
                                      Map<String, NicaNode> regNodes) {
        var dst = (Operand.Register) insn.operands().get(0);
        var src = (Operand.Register) insn.operands().get(1);
        int shift = (int) ((Operand.Immediate) insn.operands().get(2)).value();
        int lanes = parseLanes(dst.arrangement());
        var shiftConst = graph.add(new NicaNode.Constant(graph.nextId(), shift));
        for (int i = 0; i < lanes; i++) {
            var srcNode = getOrCreateParam(graph, regNodes, src.name(), i);
            var sar = graph.add(new NicaNode.Sar(graph.nextId(), srcNode, shiftConst));
            regNodes.put(dst.name() + "[" + i + "]", sar);
        }
    }

    private static void buildEor3(Instruction insn, NicaGraph graph,
                                   Map<String, NicaNode> regNodes) {
        var dst = (Operand.Register) insn.operands().get(0);
        var a = (Operand.Register) insn.operands().get(1);
        var b = (Operand.Register) insn.operands().get(2);
        var c = (Operand.Register) insn.operands().get(3);
        // eor3 operates on .16b but we model at .4s granularity (XOR is bitwise)
        for (int i = 0; i < 4; i++) {
            var nodeA = getOrCreateParam(graph, regNodes, a.name(), i);
            var nodeB = getOrCreateParam(graph, regNodes, b.name(), i);
            var nodeC = getOrCreateParam(graph, regNodes, c.name(), i);
            var xor1 = graph.add(new NicaNode.Xor(graph.nextId(), nodeA, nodeB));
            var xor2 = graph.add(new NicaNode.Xor(graph.nextId(), xor1, nodeC));
            regNodes.put(dst.name() + "[" + i + "]", xor2);
        }
    }

    // --- Helpers ---

    private static NicaNode getOrCreateParam(NicaGraph graph,
                                              Map<String, NicaNode> regNodes,
                                              String regName, int lane) {
        String key = regName + "[" + lane + "]";
        return regNodes.computeIfAbsent(key, _ -> graph.addParam(regName, lane));
    }

    private static void setResultFromLastXor(NicaGraph graph,
                                              Map<String, NicaNode> regNodes,
                                              int lanes) {
        // Find the last register written by a Xor/eor3 node
        String lastXorReg = null;
        for (var entry : regNodes.entrySet()) {
            if (entry.getValue() instanceof NicaNode.Xor) {
                String key = entry.getKey();
                String reg = key.substring(0, key.indexOf('['));
                lastXorReg = reg;
            }
        }
        if (lastXorReg != null) {
            for (int i = 0; i < lanes; i++) {
                var node = regNodes.get(lastXorReg + "[" + i + "]");
                if (node != null) graph.setLaneResult(i, node);
            }
        }
    }

    private static int parseLanes(String arrangement) {
        StringBuilder digits = new StringBuilder();
        for (char c : arrangement.toCharArray()) {
            if (Character.isDigit(c)) digits.append(c);
            else break;
        }
        return Integer.parseInt(digits.toString());
    }
}

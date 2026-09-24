package org.mendrugo.nica.ir;

import com.seaofnodes.simple.node.*;
import org.mendrugo.nica.asm.*;
import org.mendrugo.nica.semantics.MachineState;

import java.util.*;

/**
 * Builds a {@link NicaGraph} of Simple IR nodes from a parsed {@link AssemblySnippet}.
 *
 * <p>Translates assembly instructions into architecture-agnostic Simple IR nodes.
 * SIMD instructions are expanded into N scalar data-flow graphs (one per lane).
 * Memory loads are modeled as named parameters (ConstantNodes whose values
 * are bound before evaluation).</p>
 *
 * <p>Uses Simple chapter 14 nodes: AddNode, MulNode, XorNode, ShlNode, SarNode,
 * ConstantNode. The graph is constructed with peepholes disabled to prevent
 * Simple's optimizer from rewriting the faithful representation.</p>
 */
public final class IRBuilder {

    private IRBuilder() {}

    /**
     * Build an IR graph from the data-flow portion of an assembly snippet.
     *
     * @param snippet the parsed assembly
     * @param state a pre-configured machine state for resolving memory loads
     * @return the constructed graph
     */
    public static NicaGraph build(AssemblySnippet snippet, MachineState state) {
        SimpleIR.bootstrap();
        return switch (snippet.architecture()) {
            case X86_64 -> buildX86(snippet, state);
            case AARCH64 -> buildAArch64(snippet, state);
        };
    }

    // --- x86_64 IR construction ---

    private static NicaGraph buildX86(AssemblySnippet snippet, MachineState state) {
        var graph = new NicaGraph();
        var regNodes = new HashMap<String, Node>();

        for (var insn : snippet.instructions()) {
            switch (insn.mnemonic()) {
                case "vmovq" -> buildVmovqX86(insn, graph, regNodes, state);
                case "vpmovsxbd" -> buildVpmovsxbd(insn, graph, regNodes);
                case "vpmulld" -> buildBinop(insn, graph, regNodes, 8, IRBuilder::makeMul);
                case "vpaddd" -> buildBinop(insn, graph, regNodes, 8, IRBuilder::makeAdd);
                case "vpslld" -> buildShift(insn, graph, regNodes, 8, IRBuilder::makeShl);
                case "vpsrad" -> buildShift(insn, graph, regNodes, 8, IRBuilder::makeSar);
                case "vpxor" -> buildBinop(insn, graph, regNodes, 8, IRBuilder::makeXor);
                case "leal", "cmpl", "jl" -> {} // Control flow, skip
                default -> {}
            }
        }

        setResultFromLastXor(graph, regNodes, 8);
        return graph;
    }

    private static void buildVmovqX86(Instruction insn, NicaGraph graph,
                                       Map<String, Node> regNodes, MachineState state) {
        var ops = insn.operands();
        if (ops.get(0) instanceof Operand.Memory && ops.get(1) instanceof Operand.Register dst) {
            String name = dst.name();
            for (int i = 0; i < 8; i++) {
                var param = graph.addParam(name, i);
                regNodes.put(name + "[" + i + "]", param);
            }
        } else if (ops.get(0) instanceof Operand.Register src
                && ops.get(1) instanceof Operand.Register dst) {
            for (int i = 0; i < 8; i++) {
                var node = regNodes.get(src.name() + "[" + i + "]");
                if (node != null) regNodes.put(dst.name() + "[" + i + "]", node);
            }
        }
    }

    private static void buildVpmovsxbd(Instruction insn, NicaGraph graph,
                                        Map<String, Node> regNodes) {
        String src = ((Operand.Register) insn.operands().get(0)).name();
        String dst = ((Operand.Register) insn.operands().get(1)).name();
        // Sign-extend byte to long: shl 56 then sar 56 (Simple uses 64-bit longs)
        var c56 = graph.add(SimpleIR.constant(56));
        for (int i = 0; i < 8; i++) {
            var srcNode = getOrCreateParam(graph, regNodes, src, i);
            var shl = graph.add(SimpleIR.typed(new ShlNode(srcNode, c56)));
            var sar = graph.add(SimpleIR.typed(new SarNode(shl, c56)));
            regNodes.put(dst + "[" + i + "]", sar);
        }
    }

    // --- aarch64 IR construction ---

    private static NicaGraph buildAArch64(AssemblySnippet snippet, MachineState state) {
        var graph = new NicaGraph();
        var regNodes = new HashMap<String, Node>();

        for (var insn : snippet.instructions()) {
            boolean isVec = insn.operands().stream().anyMatch(
                op -> op instanceof Operand.Register r && r.isVector());

            switch (insn.mnemonic()) {
                case "ldr" -> buildLdrAArch64(insn, graph, regNodes);
                case "sshll" -> buildSshll(insn, graph, regNodes);
                case "mul" -> { if (isVec) buildVecBinop(insn, graph, regNodes, IRBuilder::makeMul); }
                case "mla" -> { if (isVec) buildMlaVec(insn, graph, regNodes); }
                case "add" -> { if (isVec) buildVecBinop(insn, graph, regNodes, IRBuilder::makeAdd); }
                case "shl" -> { if (isVec) buildVecShift(insn, graph, regNodes, IRBuilder::makeShl); }
                case "sshr" -> { if (isVec) buildVecShift(insn, graph, regNodes, IRBuilder::makeSar); }
                case "eor3" -> buildEor3(insn, graph, regNodes);
                default -> {}
            }
        }

        setResultFromLastXor(graph, regNodes, 4);
        return graph;
    }

    private static void buildLdrAArch64(Instruction insn, NicaGraph graph,
                                         Map<String, Node> regNodes) {
        var dst = (Operand.Register) insn.operands().get(0);
        if (dst.name().startsWith("s")) {
            for (int i = 0; i < 4; i++) {
                var param = graph.addParam(dst.name(), i);
                regNodes.put(dst.name() + "[" + i + "]", param);
            }
        }
    }

    private static void buildSshll(Instruction insn, NicaGraph graph,
                                    Map<String, Node> regNodes) {
        var dst = (Operand.Register) insn.operands().get(0);
        var src = (Operand.Register) insn.operands().get(1);
        String srcArr = src.arrangement();
        int srcLanes = parseLanes(srcArr);

        int shiftAmount;
        if (srcArr.endsWith("b")) shiftAmount = 56;      // byte → shl/sar 56 (64-bit)
        else if (srcArr.endsWith("h")) shiftAmount = 48;  // halfword → shl/sar 48 (64-bit)
        else throw new UnsupportedOperationException("sshll from: " + srcArr);

        var shiftConst = graph.add(SimpleIR.constant(shiftAmount));
        for (int i = 0; i < srcLanes; i++) {
            var srcNode = getOrCreateParam(graph, regNodes, src.name(), i);
            var shl = graph.add(SimpleIR.typed(new ShlNode(srcNode, shiftConst)));
            var sar = graph.add(SimpleIR.typed(new SarNode(shl, shiftConst)));
            regNodes.put(dst.name() + "[" + i + "]", sar);
        }
    }

    private static void buildMlaVec(Instruction insn, NicaGraph graph,
                                     Map<String, Node> regNodes) {
        var dst = (Operand.Register) insn.operands().get(0);
        var a = (Operand.Register) insn.operands().get(1);
        var b = (Operand.Register) insn.operands().get(2);
        int lanes = parseLanes(dst.arrangement());
        for (int i = 0; i < lanes; i++) {
            var acc = getOrCreateParam(graph, regNodes, dst.name(), i);
            var nodeA = getOrCreateParam(graph, regNodes, a.name(), i);
            var nodeB = getOrCreateParam(graph, regNodes, b.name(), i);
            var mul = graph.add(SimpleIR.typed(new MulNode(nodeA, nodeB)));
            var add = graph.add(SimpleIR.typed(new AddNode(acc, mul)));
            regNodes.put(dst.name() + "[" + i + "]", add);
        }
    }

    private static void buildEor3(Instruction insn, NicaGraph graph,
                                   Map<String, Node> regNodes) {
        var dst = (Operand.Register) insn.operands().get(0);
        var a = (Operand.Register) insn.operands().get(1);
        var b = (Operand.Register) insn.operands().get(2);
        var c = (Operand.Register) insn.operands().get(3);
        for (int i = 0; i < 4; i++) {
            var nodeA = getOrCreateParam(graph, regNodes, a.name(), i);
            var nodeB = getOrCreateParam(graph, regNodes, b.name(), i);
            var nodeC = getOrCreateParam(graph, regNodes, c.name(), i);
            var xor1 = graph.add(SimpleIR.typed(new XorNode(nodeA, nodeB)));
            var xor2 = graph.add(SimpleIR.typed(new XorNode(xor1, nodeC)));
            regNodes.put(dst.name() + "[" + i + "]", xor2);
        }
    }

    // --- Generic builders for binops and shifts ---

    @FunctionalInterface
    private interface BinopFactory { Node create(Node left, Node right); }

    private static void buildBinop(Instruction insn, NicaGraph graph,
                                    Map<String, Node> regNodes, int lanes, BinopFactory factory) {
        String a = ((Operand.Register) insn.operands().get(0)).name();
        String b = ((Operand.Register) insn.operands().get(1)).name();
        String dst = ((Operand.Register) insn.operands().get(2)).name();
        for (int i = 0; i < lanes; i++) {
            var nodeA = getOrCreateParam(graph, regNodes, a, i);
            var nodeB = getOrCreateParam(graph, regNodes, b, i);
            var result = graph.add(SimpleIR.typed(factory.create(nodeA, nodeB)));
            regNodes.put(dst + "[" + i + "]", result);
        }
    }

    private static void buildVecBinop(Instruction insn, NicaGraph graph,
                                       Map<String, Node> regNodes, BinopFactory factory) {
        var dst = (Operand.Register) insn.operands().get(0);
        var a = (Operand.Register) insn.operands().get(1);
        var b = (Operand.Register) insn.operands().get(2);
        int lanes = parseLanes(dst.arrangement());
        for (int i = 0; i < lanes; i++) {
            var nodeA = getOrCreateParam(graph, regNodes, a.name(), i);
            var nodeB = getOrCreateParam(graph, regNodes, b.name(), i);
            var result = graph.add(SimpleIR.typed(factory.create(nodeA, nodeB)));
            regNodes.put(dst.name() + "[" + i + "]", result);
        }
    }

    private static void buildShift(Instruction insn, NicaGraph graph,
                                    Map<String, Node> regNodes, int lanes, BinopFactory factory) {
        int shift = (int) ((Operand.Immediate) insn.operands().get(0)).value();
        // Adjust shift for 64-bit: assembly operates on 32-bit lanes,
        // but Simple IR uses 64-bit longs. Add 32 to the shift amount.
        int adjustedShift = shift + 32;
        String src = ((Operand.Register) insn.operands().get(1)).name();
        String dst = ((Operand.Register) insn.operands().get(2)).name();
        var shiftConst = graph.add(SimpleIR.constant(adjustedShift));
        for (int i = 0; i < lanes; i++) {
            var srcNode = getOrCreateParam(graph, regNodes, src, i);
            var result = graph.add(SimpleIR.typed(factory.create(srcNode, shiftConst)));
            regNodes.put(dst + "[" + i + "]", result);
        }
    }

    private static void buildVecShift(Instruction insn, NicaGraph graph,
                                       Map<String, Node> regNodes, BinopFactory factory) {
        var dst = (Operand.Register) insn.operands().get(0);
        var src = (Operand.Register) insn.operands().get(1);
        int shift = (int) ((Operand.Immediate) insn.operands().get(2)).value();
        // Adjust shift for 64-bit (assembly uses 32-bit lanes)
        int adjustedShift = shift + 32;
        int lanes = parseLanes(dst.arrangement());
        var shiftConst = graph.add(SimpleIR.constant(adjustedShift));
        for (int i = 0; i < lanes; i++) {
            var srcNode = getOrCreateParam(graph, regNodes, src.name(), i);
            var result = graph.add(SimpleIR.typed(factory.create(srcNode, shiftConst)));
            regNodes.put(dst.name() + "[" + i + "]", result);
        }
    }

    // --- Node factories ---

    private static Node makeAdd(Node l, Node r) { return new AddNode(l, r); }
    private static Node makeMul(Node l, Node r) { return new MulNode(l, r); }
    private static Node makeXor(Node l, Node r) { return new XorNode(l, r); }
    private static Node makeShl(Node l, Node r) { return new ShlNode(l, r); }
    private static Node makeSar(Node l, Node r) { return new SarNode(l, r); }

    // --- Helpers ---

    private static Node getOrCreateParam(NicaGraph graph,
                                          Map<String, Node> regNodes,
                                          String regName, int lane) {
        String key = regName + "[" + lane + "]";
        return regNodes.computeIfAbsent(key, _ -> {
            var param = graph.addParam(regName, lane);
            SimpleIR.typed(param);
            return param;
        });
    }

    private static void setResultFromLastXor(NicaGraph graph,
                                              Map<String, Node> regNodes,
                                              int lanes) {
        String lastXorReg = null;
        for (var entry : regNodes.entrySet()) {
            if (entry.getValue() instanceof XorNode) {
                String key = entry.getKey();
                lastXorReg = key.substring(0, key.indexOf('['));
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
        var sb = new StringBuilder();
        for (char c : arrangement.toCharArray()) {
            if (Character.isDigit(c)) sb.append(c);
            else break;
        }
        return Integer.parseInt(sb.toString());
    }
}

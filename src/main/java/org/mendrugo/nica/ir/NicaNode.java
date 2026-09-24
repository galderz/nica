package org.mendrugo.nica.ir;

import java.util.List;

/**
 * Architecture-agnostic IR node for Nica.
 *
 * <p>Models the same semantic operations as Sea of Nodes / Simple
 * (Add, Mul, Xor, Shl, Sar, Load, Convert) without requiring Simple's
 * CodeGen initialization. Each node holds its inputs and can be evaluated
 * to produce a concrete value.</p>
 *
 * <p>Designed for Java 27 pattern matching: use {@code switch} with
 * type patterns and guards over this sealed hierarchy.</p>
 */
public sealed interface NicaNode {

    /** Unique node ID for debugging and printing. */
    int id();

    /** Human-readable label (e.g. "Add", "Mul", "Load"). */
    String label();

    /** Input nodes. */
    List<NicaNode> inputs();

    /** Evaluate this node given concrete lane values from inputs. */
    int evaluate(int[] inputValues);

    // --- Concrete node types ---

    /** A constant integer value. */
    record Constant(int id, int value) implements NicaNode {
        @Override public String label() { return "#" + value; }
        @Override public List<NicaNode> inputs() { return List.of(); }
        @Override public int evaluate(int[] inputValues) { return value; }
    }

    /** A named input parameter (e.g. a byte loaded from an array). */
    record Param(int id, String name) implements NicaNode {
        @Override public String label() { return name; }
        @Override public List<NicaNode> inputs() { return List.of(); }
        @Override public int evaluate(int[] inputValues) {
            throw new UnsupportedOperationException("Param must be bound before evaluation");
        }
    }

    /** Load a value from memory (base + offset). */
    record Load(int id, String description, NicaNode base, NicaNode offset) implements NicaNode {
        @Override public String label() { return "Load"; }
        @Override public List<NicaNode> inputs() { return List.of(base, offset); }
        @Override public int evaluate(int[] inputValues) {
            throw new UnsupportedOperationException("Load must be resolved before evaluation");
        }
    }

    /** Sign-extend: convert a narrower value to a wider one (e.g. byte → int). */
    record Convert(int id, int fromBits, int toBits, NicaNode input) implements NicaNode {
        @Override public String label() { return "Convert_" + fromBits + "→" + toBits; }
        @Override public List<NicaNode> inputs() { return List.of(input); }
        @Override public int evaluate(int[] inputValues) {
            int v = inputValues[0];
            // Sign-extend from fromBits
            int shift = 32 - fromBits;
            return (v << shift) >> shift;
        }
    }

    /** Addition: a + b. */
    record Add(int id, NicaNode left, NicaNode right) implements NicaNode {
        @Override public String label() { return "Add"; }
        @Override public List<NicaNode> inputs() { return List.of(left, right); }
        @Override public int evaluate(int[] inputValues) { return inputValues[0] + inputValues[1]; }
    }

    /** Multiplication: a * b. */
    record Mul(int id, NicaNode left, NicaNode right) implements NicaNode {
        @Override public String label() { return "Mul"; }
        @Override public List<NicaNode> inputs() { return List.of(left, right); }
        @Override public int evaluate(int[] inputValues) { return inputValues[0] * inputValues[1]; }
    }

    /** XOR: a ^ b. */
    record Xor(int id, NicaNode left, NicaNode right) implements NicaNode {
        @Override public String label() { return "Xor"; }
        @Override public List<NicaNode> inputs() { return List.of(left, right); }
        @Override public int evaluate(int[] inputValues) { return inputValues[0] ^ inputValues[1]; }
    }

    /** Left shift: value << amount. */
    record Shl(int id, NicaNode value, NicaNode amount) implements NicaNode {
        @Override public String label() { return "Shl"; }
        @Override public List<NicaNode> inputs() { return List.of(value, amount); }
        @Override public int evaluate(int[] inputValues) { return inputValues[0] << inputValues[1]; }
    }

    /** Arithmetic right shift: value >> amount (sign-extending). */
    record Sar(int id, NicaNode value, NicaNode amount) implements NicaNode {
        @Override public String label() { return "Sar"; }
        @Override public List<NicaNode> inputs() { return List.of(value, amount); }
        @Override public int evaluate(int[] inputValues) { return inputValues[0] >> inputValues[1]; }
    }

    /** Multiply-accumulate: acc + a * b. */
    record Mla(int id, NicaNode acc, NicaNode left, NicaNode right) implements NicaNode {
        @Override public String label() { return "Mla"; }
        @Override public List<NicaNode> inputs() { return List.of(acc, left, right); }
        @Override public int evaluate(int[] inputValues) {
            return inputValues[0] + inputValues[1] * inputValues[2];
        }
    }
}

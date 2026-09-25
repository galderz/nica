package org.mendrugo.nica.cheatsheet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mendrugo.nica.asm.Architecture;

import static org.junit.jupiter.api.Assertions.*;

class InstructionDescriptionsTest {

    // --- x86_64: every mnemonic from the example snippets ---

    @ParameterizedTest
    @ValueSource(strings = {
        "vmovq", "vpmovsxbd", "vpmulld", "vpaddd", "vpslld", "vpsrad", "vpxor",
        "leal", "cmpl", "jl",
        "leaq", "cmpq", "jbe", "movq", "movl", "nop", "testl", "je",
        "addq", "retq", "jle", "callq", "jb"
    })
    void x86MnemonicHasDescription(String mnemonic) {
        var desc = InstructionDescriptions.lookup(mnemonic, Architecture.X86_64);
        assertFalse(desc.isUnsupported(), "Missing description for x86_64: " + mnemonic);
        assertFalse(desc.summary().isBlank());
        assertFalse(desc.javaEquivalent().isBlank());
    }

    // --- aarch64: every mnemonic from the example snippet ---

    @ParameterizedTest
    @ValueSource(strings = {
        "sxtw", "add", "ldr", "sshll", "mul", "mla", "shl", "sshr", "eor3",
        "cmp", "b.lt"
    })
    void aarch64MnemonicHasDescription(String mnemonic) {
        var desc = InstructionDescriptions.lookup(mnemonic, Architecture.AARCH64);
        assertFalse(desc.isUnsupported(), "Missing description for aarch64: " + mnemonic);
        assertFalse(desc.summary().isBlank());
        assertFalse(desc.javaEquivalent().isBlank());
    }

    // --- Unknown mnemonic returns UNSUPPORTED ---

    @Test
    void unknownMnemonicReturnsUnsupported() {
        var desc = InstructionDescriptions.lookup("vfmadd231ps", Architecture.X86_64);
        assertTrue(desc.isUnsupported());
    }

    @Test
    void unsupportedSentinelIdentity() {
        assertSame(InstructionDescription.UNSUPPORTED,
            InstructionDescriptions.lookup("nonexistent", Architecture.AARCH64));
    }

    // --- Spot-check specific descriptions ---

    @Test
    void vpmovsxbdDescriptionMentionsSignExtend() {
        var desc = InstructionDescriptions.lookup("vpmovsxbd", Architecture.X86_64);
        assertTrue(desc.summary().toLowerCase().contains("sign-extend"));
        assertTrue(desc.javaEquivalent().contains("(byte)"));
    }

    @Test
    void sshllDescriptionMentionsWiden() {
        var desc = InstructionDescriptions.lookup("sshll", Architecture.AARCH64);
        assertTrue(desc.summary().toLowerCase().contains("widen"));
    }

    @Test
    void eor3DescriptionMentionsXor() {
        var desc = InstructionDescriptions.lookup("eor3", Architecture.AARCH64);
        assertTrue(desc.javaEquivalent().contains("^"));
    }

    @Test
    void movlDescriptionMentionsZeroExtend() {
        var desc = InstructionDescriptions.lookup("movl", Architecture.X86_64);
        assertTrue(desc.summary().toLowerCase().contains("zero-extend"));
    }
}

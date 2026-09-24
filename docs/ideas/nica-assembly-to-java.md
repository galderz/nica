# Nica — Assembly-to-Java Translator for JIT Output

## Problem Statement

How might we make disassembled JIT compiler output (HotSpot C2, GraalVM native image) immediately comprehensible to Java developers by translating architecture-specific assembly into runnable, verified Java code — with multiple levels of abstraction?

## Recommended Direction

Nica is a **triple-view assembly translator** that takes disassembled x86_64 or aarch64 snippets from JIT compilers and produces three complementary Java representations:

1. **Cheat Sheet** — the original assembly annotated inline with one-line Java/English comments explaining each instruction. Zero transformation, maximum accessibility. This is what you reach for when you just need to look up what `vpmovsxbd` does.

2. **Literal** — a faithful, runnable Java class that models every register as a variable and every instruction as a Java statement. SIMD registers become `int[]` arrays. The output reads like a CPU simulator: tedious but exact.

3. **Explained** — a high-level runnable Java class produced by recognizing known patterns (vectorized byte operations, multiply-accumulate, xor-reduce, etc.) and replacing them with readable abstractions. When no pattern matches, falls back to literal translation.

The three views share a common backbone: an **architecture-agnostic IR** built from [Sea of Nodes / Simple](https://github.com/SeaOfNodes/Simple) (chapter 25). The pipeline is:

```
Assembly Text → Parser → Arch-Specific Instructions → Simple IR Graph → Three Views
                                                                         ├── Cheat Sheet (annotated asm)
                                                                         ├── Literal (register-level Java)
                                                                         └── Explained (pattern-recognized Java)
```

SIMD operations are modeled as N scalar operations over the Simple IR for the MVP. This avoids extending Simple's node system and keeps the IR compatible with Simple's existing debugging tools (IRPrinter, GraphViewer, GraphJson). The Explained view is where SIMD gets reconstructed into readable vector operations.

Simple has no published Maven artifacts. It will be built locally as a dependency (`mvn install` on the Simple checkout). CI will checkout and build Simple before building/testing Nica.

### Why this architecture

- **Simple IR as the pivot** means x86 and aarch64 snippets that do the same computation produce the same IR graph. Cross-architecture comparison becomes possible for free.
- **Triple view** serves three audiences: the curious app developer (cheat sheet), the compiler engineer debugging register allocation (literal), and the engineer asking "what did the compiler decide to do here?" (explained).
- **Runnable Java output with tests** means users don't have to trust the tool — they can verify it. The Literal and Explained views must produce identical results for the same inputs, and that equivalence is itself a test.

## Key Assumptions to Validate

- [ ] **Assembly formats are parseable into a common instruction model.** Three input flavors exist: HotSpot capstone disassembly, HotSpot fast-debug disassembly, and `perf annotate` output. They differ in address format, annotation style, and metadata. *Validate by: successfully parsing all three example snippets from the spec into the same internal instruction representation.*

- [ ] **Simple's IR (chapter 25) can represent the semantics of target instructions without modification.** The core ops map cleanly (Add, Mul, Xor, Shl, Sar, Load, If, Loop). SIMD is modeled as N scalar operations. *Validate by: building a Simple IR graph for the x86 example, evaluating it, and producing the correct numeric result.*

- [ ] **Instruction coverage grows sub-linearly.** After implementing the ~15 instructions needed for the example snippets, new real-world snippets should mostly reuse existing instruction implementations. *Validate by: after MVP, try 5 new real JIT snippets and count how many new instruction implementations are needed.*

- [ ] **Literal ↔ Explained equivalence is testable.** Both views must produce identical outputs for the same inputs. *Validate by: generating both views for each example snippet and asserting numeric equivalence in JUnit tests.*

## MVP Scope

**In:**

- **Assembly parser** supporting two input formats (a third will come later):
  - HotSpot fast-debug disassembly (x86_64 and aarch64) — provided in examples
  - `perf annotate` output (x86_64, from GraalVM native image) — provided in examples
  - *(Future: HotSpot release build capstone disassembly — no examples yet, deferred)*
- **Instruction semantics** for only the instructions appearing in the three example snippets:
  - x86_64: `vmovq`, `vpmovsxbd`, `vpmulld`, `vpaddd`, `vpslld`, `vpsrad`, `vpxor`, `leal`, `cmpl`, `jl`, `leaq`, `cmpq`, `jbe`, `movq`, `movl`, `nop`, `testl`, `je`, `addq`, `retq`, `jle`, `callq`
  - aarch64: `sxtw`, `add`, `ldr`, `sshll`, `mul`, `mla`, `shl`, `sshr`, `eor3`, `cmp`, `b.lt`
- **Simple IR graph construction** from parsed instructions using chapter 25 nodes (AddNode, MulNode, XorNode, ShlNode, SarNode, LoadNode, ConvertNode, LoopNode, IfNode, ConstantNode)
- **Three output generators:**
  - Cheat Sheet: annotated assembly (comments only, no transformation)
  - Literal: runnable Java with register-as-variable modeling, SIMD-as-int-array
  - Explained: pattern-matched Java (starting with 2-3 recipes: "vectorized sign-extend + multiply-accumulate", "shift-mask-xor-reduce", "array bounds check with exception")
- **Equivalence tests** asserting Literal and Explained produce identical results
- **CLI entry point:** `nica <assembly-file> [--source <java-file> --lines <range>]`
- **Java 27, Maven build, `org.mendrugo.nica` package**
- **GitHub Actions CI** that checks out and builds Simple, then builds and tests Nica

**Out:**

- GUI or web interface
- Interactive/REPL mode (variation 4 — interesting but not MVP)
- Inverse direction: Java → expected assembly (variation 5 — much harder problem)
- Full x86_64 or aarch64 instruction set coverage
- SIMD-aware IR nodes in Simple (MVP uses N scalar ops)
- GraphViewer integration (would be nice, but not core)
- Support for non-JIT assembly (system libraries, kernel code)
- Floating-point instruction support (the example snippets are integer-only)

## Not Doing (and Why)

- **Full ISA coverage** — The x86_64 instruction set alone has thousands of instructions. Covering them all up front would take months and most would never be exercised. Grow coverage demand-driven: when a user brings a snippet with an unsupported instruction, add it then. The tool should fail clearly on unknown instructions, not silently produce wrong output.

- **SIMD-native IR nodes** — Extending Simple's IR with vector-width semantics would be cleaner long-term but adds coupling and complexity. Modeling SIMD as N scalar operations is correct, composable with existing Simple tooling, and sufficient for the Explained view to reconstruct the vector intent.

- **Interactive mode** — An assembly reading companion (load, ask questions, annotate) is a compelling UX but requires a fundamentally different architecture. The batch translator is the foundation; interactive can layer on top later.

- **Java → assembly prediction** — This inverts the entire problem. It requires modeling the JIT compiler's decision-making, not just its output. Extraordinarily hard, separate project.

- **GraphViewer integration** — Simple's web-based graph viewer is powerful for debugging IR construction, and Nica should adopt Simple's `IRPrinter` for textual debugging. But wiring up the WebSocket-based GraphViewer is polish, not core.

- **Floating-point instructions** — The example snippets are integer/byte arithmetic. FP support (SSE/AVX scalar, NEON FP) is a natural extension but not needed for MVP validation.

## Open Questions

- **~~How should unknown instructions be handled?~~** Decided: (a) fail hard for Literal/Explained (correctness matters), (b) emit `// UNSUPPORTED: <instruction>` for Cheat Sheet (best-effort is fine).

- **~~Should Simple be a Git submodule or a pre-built dependency?~~** Decided: Git submodule. See contribution guide for the workflow.

- **How to handle register aliasing in the Java output?** x86 registers alias (`rsi`/`esi`/`si`/`sil` are the same physical register at different widths). The x86_64 rules:
  - **32-bit writes** (`movl` to `%eax`) **zero-extend to 64 bits** — `rax` becomes `0x00000000_xxxxxxxx`. Clean and simple.
  - **16-bit writes** (`movw` to `%ax`) **preserve upper bits** — only low 16 bits change. Requires mask-and-merge: `rax = (rax & ~0xFFFFL) | (val & 0xFFFFL)`.
  - **8-bit writes** (`movb` to `%al`) **preserve upper bits** — same mask-and-merge pattern.

  **Key observation:** JIT compilers (C2, Graal) almost exclusively emit 32-bit and 64-bit operations. All three example snippets confirm this — no 16-bit or 8-bit register writes appear. Sub-32-bit partial writes are a hand-written assembly concern, not a JIT output concern.

  **Decision: Option B (one `long` variable per physical register, explicit width conversions inline) for MVP.** The 32→64 zero-extension is the only aliasing case that matters for JIT output, and it's handled naturally:
    ```java
    // movl 0x4(%rdi), %eax  →  32-bit write zero-extends to 64
    long rax = Integer.toUnsignedLong(memory.loadInt(rdi + 0x4));
    // leaq (%r14,%rax,8), %rcx
    long rcx = r14 + rax * 8;
    ```
  If sub-32-bit register writes are encountered in the future, the tool should fail clearly (same as unknown instructions) rather than silently produce wrong output. This constraint is documented.

- **~~What's the verification story for users?~~** Decided: all three approaches, clearly documented:
  1. Equivalence tests in the repo prove Literal ≡ Explained for known snippets
  2. Users can run the generated Java with known inputs and compare against the actual compiled code
  3. The Cheat Sheet view enables manual instruction-by-instruction verification

- **Recipe/pattern extensibility:** Hardcoded for MVP. The architecture should make adding new recipes straightforward via a pattern matcher over the Simple IR graph. Inspiration: HotSpot's C++ IR node pattern matcher for instruction selection, but less cumbersome. Simple targets Java 21 with plain class hierarchies (no sealed classes, no records), and **no bridge or wrapper layer is needed** — Java 27's `switch` type patterns with guards work directly on Simple's Node classes:
    ```java
    String matchPattern(Node node) -> switch (node) {
        case SarNode sar when sar.in(1) instanceof ShlNode shl
                          && shl.in(2) instanceof ConstantNode c1
                          && sar.in(2) instanceof ConstantNode c2
                          && c1._type instanceof TypeInteger t1
                          && c2._type instanceof TypeInteger t2
                          && t1.value() == t2.value()
            -> "sign-extend from %d bits".formatted(64 - t1.value());
        case XorNode xor when xor.in(1) instanceof XorNode inner
            -> "chained xor-reduce";
        default -> null;  // no pattern matched, use literal
    };
    ```
  You lose compile-time exhaustiveness (which you'd never get matching over graph *shapes* anyway), but the ergonomics are dramatically better than C++ macro-based matching or Java visitor patterns.

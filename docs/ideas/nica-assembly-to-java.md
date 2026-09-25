# Nica — Assembly-to-Java Translator for JIT Output

## Problem Statement

How might we make disassembled JIT compiler output (HotSpot C2, GraalVM native image)
immediately comprehensible to Java developers by translating architecture-specific assembly into runnable,
verified Java code — with multiple levels of abstraction?

## Recommended Direction

Nica is a **triple-view assembly translator** that takes disassembled x86_64 or aarch64 snippets from JIT compilers
and produces three complementary Java representations:

1. **Cheat Sheet** — the original assembly annotated inline with one-line Java/English comments explaining each instruction.
Zero transformation, maximum accessibility.
This is what you reach for when you just need to look up what `vpmovsxbd` does.

2. **Literal** — a faithful, runnable Java class that models every register as a variable and every instruction as a Java statement.
SIMD registers become `int[]` arrays.
The output reads like a CPU simulator: tedious but exact.

3. **Explained** — a high-level runnable Java class produced by recognizing known patterns
(vectorized byte operations, multiply-accumulate, xor-reduce, etc.)
and replacing them with readable abstractions.
When no pattern matches, falls back to literal translation.

The three views share a common backbone: an **architecture-agnostic IR** built from
[Sea of Nodes / Simple](https://github.com/SeaOfNodes/Simple)
(chapter 14).
The pipeline is:

```
Assembly Text → Parser → Arch-Specific Instructions → Simple IR Graph → Three Views
                                                                         ├── Cheat Sheet (annotated asm)
                                                                         ├── Literal (register-level Java)
                                                                         └── Explained (pattern-recognized Java)
```

SIMD operations are modeled as N scalar operations over the Simple IR for the MVP.
This avoids extending Simple's node system and keeps the IR compatible with Simple's existing debugging tools (IRPrinter).
The Explained view is where SIMD gets reconstructed into readable vector operations.

Simple has no published Maven artifacts.
It will be built locally as a dependency (`mvn install` on the Simple checkout).
CI will checkout and build Simple before building/testing Nica.

### Why this architecture

- **Simple IR as the pivot** means x86 and aarch64 snippets that do the same computation produce the same IR graph.
Cross-architecture comparison becomes possible for free.
- **Triple view** serves three audiences: the curious app developer (cheat sheet),
the compiler engineer debugging register allocation (literal),
and the engineer asking "what did the compiler decide to do here?" (explained).
- **Runnable Java output with tests** means users don't have to trust the tool — they can verify it.
The Literal and Explained views must produce identical results for the same inputs,
and that equivalence is itself a test.

## Key Assumptions to Validate

- [x] **Assembly formats are parseable into a common instruction model.**
Two input flavors implemented: HotSpot fast-debug disassembly and `perf annotate` output.
They differ in address format, annotation style, and metadata.
*Validated: both formats parse into the same `Instruction`/`Operand`/`AssemblySnippet` model.
Both x86 formats use AT&T syntax (not Intel), which simplified parsing.*

- [x] **Simple's IR can represent the semantics of target instructions without modification.**
The core ops map cleanly (Add, Mul, Xor, Shl, Sar). SIMD is modeled as N scalar operations.
*Validated: chapter 14 nodes used successfully.
However, Simple uses 64-bit `long` arithmetic while SIMD operates on 32-bit `int` lanes,
so shift amounts must be adjusted (see Implementation Lessons below).*

- [ ] **Instruction coverage grows sub-linearly.**
After implementing the ~15 instructions needed for the example snippets,
new real-world snippets should mostly reuse existing instruction implementations.
*Not yet validated — requires post-MVP testing with new real snippets.*

- [x] **Literal ↔ Explained equivalence is testable.**
Both views produce identical outputs for the same inputs.
*Validated: `EquivalenceTest` compiles both views in-memory via `javax.tools.JavaCompiler`,
runs them with 3 different data sets, and asserts array equality.*

## MVP Scope

**In:**

- **Assembly parser** supporting two input formats (a third will come later).
  Only AT&T syntax is supported for x86_64; Intel syntax is not supported.
  AT&T is the default output of HotSpot's `PrintAssembly` and `perf annotate`.
  - HotSpot fast-debug disassembly (x86_64 AT&T and aarch64) — provided in examples
  - `perf annotate` output (x86_64 AT&T, from GraalVM native image) — provided in examples
  - *(Future: HotSpot release build capstone disassembly — no examples yet, deferred)*
- **Instruction semantics** for only the instructions appearing in the three example snippets:
  - x86_64: `vmovq`, `vpmovsxbd`, `vpmulld`, `vpaddd`, `vpslld`, `vpsrad`, `vpxor`, `leal`, `cmpl`, `jl`, `leaq`, `cmpq`, `jbe`, `movq`, `movl`, `nop`, `testl`, `je`, `addq`, `retq`, `jle`, `callq`
  - aarch64: `sxtw`, `add`, `ldr`, `sshll`, `mul`, `mla`, `shl`, `sshr`, `eor3`, `cmp`, `b.lt`
- **Simple IR graph construction** from parsed instructions using chapter 14 nodes
  (AddNode, MulNode, XorNode, ShlNode, SarNode, ConstantNode, plus custom ParamNode for bindable input parameters)
- **Three output generators:**
  - Cheat Sheet: annotated assembly (comments only, no transformation)
  - Literal: runnable Java with register-as-variable modeling, SIMD-as-int-array
  - Explained: pattern-matched Java (starting with 2-3 recipes:
    "vectorized sign-extend + multiply-accumulate", "shift-mask-xor-reduce", "array bounds check with exception")
- **Equivalence tests** asserting Literal and Explained produce identical results
- **CLI entry point:** `nica <assembly-file> [--view cheat-sheet|literal|explained] [--arch x86_64|aarch64] [-o output.java]`
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

- **Full ISA coverage** —
The x86_64 instruction set alone has thousands of instructions.
Covering them all up front would take months and most would never be exercised.
Grow coverage demand-driven: when a user brings a snippet with an unsupported instruction, add it then.
The tool should fail clearly on unknown instructions,
not silently produce wrong output.

- **SIMD-native IR nodes** —
Extending Simple's IR with vector-width semantics would be cleaner long-term but adds coupling and complexity.
Modeling SIMD as N scalar operations is correct,
composable with existing Simple tooling,
and sufficient for the Explained view to reconstruct the vector intent.

- **Interactive mode** —
An assembly reading companion (load, ask questions, annotate)
is a compelling UX but requires a fundamentally different architecture.
The batch translator is the foundation; interactive can layer on top later.

- **Java → assembly prediction** — This inverts the entire problem.
It requires modeling the JIT compiler's decision-making, not just its output.
Extraordinarily hard, separate project.

- **GraphViewer integration** —
Simple's web-based graph viewer is powerful for debugging IR construction,
and Nica should adopt Simple's `IRPrinter` for textual debugging.
But wiring up the WebSocket-based GraphViewer is polish, not core.

- **Floating-point instructions** —
The example snippets are integer/byte arithmetic.
FP support (SSE/AVX scalar, NEON FP) is a natural extension but not needed for MVP validation.

- **Intel syntax for x86_64** —
Only AT&T syntax is supported.
Both HotSpot `PrintAssembly` and `perf annotate` default to AT&T, so there is no immediate need.
Supporting Intel syntax would require a separate operand parser
(destination-first operand order, no `%`/`$` prefixes, square-bracket memory references).

## Open Questions

- **~~How should unknown instructions be handled?~~**
Decided: (a) fail hard for Literal/Explained (correctness matters),
(b) emit `// UNSUPPORTED: <instruction>` for Cheat Sheet (best-effort is fine).

- **~~Should Simple be a Git submodule or a pre-built dependency?~~**
Decided: Git submodule. See contribution guide for the workflow.

- **How to handle register aliasing in the Java output?**
  x86 registers alias (`rsi`/`esi`/`si`/`sil` are the same physical register at different widths). The x86_64 rules:
  - **32-bit writes** (`movl` to `%eax`) **zero-extend to 64 bits** — `rax` becomes `0x00000000_xxxxxxxx`.
    Clean and simple.
  - **16-bit writes** (`movw` to `%ax`) **preserve upper bits** — only low 16 bits change.
    Requires mask-and-merge: `rax = (rax & ~0xFFFFL) | (val & 0xFFFFL)`.
  - **8-bit writes** (`movb` to `%al`) **preserve upper bits** — same mask-and-merge pattern.

  **Key observation:** JIT compilers (C2, Graal) almost exclusively emit 32-bit and 64-bit operations.
All three example snippets confirm this — no 16-bit or 8-bit register writes appear.
Sub-32-bit partial writes are a handwritten assembly concern, not a JIT output concern.

  **Decision: Option B (one `long` variable per physical register, explicit width conversions inline) for MVP.**
  The 32→64 zero-extension is the only aliasing case that matters for JIT output, and it's handled naturally:
    ```java
    // movl 0x4(%rdi), %eax  →  32-bit write zero-extends to 64
    long rax = Integer.toUnsignedLong(memory.loadInt(rdi + 0x4));
    // leaq (%r14,%rax,8), %rcx
    long rcx = r14 + rax * 8;
    ```
  If sub-32-bit register writes are encountered in the future,
  the tool should fail clearly (same as unknown instructions)
  rather than silently produce wrong output.
  This constraint is documented.

- **~~What's the verification story for users?~~**
  Decided: all three approaches, clearly documented:
  1. Equivalence tests in the repo prove Literal ≡ Explained for known snippets
  2. Users can run the generated Java with known inputs and compare against the actual compiled code
  3. The Cheat Sheet view enables manual instruction-by-instruction verification

- **Recipe/pattern extensibility:** Hardcoded for MVP.
  The architecture should make adding new recipes straightforward via a pattern matcher over the Simple IR graph.
  Inspiration: HotSpot's C++ IR node pattern matcher for instruction selection, but less cumbersome.
  Simple targets Java 21 with plain class hierarchies (no sealed classes, no records),
  and **no bridge or wrapper layer is needed** —
  Java 27's `switch` type patterns with guards work directly on Simple's Node classes:
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
  You lose compile-time exhaustiveness (which you'd never get matching over graph *shapes* anyway),
  but the ergonomics are dramatically better than C++ macro-based matching or Java visitor patterns.

## Implementation Lessons

Key decisions and discoveries made during the MVP implementation.
These update or supersede earlier assumptions in this document.

### Simple chapter 14, not chapter 25

The original plan specified chapter 25 (the most complete).
During implementation, chapter 25's `CodeGen` class proved too heavyweight —
it requires complex static initialization with a full parser/compiler pipeline just to construct a single `ConstantNode`.
An initial attempt at Nica-specific IR nodes (records, sealed interfaces) was clean but lost access to Simple's debugging utilities.

Chapter 14 is the sweet spot:
it is the first chapter with all the node types Nica needs (Add, Mul, Xor, Shl, Sar, Load, If, Loop, Bool, Constant)
plus `IRPrinter` for debugging,
without the `CodeGen` dependency.
Bootstrap requires just 3 lines:

```java
Node.reset();
Node._disablePeephole = true;
Parser.START = new StartNode(new Type[]{Type.CONTROL, TypeInteger.BOT});
```

Peepholes must be disabled to prevent Simple's optimizer from constant-folding the graph during construction.

### Three Simple upstream issues discovered

Integrating Simple as a library (rather than using it as a compiler) exposed three issues,
documented with standalone reproducers in `docs/simple-issues/`:

1. **`_print1` is package-private** (chapters 2–24):
`Node._print1(StringBuilder, BitSet)` has default visibility,
preventing `Node` subclasses in other packages from compiling.
Chapter 25 fixed this to `public`, but earlier chapters haven't been backported.
Workaround: place `ParamNode` in the `com.seaofnodes.simple.node` package.

2. **`Node.equals()`/`hashCode()` implement structural equality**:
These are `final` and compare by class + inputs,
which is correct for GVN (Global Value Numbering) inside the compiler,
but causes `HashMap<Node, V>` to silently collapse distinct nodes with identical structure.
Additionally, the extension points `eq()` and `hash()` are package-private,
so subclasses outside the package cannot participate.
Workaround: use `IdentityHashMap` whenever nodes are map keys.

3. **`chapter25/pom.xml` declares `artifactId=chapter23`**:
A typo causing Maven coordinate collision with the actual chapter23 module,
and reactor build failures from the root POM.

### 64-bit arithmetic vs. 32-bit SIMD lanes

Simple uses 64-bit `long` throughout its type system (`TypeInteger`).
Assembly SIMD instructions operate on 32-bit `int` lanes.
This means the shift-based sign-extension idiom (`shl 24, sar 24` to truncate to a signed byte) does not work in 64-bit —
it must be `shl 56, sar 56`.

This is NOT a Simple issue —
Simple correctly models 64-bit arithmetic for its own language.
The gap is in Nica's IR mapping layer.
Importantly, Simple's own `ConvertNode.idealize()` (chapter 25)
uses the exact same `shl/sar` pattern with `Long.numberOfLeadingZeros(dst._max) - 1`
to compute the correct shift amount dynamically.
Nica should adopt this approach instead of hardcoding offsets.

The current MVP uses a `+32` adjustment for assembly `vpslld`/`vpsrad` shifts.
This is fragile — it works for the `shl N, sar N` sign-truncation idiom,
but would break for general 32-bit shifts (e.g., `vpslld $1`).
The proper fix is to insert explicit 32-bit truncation (`shl 32, sar 32`) after each SIMD arithmetic operation.
See `docs/simple-issues/004-64bit-arithmetic-not-an-issue.md` for the full analysis.

### Both x86 input formats use AT&T syntax

The original plan assumed the `perf annotate` format might use Intel syntax (destination before source).
During implementation, both HotSpot fast-debug and `perf annotate` outputs proved to use AT&T syntax
(`%` register prefix, `src,dst` order, `$` for immediates, parenthesized memory references).
This simplified parsing since both formats share the same operand parser (`HotSpotDebugParser.parseX86Operands`).

### Building Simple requires a two-step Maven invocation

All Simple chapters share the same parent `groupId=com.seaofnodes` and `artifactId=simple`.
Building from the root POM fails with `DuplicateProjectException` because multiple chapter modules declare the same artifactId.
The workaround is:

1. `mvn install -DskipTests -N` (install parent POM only, `-N` = non-recursive)
2. `cd chapter14 && mvn install -DskipTests` (build only the needed chapter)

This is documented in `CONTRIBUTING.md` and automated in the CI workflow.

### Cheat Sheet view does not need the IR

The Cheat Sheet view (annotated assembly) turned out to be entirely independent of the IR pipeline.
It operates directly on parsed `Instruction` objects plus the instruction description registry.
This was shipped as Phase 3, before the IR was even built (Phase 4),
delivering the first usable output early.

### In-memory compilation for E2E testing

End-to-end tests (parse → generate Java → compile → run → verify) use `javax.tools.JavaCompiler` with custom `InMemorySourceFile`,
`InMemoryClassFile`, and `InMemoryFileManager` implementations to compile and load generated code entirely in-process,
without writing to disk.
This makes the tests fast and self-contained.

### BoundsCheckRecipe operates on instructions, not IR

The GraalVM bounds-check pattern
(null check + array bounds check + exception throw)
is a control-flow guard structure, not a data-flow graph.
Recognizing it in the IR would require modeling control flow (If/Region nodes),
which the MVP IR builder does not do.
Instead, `BoundsCheckRecipe` operates directly on the `Instruction` sequence,
scanning for the `testl`/`je`/`cmpl`/`jb` pattern.
This pragmatic split — data-flow patterns via IR recipes,
control-flow patterns via instruction-sequence recipes — works well.

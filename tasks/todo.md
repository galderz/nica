# Nica — Task List

## Phase 1: Project Skeleton & Build Infrastructure

- [x] **Task 1: Maven project skeleton with Simple submodule**
  - **Description:** Initialize the Maven project structure for `org.mendrugo.nica` targeting Java 27 with preview features enabled. Add Sea of Nodes / Simple as a Git submodule pinned to a specific commit. Configure the Maven dependency on Simple's chapter25 artifact. Create the base package structure.
  - **Acceptance criteria:**
    - [x] `pom.xml` exists with `groupId=org.mendrugo`, `artifactId=nica`, Java 27 + `--enable-preview`
    - [x] Simple is a Git submodule under `lib/simple/` pinned to a specific commit
    - [x] `mvn compile` succeeds (after Simple is built locally)
    - [x] Base package `org.mendrugo.nica` exists with a placeholder class
  - **Verification:**
    - [x] `git submodule status` shows Simple pinned
    - [x] `cd lib/simple && mvn install -DskipTests` succeeds
    - [x] `mvn compile` succeeds
  - **Dependencies:** None
  - **Files likely touched:** `pom.xml`, `.gitmodules`, `src/main/java/org/mendrugo/nica/Nica.java`
  - **Estimated scope:** Small

- [x] **Task 2: GitHub Actions CI**
  - **Description:** Create a GitHub Actions workflow that checks out the repo with submodules, sets up Java 27, builds Simple, then builds and tests Nica. Cache Simple's Maven artifacts to speed up subsequent runs.
  - **Acceptance criteria:**
    - [x] `.github/workflows/ci.yml` exists
    - [x] Workflow triggers on push and pull request
    - [x] Workflow checks out submodules, builds Simple, builds and tests Nica
    - [x] Maven local repo is cached between runs
  - **Verification:**
    - [x] Push to repo triggers CI
    - [x] CI run passes (green)
  - **Dependencies:** Task 1
  - **Files likely touched:** `.github/workflows/ci.yml`
  - **Estimated scope:** Small

- [x] **Task 3: Contribution guide (Git submodule workflow)**
  - **Description:** Write `CONTRIBUTING.md` documenting the full developer workflow: cloning with submodules, building Simple locally, building and testing Nica, updating the Simple submodule pin, and common troubleshooting (stale submodule, build failures). Target audience: developers who haven't used Git submodules extensively.
  - **Acceptance criteria:**
    - [x] `CONTRIBUTING.md` exists with step-by-step clone-to-test instructions
    - [x] Git submodule commands are explained (init, update, pulling changes)
    - [x] Troubleshooting section covers common submodule pitfalls
    - [x] A fresh clone following only the guide succeeds in building and testing
  - **Verification:**
    - [x] Manual: follow the guide from scratch on a clean checkout
  - **Dependencies:** Task 1, Task 2
  - **Files likely touched:** `CONTRIBUTING.md`
  - **Estimated scope:** Small

### Checkpoint: Foundation
- [x] `mvn compile` passes for both Simple and Nica
- [x] CI workflow runs and passes
- [x] A new contributor can clone, build, and test by following `CONTRIBUTING.md`

---

## Phase 2: Assembly Parsing

- [x] **Task 4: Assembly instruction model**
  - **Description:** Define the internal representation for parsed assembly instructions. This is the common model that all parsers produce and all downstream stages consume. Includes: instruction address, mnemonic, operands (register, immediate, memory reference), original text, source annotations (Java class/method/line from JIT comments), and architecture tag (x86_64 / aarch64).
  - **Acceptance criteria:**
    - [x] `Instruction` record (or sealed hierarchy) captures mnemonic, operands, address, original text, annotations
    - [x] `Operand` sealed hierarchy covers: register, immediate, memory reference (base + index*scale + displacement)
    - [x] `AssemblySnippet` record holds a list of instructions plus metadata (architecture, block info)
    - [x] Architecture is an enum: `X86_64`, `AARCH64`
  - **Verification:**
    - [x] Unit tests construct instructions manually and verify field access
    - [x] `mvn test` passes
  - **Dependencies:** Task 1
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/asm/Instruction.java`, `src/main/java/org/mendrugo/nica/asm/Operand.java`, `src/main/java/org/mendrugo/nica/asm/AssemblySnippet.java`, `src/main/java/org/mendrugo/nica/asm/Architecture.java`
  - **Estimated scope:** Small

- [x] **Task 5: HotSpot fast-debug parser (x86_64)**
  - **Description:** Parse HotSpot fast-debug disassembly output for x86_64 into the instruction model. Handles AT&T syntax (mnemonic + operands), block header comments (`;; B22: ...`), and source annotations (`; - TestXorByte::testByte@9 (line 20)`). Input is the x86_64 example snippet from the spec.
  - **Acceptance criteria:**
    - [x] Parses the full x86_64 HotSpot fast-debug example snippet
    - [x] Every instruction in the example produces a correct `Instruction` record
    - [x] Block metadata (block number, frequency) is captured
    - [x] Source annotations (class, method, line, bci) are captured
    - [x] Comments and blank lines are handled gracefully
  - **Verification:**
    - [x] Test: parse the x86_64 example, assert instruction count, spot-check mnemonics and operands
    - [x] Test: verify source annotation on the `jl` instruction points to `TestXorByte::testByte@9 (line 20)`
    - [x] `mvn test` passes
  - **Dependencies:** Task 4
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/parser/HotSpotDebugParser.java`, `src/test/java/org/mendrugo/nica/parser/HotSpotDebugParserTest.java`, `src/test/resources/hotspot-debug-x86.asm`
  - **Estimated scope:** Medium

- [x] **Task 6: HotSpot fast-debug parser (aarch64)**
  - **Description:** Extend the HotSpot fast-debug parser to handle aarch64 syntax. Key differences: no AT&T `%` prefix on registers, different operand syntax (e.g., `[x14, #0x10]` vs `0x10(%rsi)`), condition suffixes on branches (e.g., `b.lt`), and NEON vector register notation (`v26.8h`, `v26.4s`).
  - **Acceptance criteria:**
    - [x] Parses the full aarch64 HotSpot fast-debug example snippet
    - [x] NEON vector arrangement specifiers (`.8b`, `.8h`, `.4s`, `.16b`) are captured in operands
    - [x] Conditional branches (`b.lt`) are parsed with the condition as part of the mnemonic
    - [x] Source annotations are captured (same format as x86)
  - **Verification:**
    - [x] Test: parse the aarch64 example, assert instruction count, spot-check mnemonics and operands
    - [x] Test: verify `sshll v26.8h, v26.8b, #0` parses with correct vector arrangement
    - [x] `mvn test` passes
  - **Dependencies:** Task 4, Task 5 (shared parser infrastructure)
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/parser/HotSpotDebugParser.java` (extend), `src/test/java/org/mendrugo/nica/parser/HotSpotDebugParserTest.java`, `src/test/resources/hotspot-debug-aarch64.asm`
  - **Estimated scope:** Medium

- [x] **Task 7: Perf annotate parser (x86_64)**
  - **Description:** Parse `perf annotate` output format for GraalVM native image. Key differences from HotSpot debug: percentage column on the left, arrow symbols (`→`, `↓`, `↑`, `←`) for branches, method signature as header, label targets as names (e.g., `43:`, `48:`), and Intel syntax (no `%` prefix, destination before source).
  - **Acceptance criteria:**
    - [x] Parses the full GraalVM perf-annotate example snippet
    - [x] Percentage annotations are captured per instruction
    - [x] Branch targets (label names like `43`, `48`) are resolved
    - [x] Method signature header is captured as snippet metadata
    - [x] Intel syntax operand order (dst, src) is normalized to match internal model
  - **Verification:**
    - [x] Test: parse the GraalVM example, assert instruction count, spot-check mnemonics and operands
    - [x] Test: verify percentage on `movl 0x4(%r14,%rax,8),%edi` is `12.61`
    - [x] Test: verify branch arrows are parsed correctly
    - [x] `mvn test` passes
  - **Dependencies:** Task 4
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/parser/PerfAnnotateParser.java`, `src/test/java/org/mendrugo/nica/parser/PerfAnnotateParserTest.java`, `src/test/resources/perf-annotate-graalvm-x86.asm`
  - **Estimated scope:** Medium

### Checkpoint: Parsing Complete
- [x] All three example snippets parse cleanly into the instruction model
- [x] `mvn test` passes with all parser tests green
- [x] Each parser has tests covering every instruction in its example snippet

---

## Phase 3: Cheat Sheet View

- [x] **Task 8: Instruction description registry**
  - **Description:** Build a registry mapping instruction mnemonics to human-readable descriptions with Java-equivalent pseudocode. Covers all instructions in the three example snippets. Registry is architecture-aware (same mnemonic can have different semantics on different architectures, e.g., `add`).
  - **Acceptance criteria:**
    - [x] Every x86_64 mnemonic in the examples has a description and Java-equivalent one-liner
    - [x] Every aarch64 mnemonic in the examples has a description and Java-equivalent one-liner
    - [x] Unknown mnemonics return a sentinel "UNSUPPORTED" description (not null/exception)
    - [x] SIMD instructions describe the per-lane operation (e.g., `vpmovsxbd: sign-extend 8 packed bytes → 8 packed ints`)
  - **Verification:**
    - [x] Test: look up every example mnemonic, assert non-empty description
    - [x] Test: look up unknown mnemonic, assert UNSUPPORTED
    - [x] `mvn test` passes
  - **Dependencies:** Task 4
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/cheatsheet/InstructionDescriptions.java`, `src/test/java/org/mendrugo/nica/cheatsheet/InstructionDescriptionsTest.java`
  - **Estimated scope:** Medium

- [x] **Task 9: Cheat Sheet generator**
  - **Description:** Given an `AssemblySnippet`, produce annotated assembly text with each instruction followed by a `// description` comment from the registry. Preserves original formatting, addresses, and existing comments. Adds Java-equivalent pseudocode as inline comments.
  - **Acceptance criteria:**
    - [x] Output is the original assembly with `//` comments appended to each instruction line
    - [x] Existing JIT comments (`;` lines) are preserved
    - [x] Unknown instructions get `// UNSUPPORTED: <mnemonic>` comments
    - [x] Output is human-readable and correctly aligned
  - **Verification:**
    - [x] Test: generate cheat sheet for each example snippet, verify key instruction annotations
    - [x] Test: verify unknown instruction produces UNSUPPORTED comment
    - [x] `mvn test` passes
  - **Dependencies:** Task 5, Task 6, Task 7, Task 8
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/cheatsheet/CheatSheetGenerator.java`, `src/test/java/org/mendrugo/nica/cheatsheet/CheatSheetGeneratorTest.java`
  - **Estimated scope:** Small

### Checkpoint: First Usable Output
- [x] Cheat sheet output for all three example snippets is correct and readable
- [x] `mvn test` passes

---

## Phase 4: Instruction Semantics & Simple IR

- [x] **Task 10: Instruction semantics — scalar x86_64**
  - **Description:** Implement the runtime semantics of scalar x86_64 instructions: `movq`, `movl`, `leaq`, `leal`, `addq`, `cmpl`, `cmpq`, `testl`, `jl`, `jle`, `jbe`, `je`, `nop`, `retq`, `callq`. Each instruction is a function that takes a machine state (registers + memory) and mutates it. Register writes follow Option B conventions (one `long` per physical register, 32-bit writes zero-extend).
  - **Acceptance criteria:**
    - [x] Each scalar x86_64 instruction has a semantic implementation
    - [x] `movl` zero-extends to 64 bits
    - [x] `leaq`/`leal` compute addresses without memory access
    - [x] `cmpl`/`cmpq` set flags (modeled as a boolean or flags register)
    - [x] Conditional jumps read flags and return branch-taken/not-taken
    - [x] Sub-32-bit register writes throw `UnsupportedOperationException`
  - **Verification:**
    - [x] Test: `movl $5, %eax` → rax = 5 (zero-extended)
    - [x] Test: `leaq (%r14,%rax,8), %rcx` computes correct address
    - [x] Test: `cmpl` followed by `jl` produces correct branch decision
    - [x] `mvn test` passes
  - **Dependencies:** Task 4
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/semantics/x86/ScalarX86Semantics.java`, `src/main/java/org/mendrugo/nica/semantics/MachineState.java`, tests
  - **Estimated scope:** Medium

- [x] **Task 11: Instruction semantics — SIMD x86_64**
  - **Description:** Implement the runtime semantics of AVX2 SIMD x86_64 instructions: `vmovq`, `vpmovsxbd`, `vpmulld`, `vpaddd`, `vpslld`, `vpsrad`, `vpxor`. SIMD registers (`ymm*`) are modeled as `int[8]` (256-bit, 8 packed ints). `xmm*` registers are `long` (64-bit, for `vmovq` loads).
  - **Acceptance criteria:**
    - [x] `vmovq` loads 8 bytes from memory into an xmm register (as `long`)
    - [x] `vpmovsxbd` sign-extends 8 packed bytes from xmm → 8 packed ints in ymm (`int[8]`)
    - [x] `vpmulld` performs element-wise 32-bit multiply across two ymm registers
    - [x] `vpaddd` performs element-wise 32-bit add
    - [x] `vpslld` performs element-wise left shift by immediate
    - [x] `vpsrad` performs element-wise arithmetic right shift by immediate
    - [x] `vpxor` performs element-wise XOR
    - [x] Three-operand form: destination is separate from both sources (AVX encoding)
  - **Verification:**
    - [x] Test: `vpmovsxbd` of bytes `[1, -1, 127, -128, 0, 2, -2, 64]` produces correct `int[8]`
    - [x] Test: `vpmulld` of two known int[8] vectors produces correct result
    - [x] Test: chain `vpmovsxbd` → `vpmulld` → `vpaddd` matches manual calculation
    - [x] `mvn test` passes
  - **Dependencies:** Task 10 (shared MachineState)
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/semantics/x86/SimdX86Semantics.java`, tests
  - **Estimated scope:** Medium

- [x] **Task 12: Instruction semantics — scalar aarch64**
  - **Description:** Implement the runtime semantics of scalar aarch64 instructions: `sxtw`, `add` (register form), `ldr` (single-precision float register used as 32-bit load), `cmp`, `b.lt`. Register model: `x0`-`x30` as `long`, `w0`-`w30` as lower 32 bits (writes zero-extend, same as x86 convention).
  - **Acceptance criteria:**
    - [x] `sxtw` sign-extends a 32-bit value in `wN` to 64-bit in `xN`
    - [x] `add` with register operands computes sum
    - [x] `ldr s21, [x14, #0x10]` loads 32 bits (4 bytes) from memory
    - [x] `cmp` sets condition flags
    - [x] `b.lt` branches based on signed less-than
  - **Verification:**
    - [x] Test: `sxtw x12, w17` with w17 = -1 → x12 = 0xFFFFFFFF_FFFFFFFF
    - [x] Test: `add x14, x2, x12` computes correct sum
    - [x] `mvn test` passes
  - **Dependencies:** Task 10 (shared MachineState pattern)
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/semantics/aarch64/ScalarAArch64Semantics.java`, tests
  - **Estimated scope:** Medium

- [x] **Task 13: Instruction semantics — SIMD aarch64**
  - **Description:** Implement the runtime semantics of NEON SIMD aarch64 instructions: `sshll` (signed shift left long), `mul` (vector), `mla` (vector multiply-accumulate), `shl` (vector shift left), `sshr` (vector signed shift right), `eor3` (three-way XOR). Vector registers use arrangement specifiers (`.8b`, `.8h`, `.4s`, `.16b`).
  - **Acceptance criteria:**
    - [x] `sshll v26.8h, v26.8b, #0` sign-extends 8 bytes to 8 halfwords
    - [x] `sshll v26.4s, v26.4h, #0` sign-extends 4 halfwords to 4 ints (two-step sign extension)
    - [x] `mul v28.4s, v22.4s, v21.4s` multiplies 4 packed ints element-wise
    - [x] `mla v22.4s, v20.4s, v29.4s` multiply-accumulates: v22 += v20 * v29
    - [x] `eor3 v16.16b, v16.16b, v18.16b, v31.16b` three-way XOR across 16 bytes
    - [x] Vector widths `.8b` (8 bytes), `.8h` (8 halfwords), `.4s` (4 ints), `.16b` (16 bytes) are handled
  - **Verification:**
    - [x] Test: two-step `sshll` (`.8b→.8h→.4s`) matches `vpmovsxbd` result for same input bytes
    - [x] Test: `mla` produces same result as separate `mul` + `add`
    - [x] Test: `eor3` of three known vectors matches `a ^ b ^ c`
    - [x] `mvn test` passes
  - **Dependencies:** Task 12 (shared MachineState)
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/semantics/aarch64/SimdAArch64Semantics.java`, tests
  - **Estimated scope:** Medium

- [x] **Task 14: Simple IR graph builder**
  - **Description:** Translate a parsed `AssemblySnippet` with resolved instruction semantics into a Simple IR graph (Sea of Nodes). Each instruction produces one or more Simple nodes. SIMD instructions expand into N scalar nodes (e.g., `vpaddd ymm` → 8 `AddNode`). Memory loads become `LoadNode` (or a simplified equivalent since we don't have full Simple memory alias tracking — may need a lightweight wrapper). Control flow (`cmp`/`jl`) becomes `IfNode` + `LoopNode`. Use Simple's `IRPrinter` to dump graphs for debugging.
  - **Acceptance criteria:**
    - [x] x86 HotSpot example snippet builds a valid Simple IR graph
    - [x] aarch64 HotSpot example snippet builds a valid Simple IR graph
    - [x] Both graphs have structurally equivalent data-flow subgraphs (same Add/Mul/Xor/Shl/Sar shapes)
    - [x] IR graph can be printed with `IRPrinter`
    - [x] Evaluating the graph with known inputs produces correct numeric results
  - **Verification:**
    - [x] Test: build IR from x86 example, evaluate, assert correct xor-reduce result
    - [x] Test: build IR from aarch64 example, evaluate, assert same result as x86
    - [x] Test: IRPrinter output is non-empty and contains expected node labels
    - [x] `mvn test` passes
  - **Dependencies:** Task 5, Task 6, Task 7, Task 10, Task 11, Task 12, Task 13
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/ir/IRBuilder.java`, `src/main/java/org/mendrugo/nica/ir/IREvaluator.java`, tests
  - **Estimated scope:** Large (but vertically sliced — build x86 first, then extend to aarch64)

### Checkpoint: IR Construction
- [x] Simple IR graphs build from both x86 and aarch64 examples
- [x] Evaluation produces correct numeric results
- [x] Cross-architecture IR equivalence demonstrated
- [x] `mvn test` passes

---

## Phase 5: Literal View

- [x] **Task 15: Literal Java code generator**
  - **Description:** Given a Simple IR graph (from Task 14), generate a runnable Java source file that faithfully reproduces the computation. Each register maps to a `long` variable. SIMD registers map to `int[]` arrays. Each IR node maps to a Java statement, commented with the original assembly instruction(s) it came from. The generated class has a `main` method that takes inputs, runs the computation, and prints the result.
  - **Acceptance criteria:**
    - [x] Generated Java compiles with `javac --enable-preview --release 27`
    - [x] Generated Java runs and produces correct output for known inputs
    - [x] Assembly comments appear inline in the generated code
    - [x] SIMD operations use `int[]` arrays with explicit loop-over-lanes
    - [x] Register writes use the Option B convention (explicit zero-extension for 32-bit)
  - **Verification:**
    - [x] Test: generate Literal for x86 example, compile, run, assert correct result
    - [x] Test: generate Literal for aarch64 example, compile, run, assert correct result
    - [x] Test: generated code contains assembly comments
    - [x] `mvn test` passes
  - **Dependencies:** Task 14
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/codegen/LiteralJavaGenerator.java`, tests
  - **Estimated scope:** Medium

- [x] **Task 16: Literal view end-to-end tests**
  - **Description:** End-to-end tests that parse assembly → build IR → generate Literal Java → compile → run → assert results. Tests use the three example snippets as inputs with known data. Verifies the full pipeline.
  - **Acceptance criteria:**
    - [x] E2E test for x86 HotSpot fast-debug snippet
    - [x] E2E test for aarch64 HotSpot fast-debug snippet
    - [x] E2E test for GraalVM perf-annotate snippet
    - [x] Each test compiles and runs the generated Java in-process (using `javax.tools.JavaCompiler`)
  - **Verification:**
    - [x] All three E2E tests pass
    - [x] `mvn test` passes
  - **Dependencies:** Task 15
  - **Files likely touched:** `src/test/java/org/mendrugo/nica/e2e/LiteralEndToEndTest.java`
  - **Estimated scope:** Medium

### Checkpoint: Runnable Output
- [x] Full pipeline works: parse → IR → Literal Java → compile → run → correct result
- [x] All three example snippets produce valid, runnable Java
- [x] `mvn test` passes

---

## Phase 6: Explained View

- [x] **Task 17: Pattern matcher framework**
  - **Description:** Build the pattern matching infrastructure for recognizing IR graph patterns. A `Recipe` is a named pattern that matches a subgraph of Simple IR nodes and produces a high-level Java code fragment. Uses Java 27 `switch` pattern matching with guards over Simple's Node class hierarchy. The framework walks the IR graph, tries each recipe, and returns matches with the matched subgraph marked.
  - **Acceptance criteria:**
    - [x] `Recipe` interface: `Optional<Match> match(Node root)`
    - [x] `Match` record: holds the recipe name, matched nodes, and a code generation function
    - [x] `PatternMatcher` walks the IR and collects all matches, resolving overlaps (longest match wins)
    - [x] Framework uses `switch` with type patterns and guards over Simple Node subclasses
    - [x] At least one trivial test recipe (e.g., "constant fold") validates the framework works
  - **Verification:**
    - [x] Test: trivial recipe matches a known IR subgraph
    - [x] Test: overlapping matches resolved correctly (longest wins)
    - [x] `mvn test` passes
  - **Dependencies:** Task 14
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/recipe/Recipe.java`, `src/main/java/org/mendrugo/nica/recipe/Match.java`, `src/main/java/org/mendrugo/nica/recipe/PatternMatcher.java`, tests
  - **Estimated scope:** Medium

- [x] **Task 18: Recipe — vectorized sign-extend + multiply-accumulate**
  - **Description:** Recognize the pattern: N `ConvertNode`s (sign-extend) feeding into `MulNode`s and `AddNode`s, where the structure indicates a vectorized multiply-accumulate over byte arrays. Emit readable Java: `for (int i = 0; i < N; i++) result[i] = a[i] * b[i] + c[i];`
  - **Acceptance criteria:**
    - [x] Pattern matches the inner multiply-accumulate block of the x86 and aarch64 examples
    - [x] Generated Java uses array notation instead of individual scalar variables
    - [x] Generated Java is commented with the recognized pattern name
  - **Verification:**
    - [x] Test: match against IR built from x86 example, verify pattern is recognized
    - [x] Test: match against IR built from aarch64 example, verify same pattern
    - [x] `mvn test` passes
  - **Dependencies:** Task 17, Task 14
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/recipe/MultiplyAccumulateRecipe.java`, tests
  - **Estimated scope:** Medium

- [x] **Task 19: Recipe — shift-mask-xor-reduce**
  - **Description:** Recognize the pattern: `ShlNode` → `SarNode` (same shift amount = sign-extension/truncation) followed by chained `XorNode`s (accumulating XOR across multiple results). Emit readable Java: `result ^= truncateToSignedByte(value);`
  - **Acceptance criteria:**
    - [x] Pattern matches the xor-reduce tail of the x86 and aarch64 examples
    - [x] Shift-then-right-shift-same-amount is explained as "truncate to signed byte"
    - [x] Chained XORs are collapsed into a loop or accumulation
  - **Verification:**
    - [x] Test: match against IR from examples, verify pattern recognized
    - [x] `mvn test` passes
  - **Dependencies:** Task 17, Task 14
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/recipe/ShiftMaskXorReduceRecipe.java`, tests
  - **Estimated scope:** Medium

- [x] **Task 20: Recipe — array bounds check with exception (GraalVM)**
  - **Description:** Recognize the GraalVM native image pattern: null check (`testl`/`je` → `throwNewNullPointerException`), bounds check (`cmpl`/`jb` → `outOfBoundsCheckIndex`), and the happy path (load and return). Emit readable Java: `Objects.checkIndex(index, length); return array[index];` with clear comments about the guard structure.
  - **Acceptance criteria:**
    - [x] Pattern matches the GraalVM perf-annotate example structure
    - [x] Null check + bounds check are explained as Java-level safety checks
    - [x] Exception throw paths are annotated as "slow path / never taken in normal operation"
  - **Verification:**
    - [x] Test: match against IR from GraalVM example, verify pattern recognized
    - [x] `mvn test` passes
  - **Dependencies:** Task 17, Task 14
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/recipe/BoundsCheckRecipe.java`, tests
  - **Estimated scope:** Medium

- [x] **Task 21: Explained Java code generator**
  - **Description:** Given a Simple IR graph and a set of pattern matches, generate a runnable Java source file that uses recipe-generated code for matched regions and falls back to literal translation for unmatched regions. The output should be significantly more readable than the Literal view.
  - **Acceptance criteria:**
    - [x] Matched regions use recipe-generated high-level Java
    - [x] Unmatched regions fall back to literal register-level Java
    - [x] Transitions between matched/unmatched regions are clearly commented
    - [x] Generated Java compiles and runs correctly
  - **Verification:**
    - [x] Test: generate Explained for x86 example, compile, run, assert correct result
    - [x] Test: output is shorter / more readable than Literal for the same snippet
    - [x] `mvn test` passes
  - **Dependencies:** Task 15, Task 17, Task 18, Task 19, Task 20
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/codegen/ExplainedJavaGenerator.java`, tests
  - **Estimated scope:** Medium

- [x] **Task 22: Literal ↔ Explained equivalence tests**
  - **Description:** For each example snippet, generate both Literal and Explained Java, compile and run both with the same inputs, and assert they produce identical outputs. This is the core correctness guarantee: the Explained view is a *readable* version of the Literal view, not a different computation.
  - **Acceptance criteria:**
    - [x] Equivalence test for x86 HotSpot example
    - [x] Equivalence test for aarch64 HotSpot example
    - [x] Equivalence test for GraalVM perf-annotate example
    - [x] Tests use multiple different input data sets (not just one)
  - **Verification:**
    - [x] All equivalence tests pass
    - [x] `mvn test` passes
  - **Dependencies:** Task 16, Task 21
  - **Files likely touched:** `src/test/java/org/mendrugo/nica/e2e/EquivalenceTest.java`
  - **Estimated scope:** Small

### Checkpoint: Triple View Complete
- [x] All three views work for all three example snippets
- [x] Literal ↔ Explained equivalence tests pass
- [x] `mvn test` passes

---

## Phase 7: CLI & Polish

- [x] **Task 23: CLI entry point with argument parsing**
  - **Description:** Build the `nica` CLI entry point. Accepts an assembly file, optional `--source` Java file with `--lines` range, and `--view` flag (`cheat-sheet`, `literal`, `explained`, default `literal`). Auto-detects the assembly format (HotSpot debug vs perf annotate) from file content. Outputs generated Java to stdout or a specified file.
  - **Acceptance criteria:**
    - [x] `nica input.asm` produces Literal view to stdout
    - [x] `nica --view cheat-sheet input.asm` produces Cheat Sheet
    - [x] `nica --view explained input.asm` produces Explained view
    - [x] `nica --source Foo.java --lines 20-25 input.asm` includes source context in output
    - [x] `nica -o output.java input.asm` writes to file
    - [x] Format auto-detection works for both formats
    - [x] Clear error messages for: file not found, parse failure, unsupported instruction
  - **Verification:**
    - [x] Test: invoke CLI programmatically with each view flag, assert non-empty output
    - [x] Test: format auto-detection selects correct parser
    - [x] Test: error cases produce clear messages
    - [x] `mvn test` passes
  - **Dependencies:** Task 9, Task 15, Task 21
  - **Files likely touched:** `src/main/java/org/mendrugo/nica/cli/Main.java`, tests
  - **Estimated scope:** Medium

- [x] **Task 24: End-to-end integration tests & README**
  - **Description:** Full integration tests exercising the CLI with all three example snippets and all three views (9 combinations). Update README with usage instructions, example output, the verification story (three approaches), and links to the idea document.
  - **Acceptance criteria:**
    - [x] 9 integration tests (3 snippets × 3 views) all pass
    - [x] README documents: what Nica is, how to build, how to use, example output, how to verify
    - [x] README links to the contribution guide and idea document
  - **Verification:**
    - [x] All integration tests pass
    - [x] README is accurate (a fresh reader can build and use the tool)
    - [x] `mvn test` passes
    - [x] CI green
  - **Dependencies:** Task 23
  - **Files likely touched:** `src/test/java/org/mendrugo/nica/e2e/IntegrationTest.java`, `README.md`
  - **Estimated scope:** Medium

### Checkpoint: MVP Complete
- [x] All 24 tasks complete
- [x] All tests pass (`mvn test`)
- [x] CI green
- [x] README documents usage and verification
- [x] `CONTRIBUTING.md` enables new contributors
- [x] The tool handles all three example snippets across all three views

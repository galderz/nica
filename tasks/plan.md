# Implementation Plan: Nica — Assembly-to-Java Translator

## Overview

Build a CLI tool that translates disassembled JIT compiler output (x86_64 and aarch64) into three complementary Java representations: Cheat Sheet (annotated assembly), Literal (register-level runnable Java), and Explained (pattern-recognized readable Java). Uses Sea of Nodes / Simple IR as an architecture-agnostic pivot between parsing and code generation.

## Architecture Decisions

- **Simple as Git submodule:** Simple (chapter 25) is added as a Git submodule, built locally via `mvn install`, and consumed as a Maven dependency. CI checks it out and builds it first. The contribution guide documents this workflow step-by-step for developers unfamiliar with submodules.
- **Java 27 with preview features:** Enables `switch` pattern matching with guards for the recipe/pattern matcher. Maven configured with `--enable-preview`.
- **Register model — Option B:** One `long` variable per physical register, explicit width conversions inline. Sub-32-bit writes fail hard. Sufficient for JIT output.
- **SIMD as N scalar operations:** SIMD instructions expand into N scalar Simple IR nodes (e.g., `vpaddd ymm` becomes 8 AddNodes). The Explained view reconstructs the SIMD intent.
- **Unknown instructions:** Fail hard for Literal/Explained views; emit `// UNSUPPORTED` comment for Cheat Sheet.
- **Three example snippets as test fixtures:** The x86 HotSpot fast-debug, aarch64 HotSpot fast-debug, and x86 perf-annotate GraalVM snippets from the spec are the acceptance test inputs. The tool must handle all three before MVP is done.

## Task List

See `tasks/todo.md` for the ordered checklist.

### Phase 1: Project Skeleton & Build Infrastructure
- Task 1: Maven project skeleton with Simple submodule
- Task 2: GitHub Actions CI
- Task 3: Contribution guide (Git submodule workflow)

### Checkpoint: Foundation
- All builds clean (both Simple and Nica)
- CI runs and passes on an empty test suite
- A new contributor can clone, build, and test by following the guide

### Phase 2: Assembly Parsing
- Task 4: Assembly instruction model (arch-agnostic + arch-specific)
- Task 5: HotSpot fast-debug parser (x86_64)
- Task 6: HotSpot fast-debug parser (aarch64)
- Task 7: Perf annotate parser (x86_64)

### Checkpoint: Parsing Complete
- All three example snippets parse into the instruction model
- Parser tests cover every instruction in the examples
- Round-trip: parsed instructions can reproduce the original assembly text (minus addresses)

### Phase 3: Cheat Sheet View
- Task 8: Instruction description registry
- Task 9: Cheat Sheet generator

### Checkpoint: First Usable Output
- Running `nica --cheat-sheet <file>` on any example snippet produces annotated assembly
- Every instruction in the examples has a description

### Phase 4: Instruction Semantics & Simple IR
- Task 10: Instruction semantics — scalar x86_64
- Task 11: Instruction semantics — SIMD x86_64
- Task 12: Instruction semantics — scalar aarch64
- Task 13: Instruction semantics — SIMD aarch64
- Task 14: Simple IR graph builder

### Checkpoint: IR Construction
- Building IR from the x86 HotSpot example and evaluating it produces correct numeric results
- Building IR from the aarch64 example produces the same IR shape as x86 (cross-arch equivalence)
- IRPrinter can dump the constructed graphs for debugging

### Phase 5: Literal View
- Task 15: Literal Java code generator
- Task 16: Literal view end-to-end tests

### Checkpoint: Runnable Output
- Running `nica --literal <file>` produces compilable Java
- The generated Java compiles and runs with `javac` + `java`
- Output matches expected results for known inputs

### Phase 6: Explained View
- Task 17: Pattern matcher framework
- Task 18: Recipe: vectorized sign-extend + multiply-accumulate
- Task 19: Recipe: shift-mask-xor-reduce
- Task 20: Recipe: array bounds check with exception (GraalVM snippet)
- Task 21: Explained Java code generator
- Task 22: Literal ↔ Explained equivalence tests

### Checkpoint: Triple View Complete
- All three views work for all three example snippets
- Equivalence tests pass (Literal and Explained produce identical results)

### Phase 7: CLI & Polish
- Task 23: CLI entry point with argument parsing
- Task 24: End-to-end integration tests

### Checkpoint: MVP Complete
- `nica <file> [--cheat-sheet|--literal|--explained]` works
- All tests pass
- CI green
- README documents usage

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Simple's IR can't represent some instruction semantics | High | Task 14 is designed to validate this early. If gaps found, model the missing semantics in Nica's own node subclasses. |
| Assembly parsing is fragile across format variations | Medium | Pin to the exact example formats first. Add format-detection heuristics later. Each format gets its own parser, not one mega-parser. |
| Instruction coverage grows faster than expected | Medium | Strict MVP discipline: only the instructions in the three examples. Unknown instructions fail explicitly. |
| Simple submodule build breaks in CI | Low | Pin submodule to a specific commit. CI caches the built artifacts. |
| Java 27 preview features change or break | Low | Preview features used (pattern matching in switch) are stable since Java 21, just gaining minor refinements. Risk is minimal. |

## Open Questions

None — all questions resolved in the idea refinement phase. See `docs/ideas/nica-assembly-to-java.md` for the full decision log.

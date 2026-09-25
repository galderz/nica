# Nica

Assembly-to-Java translator for JIT / AOT compiler output.

Nica takes disassembled x86_64 or aarch64 assembly from HotSpot C2 JIT
and GraalVM AOT native image compilers
and translates it into **runnable Java code** with three levels of abstraction:

1. **Cheat Sheet** — annotated assembly with inline comments explaining each instruction
2. **Literal** — faithful register-level Java (every register is a variable, every SIMD lane is an array element)
3. **Explained** — high-level Java with recognized patterns replaced by readable abstractions

## Quick Start

```bash
# Clone with submodules
git clone --recurse-submodules https://github.com/mendrugo/nica.git
cd nica

# Build Simple IR dependency
cd lib/simple && mvn install -DskipTests -N && cd chapter14 && mvn install -DskipTests && cd ../../..

# Build and test Nica
mvn verify
```

## Usage

```bash
# Default (literal view)
java --enable-preview -jar target/nica-0.1.0-SNAPSHOT.jar input.asm

# Cheat sheet — annotated assembly
java --enable-preview -jar target/nica-0.1.0-SNAPSHOT.jar --view cheat-sheet input.asm

# Explained — pattern-recognized high-level Java
java --enable-preview -jar target/nica-0.1.0-SNAPSHOT.jar --view explained input.asm

# Write to file
java --enable-preview -jar target/nica-0.1.0-SNAPSHOT.jar --view literal -o Output.java input.asm

# Specify architecture (auto-detected by default)
java --enable-preview -jar target/nica-0.1.0-SNAPSHOT.jar --arch aarch64 input.asm
```

## Example

Given this HotSpot C2 AVX2 assembly (vectorized XOR over byte arrays):

```asm
vpmovsxbd    %xmm10, %ymm13       ; sign-extend 8 bytes → 8 ints
vpmulld      %ymm9, %ymm10, %ymm12 ; multiply 8 ints element-wise
vpaddd       %ymm9, %ymm10, %ymm9  ; add 8 ints element-wise
vpslld       $0x18, %ymm8, %ymm8   ; shift left by 24
vpsrad       $0x18, %ymm8, %ymm7   ; arithmetic shift right by 24 (truncate to byte)
vpxor        %ymm0, %ymm1, %ymm0   ; XOR 8 ints element-wise
```

**Cheat Sheet** output:

```
vpmovsxbd  %xmm10, %ymm13  // Sign-extend 8 packed bytes → 8 packed 32-bit ints  →  for (i < 8) dst[i] = (int)(byte) src[i]
vpmulld    %ymm9, %ymm10, %ymm12  // Multiply 8 packed 32-bit ints (element-wise)  →  for (i < 8) dst[i] = a[i] * b[i]
vpslld     $0x18, %ymm8, %ymm8  // Shift left 8 packed 32-bit ints by immediate  →  for (i < 8) dst[i] = src[i] << imm
```

**Literal** output:

```java
for (int i = 0; i < 8; i++) ymm13[i] = (byte) xmm10[i];       // vpmovsxbd
for (int i = 0; i < 8; i++) ymm12[i] = ymm9[i] * ymm10[i];    // vpmulld
for (int i = 0; i < 8; i++) ymm8[i] = ymm8[i] << 24;          // vpslld
for (int i = 0; i < 8; i++) ymm7[i] = ymm8[i] >> 24;          // vpsrad
for (int i = 0; i < 8; i++) ymm0[i] = ymm0[i] ^ ymm1[i];     // vpxor
```

**Explained** output:

```java
for (int i = 0; i < 8; i++) {
    int a = signExtByte(xmm0[i]);   // vpmovsxbd: sign-extend byte to int
    int b = signExtByte(xmm1[i]);
    int product = a * b;             // vpmulld: multiply
    int truncated = signExtByte(product); // vpslld + vpsrad: truncate to signed byte
    result[i] = truncated ^ a;       // vpxor: XOR accumulate
}
```

## Supported Formats

| Format | Architecture | Syntax | Source |
|--------|-------------|--------|--------|
| HotSpot fast-debug disassembly | x86_64, aarch64 | AT&T | `PrintAssembly` with capstone |
| `perf annotate` output | x86_64 | AT&T | GraalVM native image |

> **Note:** Only AT&T syntax is supported for x86\_64 assembly.
> Intel syntax (used by some disassemblers and debuggers) is not supported.
> AT&T syntax is the default output format for HotSpot's `PrintAssembly` and `perf annotate`,
> so no configuration is typically needed.

## Recognized Patterns

The Explained view recognizes these assembly idioms:

| Pattern | Description | Seen in |
|---------|-------------|---------|
| Vectorized multiply-accumulate | Sign-extend bytes → multiply → add | HotSpot C2 SIMD loops |
| Shift-mask XOR reduce | Truncate to byte via shl/sar → XOR accumulate | HotSpot C2 SIMD loops |
| Bounds check guard | Null check + array bounds check → exception | GraalVM native image |

## How to Verify

The generated Java is a faithful representation of the assembly. Three ways to verify:

1. **Equivalence tests**:
The test suite proves Literal and Explained views produce identical results for the same inputs
(`EquivalenceTest.java`)
2. **Run it yourself**:
Compile and run the generated Java with known inputs,
compare against the actual compiled code's behavior
3. **Cheat sheet**:
Read the annotated assembly instruction-by-instruction
and verify each comment matches the instruction's semantics

## Architecture

```
Assembly Text → Parser → Instructions → Simple IR Graph → Three Views
                                                            ├── Cheat Sheet
                                                            ├── Literal
                                                            └── Explained
```

- **Parsers**: `HotSpotDebugParser`, `PerfAnnotateParser`
- **IR**: Uses [Sea of Nodes / Simple](https://github.com/SeaOfNodes/Simple) chapter 14 nodes (`Add`, `Mul`, `Xor`, `Shl`, `Sar`, `Constant`)
- **Pattern matching**: Java 27 `switch` with type patterns and guards over Simple's Node classes
- **Code generation**: `LiteralJavaGenerator`, `ExplainedJavaGenerator`, `CheatSheetGenerator`

## Requirements

- Java 27 (with `--enable-preview`)
- Maven 3.9+

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for build instructions and Git submodule workflow.

## Design

See [docs/ideas/nica-assembly-to-java.md](docs/ideas/nica-assembly-to-java.md) for the full design document.

## License

TBD

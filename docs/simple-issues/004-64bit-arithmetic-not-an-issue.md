# Analysis: 64-bit Arithmetic in Simple vs. 32-bit SIMD Lanes — Not a Simple Issue

## Summary

Simple's IR uses 64-bit `long` values throughout (`TypeInteger`). Assembly SIMD
instructions (x86 AVX2, aarch64 NEON) operate on 32-bit `int` lanes. This
creates a semantic gap when mapping SIMD operations to Simple IR nodes.

**This is NOT a Simple issue.** Simple correctly models 64-bit arithmetic for
its own language. The gap is in Nica's IR mapping layer, which must explicitly
handle the 32-bit to 64-bit conversion when representing SIMD lane semantics.

## The Problem

Assembly SIMD instructions operate on 32-bit lanes. The pattern
`vpslld $0x18` / `vpsrad $0x18` (shift left 24, arithmetic right shift 24)
truncates a 32-bit value to a signed byte. This works because:

```
32-bit int: 300 (0x0000012C)
  << 24:    0x2C000000 (overflow wraps at 32 bits)
  >> 24:    0x0000002C = 44 = (byte)300  ✓
```

In 64-bit `long`, the same shifts do NOT truncate:

```
64-bit long: 300 (0x000000000000012C)
  << 24:     0x0000012C000000 (no overflow in 64 bits!)
  >> 24:     0x000000000000012C = 300  ✗ (no truncation)
```

## How Simple's Own Code Handles This

Chapter 25's `ConvertNode.idealize()` narrows integers using the same
shl/sar pattern, but with 64-bit-aware shift amounts:

```java
// ConvertNode.java (chapter 25, line ~99)
// Narrow integers produce the declared sign/zero extension.
if (src instanceof TypeInteger && _dst instanceof TypeInteger dst) {
    if (dst._min == 0)
        return new AndNode(null, val(), con(dst._max));  // unsigned: mask
    int shift = Long.numberOfLeadingZeros(dst._max) - 1;
    Node shf = con(shift);
    return new SarNode(null, new ShlNode(null, val(), shf).peephole(), shf);
}
```

For `TypeInteger.I8` (max=127):
- `Long.numberOfLeadingZeros(127)` = 57
- `shift` = 56
- Result: `shl 56, sar 56` — sign-extends from bit 7 across all 64 bits ✓

This is the canonical Simple idiom for narrowing to a signed sub-type.

## What Nica Does (Current Workaround)

Nica adjusts shift amounts when building IR from assembly:

1. **Byte sign-extension** (`vpmovsxbd`, `sshll .8b`):
   - Assembly semantics: sign-extend byte to 32-bit int
   - Simple IR: `shl 56, sar 56` (not `shl 24, sar 24`)
   - Follows Simple's own ConvertNode pattern ✓

2. **Assembly shifts** (`vpslld $N`, `vpsrad $N`):
   - Assembly semantics: shift in 32-bit lanes
   - Simple IR: shift by `N + 32` to account for 64-bit representation
   - This is a hack that only works for the specific pattern `shl N, sar N`
     (sign truncation) ✗

## Why the +32 Adjustment Is Fragile

The `N + 32` adjustment works for the sign-extension idiom because:
- `shl (24+32)` = `shl 56` pushes the byte's sign bit to bit 63
- `sar (24+32)` = `sar 56` sign-extends from bit 7

But it BREAKS for general shifts. Example:

```
Assembly: vpslld $1  (shift left by 1 in 32-bit lane)
  32-bit: 0x80000001 << 1 = 0x00000002 (wraps at 32 bits)

Nica:     shl 33 in 64-bit
  64-bit: 0x80000001 << 33 = 0x10000000200000000 (completely wrong!)

Correct:  (value << 1) with 32-bit truncation afterward
```

## Proper Fix for Nica (Future Work)

The current `+32` hardcoding should be replaced with Simple's own approach:
use `Long.numberOfLeadingZeros(dst._max) - 1` to compute the correct shift
amount for any target width dynamically. This is what `ConvertNode.idealize()`
does (chapter 25), and Nica should adopt the same pattern instead of
hardcoding an offset.

For byte sign-extension (`vpmovsxbd`, `sshll .8b`):
```java
// Instead of hardcoding 56:
int shift = Long.numberOfLeadingZeros(TypeInteger.I8._max) - 1; // = 56
```

For halfword sign-extension (`sshll .4h`):
```java
int shift = Long.numberOfLeadingZeros(TypeInteger.I16._max) - 1; // = 48
```

Beyond sign-extension, the correct approach is to model 32-bit lane semantics
explicitly:

**Option A: Truncate after each operation**

After every SIMD arithmetic operation, insert a truncation to 32-bit using
the same `ConvertNode` idiom:
```java
// Truncate to signed 32-bit:
int shift = Long.numberOfLeadingZeros(TypeInteger.I32._max) - 1; // = 32
SarNode(ShlNode(result, #shift), #shift)
```

This is the canonical Simple pattern. It ensures that intermediate SIMD
results stay within 32-bit range, making subsequent shifts and overflows
behave identically to the assembly's 32-bit lane semantics.

**Option B: Use TypeInteger.I32 type annotations**

Annotate SIMD lane values with `TypeInteger.I32` type bounds. Simple's type
system already has `I32 = make(-1L<<31, (1L<<31)-1)`. Operations on values
within this range produce correct results for most operations. Only shifts
and multiplications that could overflow 32 bits need explicit truncation.

**Option C: Extend Simple with a width-aware shift node**

Add a node that specifies the lane width (8, 16, 32, 64) alongside the shift
amount. This is the cleanest semantics but requires modifying Simple.

## Conclusion

- **Not a Simple issue**: Simple's 64-bit arithmetic is correct for its language
- **Nica limitation**: The current `+32` shift adjustment is a fragile workaround
  that happens to work for the specific assembly patterns in the example snippets
  (sign-extension via shl/sar) but would break for general 32-bit SIMD shifts
- **Simple provides the right idiom**: `ConvertNode`'s shl/sar pattern for
  narrowing is exactly what Nica should use for sign-extension
- **Future work**: Nica should insert explicit 32-bit truncation after each SIMD
  operation to properly model 32-bit lane semantics in 64-bit IR

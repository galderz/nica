package org.mendrugo.nica.semantics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * Simulated machine state: registers, memory, and flags.
 *
 * <p>Registers are modeled as one {@code long} per physical register
 * (Option B from the design doc). 32-bit writes zero-extend to 64 bits.
 * Sub-32-bit writes are unsupported and throw {@link UnsupportedOperationException}.</p>
 *
 * <p>SIMD registers (xmm/ymm on x86, v-registers on aarch64) are modeled
 * as {@code int[]} arrays — 8 elements for 256-bit ymm, 4 for 128-bit.</p>
 *
 * <p>Memory is a flat byte-addressable buffer in little-endian order.</p>
 */
public final class MachineState {

    private final Map<String, Long> registers = new HashMap<>();
    private final Map<String, int[]> simdRegisters = new HashMap<>();
    private final ByteBuffer memory;

    /** Comparison flags: negative = less than, zero = equal, positive = greater than. */
    private int flags;

    /**
     * Create a machine state with the given memory size.
     *
     * @param memorySize size of the flat memory in bytes
     */
    public MachineState(int memorySize) {
        this.memory = ByteBuffer.allocate(memorySize).order(ByteOrder.LITTLE_ENDIAN);
    }

    // --- Scalar registers ---

    /** Read a 64-bit register value. Returns 0 if the register has not been written. */
    public long getReg(String name) {
        return registers.getOrDefault(canonicalReg(name), 0L);
    }

    /**
     * Write a 64-bit value to a register.
     *
     * @param name register name (e.g. "rax", "x12")
     * @param value the 64-bit value
     */
    public void setReg64(String name, long value) {
        registers.put(canonicalReg(name), value);
    }

    /**
     * Write a 32-bit value to a register, zero-extending to 64 bits.
     * This is the x86_64 behavior for {@code movl} and similar 32-bit ops.
     *
     * @param name register name (e.g. "eax", "w12")
     * @param value the 32-bit value (only low 32 bits used)
     */
    public void setReg32(String name, int value) {
        registers.put(canonicalReg(name), Integer.toUnsignedLong(value));
    }

    // --- SIMD registers ---

    /**
     * Get a SIMD register as an int array (8 elements for ymm, 4 for xmm/.4s).
     * Returns a zero-filled array if the register has not been written.
     */
    public int[] getSimd(String name, int lanes) {
        String canon = canonicalSimdReg(name);
        int[] reg = simdRegisters.get(canon);
        if (reg == null || reg.length < lanes) {
            return new int[lanes];
        }
        if (reg.length == lanes) return reg;
        // Truncate to requested lanes
        int[] result = new int[lanes];
        System.arraycopy(reg, 0, result, 0, lanes);
        return result;
    }

    /** Set a SIMD register (array is stored directly, not copied). */
    public void setSimd(String name, int[] values) {
        simdRegisters.put(canonicalSimdReg(name), values);
    }

    // --- Flags ---

    public int getFlags() { return flags; }

    public void setFlags(int flags) { this.flags = flags; }

    /** Set flags from a 32-bit comparison: dst - src. */
    public void setFlagsFromCmp32(int a, int b) {
        this.flags = Integer.compare(a, b);
    }

    /** Set flags from a 64-bit comparison: dst - src. */
    public void setFlagsFromCmp64(long a, long b) {
        this.flags = Long.compare(a, b);
    }

    /** Set flags from a test (AND) operation. */
    public void setFlagsFromTest(long result) {
        this.flags = result == 0 ? 0 : (result < 0 ? -1 : 1);
    }

    // --- Memory ---

    public byte loadByte(long address) {
        return memory.get(toIndex(address));
    }

    public int loadInt(long address) {
        return memory.getInt(toIndex(address));
    }

    public long loadLong(long address) {
        return memory.getLong(toIndex(address));
    }

    public void storeByte(long address, byte value) {
        memory.put(toIndex(address), value);
    }

    public void storeInt(long address, int value) {
        memory.putInt(toIndex(address), value);
    }

    public void storeLong(long address, long value) {
        memory.putLong(toIndex(address), value);
    }

    /**
     * Bulk-store a byte array into memory at the given address.
     * Useful for test setup.
     */
    public void storeBytes(long address, byte[] data) {
        int idx = toIndex(address);
        for (byte b : data) {
            memory.put(idx++, b);
        }
    }

    /**
     * Load 8 bytes as a long (for vmovq / ldr s-register loads).
     */
    public long load8Bytes(long address) {
        return memory.getLong(toIndex(address));
    }

    /**
     * Load 4 bytes as an int (for ldr s-register 32-bit loads).
     */
    public int load4Bytes(long address) {
        return memory.getInt(toIndex(address));
    }

    // --- Register name canonicalization ---

    /**
     * Map register aliases to a canonical 64-bit name.
     * x86: eax→rax, ebp→rbp, etc.
     * aarch64: w12→x12, etc.
     */
    static String canonicalReg(String name) {
        // x86: 32-bit → 64-bit
        return switch (name) {
            case "eax" -> "rax";
            case "ebx" -> "rbx";
            case "ecx" -> "rcx";
            case "edx" -> "rdx";
            case "esi" -> "rsi";
            case "edi" -> "rdi";
            case "ebp" -> "rbp";
            case "esp" -> "rsp";
            case "r8d" -> "r8";
            case "r9d" -> "r9";
            case "r10d" -> "r10";
            case "r11d" -> "r11";
            case "r12d" -> "r12";
            case "r13d" -> "r13";
            case "r14d" -> "r14";
            case "r15d" -> "r15";
            // aarch64: w-registers → x-registers
            default -> {
                if (name.startsWith("w") && name.length() > 1
                    && Character.isDigit(name.charAt(1))) {
                    yield "x" + name.substring(1);
                }
                yield name;
            }
        };
    }

    /**
     * Map SIMD register aliases to a canonical name.
     * x86: xmm0, ymm0 → ymm0 (ymm is the widest)
     * aarch64: s21, v21 → v21
     */
    static String canonicalSimdReg(String name) {
        if (name.startsWith("xmm")) {
            return "ymm" + name.substring(3);
        }
        if (name.startsWith("s") && name.length() > 1
            && Character.isDigit(name.charAt(1))) {
            return "v" + name.substring(1);
        }
        return name;
    }

    private static int toIndex(long address) {
        if (address < 0 || address > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Address out of range: " + Long.toHexString(address));
        }
        return (int) address;
    }
}

package org.mendrugo.nica.asm;

/**
 * Source-level annotation from JIT compiler debug output.
 *
 * <p>Maps an instruction back to the Java source that produced it,
 * e.g. {@code TestXorByte::testByte@9 (line 20)}.</p>
 *
 * @param className the Java class name (e.g. "TestXorByte")
 * @param methodName the method name (e.g. "testByte")
 * @param bci bytecode index, or -1 if not available
 * @param lineNumber source line number, or -1 if not available
 */
public record SourceAnnotation(
    String className,
    String methodName,
    int bci,
    int lineNumber
) {}

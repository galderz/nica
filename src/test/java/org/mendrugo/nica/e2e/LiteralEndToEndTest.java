package org.mendrugo.nica.e2e;

import org.junit.jupiter.api.Test;
import org.mendrugo.nica.asm.*;
import org.mendrugo.nica.codegen.LiteralJavaGenerator;
import org.mendrugo.nica.semantics.MachineState;
import org.mendrugo.nica.semantics.x86.SimdX86Semantics;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests: parse assembly → generate Literal Java → compile in-memory → run → verify.
 *
 * <p>Uses {@link javax.tools.JavaCompiler} to compile generated code in-memory,
 * then invokes the generated {@code run()} method reflectively to verify
 * that the output matches the expected computation.</p>
 */
class LiteralEndToEndTest {

    @Test
    void smallX86SnippetEndToEnd() throws Exception {
        // Build a small snippet with known computation
        var insns = List.of(
            insn("vpmovsxbd", reg("xmm0"), reg("ymm0")),
            insn("vpmovsxbd", reg("xmm1"), reg("ymm1")),
            insn("vpmulld", reg("ymm0"), reg("ymm1"), reg("ymm2")),
            insn("vpslld", imm(0x18), reg("ymm2"), reg("ymm2")),
            insn("vpsrad", imm(0x18), reg("ymm2"), reg("ymm2")),
            insn("vpxor", reg("ymm2"), reg("ymm0"), reg("ymm0"))
        );
        var snippet = new AssemblySnippet(Architecture.X86_64, insns);

        // Generate Java source
        String source = LiteralJavaGenerator.generate(snippet, "SmallX86E2E");

        // Compute expected result via machine state
        byte[] a = {1, -1, 127, -128, 0, 2, -2, 64};
        byte[] b = {10, 20, 30, 40, 50, 60, 70, 80};
        int[] expected = new int[8];
        for (int i = 0; i < 8; i++) {
            int product = a[i] * b[i];
            int truncated = (byte) product;
            expected[i] = truncated ^ a[i];
        }

        // Compile and run
        int[] aUnsigned = bytesToUnsigned(a);
        int[] bUnsigned = bytesToUnsigned(b);

        int[] result = compileAndRun(source, "SmallX86E2E", aUnsigned, bUnsigned);

        assertArrayEquals(expected, result,
            "E2E result mismatch. Source:\n" + source);
    }

    @Test
    void smallX86SnippetDifferentInputs() throws Exception {
        var insns = List.of(
            insn("vpmovsxbd", reg("xmm0"), reg("ymm0")),
            insn("vpmovsxbd", reg("xmm1"), reg("ymm1")),
            insn("vpmulld", reg("ymm0"), reg("ymm1"), reg("ymm2")),
            insn("vpslld", imm(0x18), reg("ymm2"), reg("ymm2")),
            insn("vpsrad", imm(0x18), reg("ymm2"), reg("ymm2")),
            insn("vpxor", reg("ymm2"), reg("ymm0"), reg("ymm0"))
        );
        var snippet = new AssemblySnippet(Architecture.X86_64, insns);
        String source = LiteralJavaGenerator.generate(snippet, "SmallX86E2E2");

        // Different inputs
        byte[] a = {0, 1, -1, 50, -50, 100, -100, 127};
        byte[] b = {1, 2, 3, 4, 5, 6, 7, 8};
        int[] expected = new int[8];
        for (int i = 0; i < 8; i++) {
            expected[i] = (byte)(a[i] * b[i]) ^ a[i];
        }

        int[] result = compileAndRun(source, "SmallX86E2E2",
            bytesToUnsigned(a), bytesToUnsigned(b));

        assertArrayEquals(expected, result);
    }

    @Test
    void generatedSourceCompiles() throws Exception {
        var insns = List.of(
            insn("vpmovsxbd", reg("xmm0"), reg("ymm0")),
            insn("vpxor", reg("ymm0"), reg("ymm0"), reg("ymm0"))
        );
        var snippet = new AssemblySnippet(Architecture.X86_64, insns);
        String source = LiteralJavaGenerator.generate(snippet, "CompileTest");

        // Should compile without errors
        Class<?> clazz = compile(source, "CompileTest");
        assertNotNull(clazz);

        // Should have run() with one int[] parameter (xmm0 is the only input)
        assertNotNull(clazz.getMethod("run", int[].class));
        assertNotNull(clazz.getMethod("main", String[].class));
    }

    // --- In-memory compilation infrastructure ---

    private int[] compileAndRun(String source, String className, int[]... args) throws Exception {
        Class<?> clazz = compile(source, className);
        // Find run() method - parameter types are all int[]
        Class<?>[] paramTypes = new Class<?>[args.length];
        Arrays.fill(paramTypes, int[].class);
        Method run = clazz.getMethod("run", paramTypes);
        return (int[]) run.invoke(null, (Object[]) args);
    }

    private Class<?> compile(String source, String className) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "No system Java compiler available (need JDK, not JRE)");

        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var fileManager = new InMemoryFileManager(
            compiler.getStandardFileManager(diagnostics, null, null));

        var sourceFile = new InMemorySourceFile(className, source);
        var task = compiler.getTask(null, fileManager, diagnostics,
            null, null, List.of(sourceFile));

        boolean success = task.call();
        if (!success) {
            var sb = new StringBuilder("Compilation failed:\n");
            for (var d : diagnostics.getDiagnostics()) {
                sb.append("  ").append(d.getMessage(null)).append('\n');
                sb.append("  Line ").append(d.getLineNumber()).append('\n');
            }
            sb.append("\nSource:\n").append(source);
            fail(sb.toString());
        }

        return fileManager.getClassLoader(null).loadClass(className);
    }

    // --- In-memory JavaFileObject implementations ---

    private static class InMemorySourceFile extends SimpleJavaFileObject {
        private final String code;

        InMemorySourceFile(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension),
                Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    private static class InMemoryClassFile extends SimpleJavaFileObject {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        InMemoryClassFile(String name) {
            super(URI.create("mem:///" + name.replace('.', '/') + Kind.CLASS.extension),
                Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return bytes;
        }

        byte[] getBytes() {
            return bytes.toByteArray();
        }
    }

    private static class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, InMemoryClassFile> classFiles = new HashMap<>();

        InMemoryFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                JavaFileObject.Kind kind, FileObject sibling) {
            var file = new InMemoryClassFile(className);
            classFiles.put(className, file);
            return file;
        }

        @Override
        public ClassLoader getClassLoader(Location location) {
            return new ClassLoader(getClass().getClassLoader()) {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    InMemoryClassFile file = classFiles.get(name);
                    if (file == null) throw new ClassNotFoundException(name);
                    byte[] bytes = file.getBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                }
            };
        }
    }

    // --- Helpers ---

    private static int[] bytesToUnsigned(byte[] bytes) {
        int[] result = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) result[i] = bytes[i] & 0xFF;
        return result;
    }

    private static Instruction insn(String mnemonic, Operand... ops) {
        return new Instruction(-1, mnemonic, List.of(ops),
            mnemonic + " " + String.join(", ", Arrays.stream(ops).map(Object::toString).toList()));
    }
    private static Operand.Register reg(String name) { return new Operand.Register(name); }
    private static Operand.Immediate imm(long value) { return new Operand.Immediate(value); }
}

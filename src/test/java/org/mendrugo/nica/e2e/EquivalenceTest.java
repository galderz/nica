package org.mendrugo.nica.e2e;

import org.junit.jupiter.api.Test;
import org.mendrugo.nica.asm.*;
import org.mendrugo.nica.codegen.*;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Equivalence tests: for the same assembly snippet and same inputs,
 * the Literal and Explained views must produce identical results.
 *
 * <p>This is the core correctness guarantee: the Explained view is a
 * <em>readable</em> version of the Literal view, not a different computation.</p>
 */
class EquivalenceTest {

    // --- Test data sets ---
    static final int[][] INPUTS = {
        {1, 255, 127, 128, 0, 2, 254, 64},      // data set 1
        {10, 20, 30, 40, 50, 60, 70, 80},
    };

    static final int[][] INPUTS_2 = {
        {0, 1, 128, 255, 127, 64, 32, 16},       // data set 2
        {5, 10, 15, 20, 25, 30, 35, 40},
    };

    static final int[][] INPUTS_3 = {
        {255, 255, 255, 255, 255, 255, 255, 255}, // data set 3: all 0xFF
        {1, 1, 1, 1, 1, 1, 1, 1},
    };

    @Test
    void x86EquivalenceDataSet1() throws Exception {
        assertEquivalence(smallX86Snippet(), "EquivX86_1", INPUTS);
    }

    @Test
    void x86EquivalenceDataSet2() throws Exception {
        assertEquivalence(smallX86Snippet(), "EquivX86_2", INPUTS_2);
    }

    @Test
    void x86EquivalenceDataSet3() throws Exception {
        assertEquivalence(smallX86Snippet(), "EquivX86_3", INPUTS_3);
    }

    // --- Core equivalence assertion ---

    private void assertEquivalence(AssemblySnippet snippet, String baseName, int[][] inputs)
            throws Exception {
        String literalSrc = LiteralJavaGenerator.generate(snippet, baseName + "Literal");
        String explainedSrc = ExplainedJavaGenerator.generate(snippet, baseName + "Explained");

        // Compile both
        Class<?> literalClass = compile(literalSrc, baseName + "Literal");
        Class<?> explainedClass = compile(explainedSrc, baseName + "Explained");

        // Find run() methods
        Method literalRun = findRunMethod(literalClass);
        Method explainedRun = findRunMethod(explainedClass);

        // Run both with same inputs
        int[] literalResult = invoke(literalRun, inputs);
        int[] explainedResult = invoke(explainedRun, inputs);

        assertNotNull(literalResult, "Literal run() returned null");
        assertNotNull(explainedResult, "Explained run() returned null");
        assertArrayEquals(literalResult, explainedResult,
            "Literal and Explained views produce different results!\n" +
            "Inputs: " + Arrays.deepToString(inputs) + "\n" +
            "Literal result:   " + Arrays.toString(literalResult) + "\n" +
            "Explained result: " + Arrays.toString(explainedResult) + "\n\n" +
            "Literal source:\n" + literalSrc + "\n\n" +
            "Explained source:\n" + explainedSrc);
    }

    // --- Test snippets ---

    private static AssemblySnippet smallX86Snippet() {
        var insns = List.of(
            insn("vpmovsxbd", reg("xmm0"), reg("ymm0")),
            insn("vpmovsxbd", reg("xmm1"), reg("ymm1")),
            insn("vpmulld", reg("ymm0"), reg("ymm1"), reg("ymm2")),
            insn("vpslld", imm(0x18), reg("ymm2"), reg("ymm2")),
            insn("vpsrad", imm(0x18), reg("ymm2"), reg("ymm2")),
            insn("vpxor", reg("ymm2"), reg("ymm0"), reg("ymm0"))
        );
        return new AssemblySnippet(Architecture.X86_64, insns);
    }

    // --- Compilation and invocation infrastructure ---

    private Method findRunMethod(Class<?> clazz) {
        for (var method : clazz.getMethods()) {
            if (method.getName().equals("run")) return method;
        }
        fail("No run() method found in " + clazz.getName());
        return null;
    }

    private int[] invoke(Method run, int[][] inputs) throws Exception {
        int paramCount = run.getParameterCount();
        Object[] args = new Object[paramCount];
        for (int i = 0; i < paramCount; i++) {
            args[i] = i < inputs.length ? inputs[i] : new int[8];
        }
        return (int[]) run.invoke(null, args);
    }

    private Class<?> compile(String source, String className) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var fm = new InMemoryFileManager(compiler.getStandardFileManager(diagnostics, null, null));
        var task = compiler.getTask(null, fm, diagnostics, null, null,
            List.of(new InMemorySource(className, source)));
        boolean ok = task.call();
        if (!ok) {
            var sb = new StringBuilder("Compilation failed for " + className + ":\n");
            for (var d : diagnostics.getDiagnostics())
                sb.append("  L").append(d.getLineNumber()).append(": ").append(d.getMessage(null)).append('\n');
            sb.append("\nSource:\n").append(source);
            fail(sb.toString());
        }
        return fm.getClassLoader(null).loadClass(className);
    }

    // --- In-memory compilation helpers (shared with LiteralEndToEndTest) ---

    private static class InMemorySource extends SimpleJavaFileObject {
        private final String code;
        InMemorySource(String name, String code) {
            super(URI.create("string:///" + name + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }
        @Override public CharSequence getCharContent(boolean ignore) { return code; }
    }

    private static class InMemoryClass extends SimpleJavaFileObject {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        InMemoryClass(String name) {
            super(URI.create("mem:///" + name + Kind.CLASS.extension), Kind.CLASS);
        }
        @Override public OutputStream openOutputStream() { return bytes; }
        byte[] getBytes() { return bytes.toByteArray(); }
    }

    private static class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, InMemoryClass> classes = new HashMap<>();
        InMemoryFileManager(StandardJavaFileManager delegate) { super(delegate); }
        @Override public JavaFileObject getJavaFileForOutput(Location loc, String name,
                JavaFileObject.Kind kind, FileObject sibling) {
            var f = new InMemoryClass(name); classes.put(name, f); return f;
        }
        @Override public ClassLoader getClassLoader(Location loc) {
            return new ClassLoader(getClass().getClassLoader()) {
                @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                    var f = classes.get(name);
                    if (f == null) throw new ClassNotFoundException(name);
                    byte[] b = f.getBytes();
                    return defineClass(name, b, 0, b.length);
                }
            };
        }
    }

    // --- Instruction helpers ---

    private static Instruction insn(String mnemonic, Operand... ops) {
        return new Instruction(-1, mnemonic, List.of(ops),
            mnemonic + " " + String.join(", ", Arrays.stream(ops).map(Object::toString).toList()));
    }
    private static Operand.Register reg(String name) { return new Operand.Register(name); }
    private static Operand.Immediate imm(long value) { return new Operand.Immediate(value); }
}

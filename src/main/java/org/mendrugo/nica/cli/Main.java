package org.mendrugo.nica.cli;

import org.mendrugo.nica.asm.Architecture;
import org.mendrugo.nica.asm.AssemblySnippet;
import org.mendrugo.nica.cheatsheet.CheatSheetGenerator;
import org.mendrugo.nica.codegen.ExplainedJavaGenerator;
import org.mendrugo.nica.codegen.LiteralJavaGenerator;
import org.mendrugo.nica.parser.HotSpotDebugParser;
import org.mendrugo.nica.parser.PerfAnnotateParser;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Nica CLI entry point.
 *
 * <p>Usage: {@code nica [options] <assembly-file>}</p>
 *
 * <p>Options:</p>
 * <ul>
 *   <li>{@code --view cheat-sheet|literal|explained} — output view (default: literal)</li>
 *   <li>{@code --arch x86_64|aarch64} — architecture hint (auto-detected if omitted)</li>
 *   <li>{@code --class-name <name>} — generated class name (default: derived from file name)</li>
 *   <li>{@code -o <file>} — write output to file instead of stdout</li>
 *   <li>{@code --help} — show usage</li>
 * </ul>
 */
public final class Main {

    enum View { CHEAT_SHEET, LITERAL, EXPLAINED }

    private Main() {}

    public static void main(String[] args) {
        try {
            int exitCode = run(args, System.out, System.err);
            if (exitCode != 0) System.exit(exitCode);
        } catch (Exception e) {
            System.err.println("nica: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Main logic, separated for testability.
     *
     * @return 0 on success, non-zero on error
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        // Parse arguments
        String inputFile = null;
        View view = View.LITERAL;
        String archHint = null;
        String className = null;
        String outputFile = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> {
                    printUsage(out);
                    return 0;
                }
                case "--view" -> {
                    if (++i >= args.length) { err.println("nica: --view requires an argument"); return 1; }
                    view = parseView(args[i]);
                    if (view == null) {
                        err.println("nica: unknown view '" + args[i] + "'. Use: cheat-sheet, literal, explained");
                        return 1;
                    }
                }
                case "--arch" -> {
                    if (++i >= args.length) { err.println("nica: --arch requires an argument"); return 1; }
                    archHint = args[i];
                }
                case "--class-name" -> {
                    if (++i >= args.length) { err.println("nica: --class-name requires an argument"); return 1; }
                    className = args[i];
                }
                case "-o" -> {
                    if (++i >= args.length) { err.println("nica: -o requires an argument"); return 1; }
                    outputFile = args[i];
                }
                default -> {
                    if (args[i].startsWith("-")) {
                        err.println("nica: unknown option '" + args[i] + "'");
                        return 1;
                    }
                    if (inputFile != null) {
                        err.println("nica: multiple input files not supported");
                        return 1;
                    }
                    inputFile = args[i];
                }
            }
        }

        if (inputFile == null) {
            err.println("nica: no input file specified");
            printUsage(err);
            return 1;
        }

        // Read input file
        String text;
        try {
            text = Files.readString(Path.of(inputFile));
        } catch (IOException e) {
            err.println("nica: cannot read file '" + inputFile + "': " + e.getMessage());
            return 1;
        }

        // Detect format and parse
        AssemblySnippet snippet;
        try {
            snippet = parseSnippet(text, archHint);
        } catch (Exception e) {
            err.println("nica: parse error: " + e.getMessage());
            return 1;
        }

        // Derive class name from file if not specified
        if (className == null) {
            className = deriveClassName(inputFile);
        }

        // Generate output
        String output = switch (view) {
            case CHEAT_SHEET -> CheatSheetGenerator.generate(snippet);
            case LITERAL -> LiteralJavaGenerator.generate(snippet, className);
            case EXPLAINED -> ExplainedJavaGenerator.generate(snippet, className);
        };

        // Write output
        if (outputFile != null) {
            try {
                Files.writeString(Path.of(outputFile), output);
            } catch (IOException e) {
                err.println("nica: cannot write to '" + outputFile + "': " + e.getMessage());
                return 1;
            }
        } else {
            out.print(output);
        }

        return 0;
    }

    /**
     * Parse an assembly snippet, auto-detecting the format.
     */
    static AssemblySnippet parseSnippet(String text, String archHint) {
        Format format = detectFormat(text);
        Architecture arch = archHint != null ? parseArch(archHint) : null;

        return switch (format) {
            case PERF_ANNOTATE -> PerfAnnotateParser.parse(text);
            case HOTSPOT_DEBUG -> {
                if (arch == null) arch = detectArchitecture(text);
                yield HotSpotDebugParser.parse(text, arch);
            }
        };
    }

    /**
     * Detect the assembly format from file content.
     */
    static Format detectFormat(String text) {
        // Perf annotate: has │ (box-drawing character) as column separator
        if (text.contains("│")) return Format.PERF_ANNOTATE;
        // HotSpot debug: has 0x... addresses with colon (weak heuristic but works for now)
        if (text.contains("0x") && text.contains(":")) return Format.HOTSPOT_DEBUG;
        // Default to HotSpot debug
        return Format.HOTSPOT_DEBUG;
    }

    /**
     * Detect the architecture from assembly content.
     */
    static Architecture detectArchitecture(String text) {
        // aarch64 indicators: registers without %, bracket memory syntax [base, #disp]
        if (text.contains("[x") || text.contains("[w") || text.contains("sxtw")
            || text.contains("sshll") || text.contains("b.lt")) {
            return Architecture.AARCH64;
        }
        // x86 indicators: % prefix on registers, parenthesized memory syntax
        if (text.contains("%r") || text.contains("%xmm") || text.contains("%ymm")
            || text.contains("(%r")) {
            return Architecture.X86_64;
        }
        return Architecture.X86_64; // default
    }

    enum Format { HOTSPOT_DEBUG, PERF_ANNOTATE }

    private static View parseView(String s) {
        return switch (s.toLowerCase().replace('-', '_')) {
            case "cheat_sheet", "cheatsheet", "cheat" -> View.CHEAT_SHEET;
            case "literal", "lit" -> View.LITERAL;
            case "explained", "explain" -> View.EXPLAINED;
            default -> null;
        };
    }

    private static Architecture parseArch(String s) {
        return switch (s.toLowerCase()) {
            case "x86_64", "x86", "amd64" -> Architecture.X86_64;
            case "aarch64", "arm64", "arm" -> Architecture.AARCH64;
            default -> throw new IllegalArgumentException("Unknown architecture: " + s);
        };
    }

    private static String deriveClassName(String filePath) {
        String name = Path.of(filePath).getFileName().toString();
        // Remove extension
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        // Replace non-identifier characters
        name = name.replaceAll("[^a-zA-Z0-9]", "_");
        // Capitalize first letter
        if (!name.isEmpty() && Character.isLowerCase(name.charAt(0))) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        // Prefix with underscore if starts with digit
        if (!name.isEmpty() && Character.isDigit(name.charAt(0))) {
            name = "_" + name;
        }
        return name.isEmpty() ? "Assembly" : name;
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage: nica [options] <assembly-file>");
        out.println();
        out.println("Translates disassembled JIT compiler output into readable Java code.");
        out.println();
        out.println("Options:");
        out.println("  --view <view>       Output view: cheat-sheet, literal, explained (default: literal)");
        out.println("  --arch <arch>       Architecture: x86_64, aarch64 (auto-detected if omitted)");
        out.println("  --class-name <name> Generated class name (default: derived from file name)");
        out.println("  -o <file>           Write output to file instead of stdout");
        out.println("  --help, -h          Show this help message");
        out.println();
        out.println("Supported input formats:");
        out.println("  - HotSpot fast-debug disassembly (x86_64 and aarch64)");
        out.println("  - perf annotate output (GraalVM native image, x86_64)");
    }
}

# Contributing to Nica

## Prerequisites

- **Java 27** (EA builds are fine)
- **Maven 3.9+**
- **Git 2.13+** (for submodule support)

## First-Time Setup

Nica depends on [Sea of Nodes / Simple](https://github.com/SeaOfNodes/Simple),
which has no published Maven artifacts. It's included as a Git submodule and
must be built locally before you can build Nica.

### 1. Clone with submodules

```bash
git clone --recurse-submodules https://github.com/your-org/nica.git
cd nica
```

If you already cloned without `--recurse-submodules`, initialize the submodule:

```bash
git submodule update --init --recursive
```

### 2. Build Simple locally

Simple's chapters all share the same Maven artifactId, so you cannot build
from the root POM. Instead, install the parent POM first, then build chapter14
(which contains the IR nodes Nica depends on):

```bash
# Install the parent POM (no code, just the POM itself)
cd lib/simple
mvn install -DskipTests -N

# Build and install chapter14
cd chapter14
mvn install -DskipTests

# Return to the Nica root
cd ../../..
```

After this, `com.seaofnodes:chapter14:1.0` is in your local Maven repository
(`~/.m2/repository/com/seaofnodes/chapter14/`).

> **Why chapter14?** It's the earliest chapter with all the node types Nica
> needs (Add, Mul, Xor, Shl, Sar, Load, If, Loop, Bool, Constant) plus
> IRPrinter for debugging — without the heavy CodeGen machinery of later
> chapters.

### 3. Build and test Nica

```bash
mvn verify
```

This compiles the project, runs all tests, and verifies everything is wired
correctly. You should see `BUILD SUCCESS`.

## Day-to-Day Development

### Running tests

```bash
mvn test                  # Run all tests
mvn test -pl .            # Run only Nica tests (skip Simple)
mvn test -Dtest=FooTest   # Run a specific test class
```

### Rebuilding after Simple changes

If you update the Simple submodule (see below), rebuild it:

```bash
cd lib/simple/chapter14
mvn install -DskipTests
cd ../../..
mvn compile   # Verify Nica still compiles against the new Simple
```

## Working with the Git Submodule

### What is a submodule?

A Git submodule is a pointer to a specific commit in another repository.
When you `git clone --recurse-submodules`, Git checks out that exact commit
into `lib/simple/`. The pointer is stored in the `.gitmodules` file and
tracked by Git.

**Key concept:** the submodule is pinned to a commit, not a branch. When
Simple's `main` branch advances, your local copy does not change until you
explicitly update it.

### Checking submodule status

```bash
git submodule status
```

This shows the pinned commit hash and whether your local copy matches:

```
 0f32001b466d... lib/simple (heads/main)     # ✓ clean, at pinned commit
+a1b2c3d4e5f6... lib/simple (heads/main)     # modified locally (uncommitted)
-0f32001b466d... lib/simple                   # not initialized (run git submodule update --init)
```

### Updating Simple to a newer commit

When you want to pick up changes from Simple's upstream:

```bash
cd lib/simple
git fetch origin
git checkout origin/main    # or a specific commit/tag
cd ../..

# Rebuild Simple
cd lib/simple
mvn install -DskipTests -N
cd chapter25
mvn install -DskipTests
cd ../../..

# Verify Nica still works
mvn verify

# Commit the submodule pointer update
git add lib/simple
git commit -m "chore: update Simple submodule to <commit>"
```

### Common submodule pitfalls

**Problem: `lib/simple/` is empty after cloning**
```bash
git submodule update --init --recursive
```

**Problem: `mvn compile` fails with "Could not find artifact com.seaofnodes:simple:pom:1.0"**

You need to install Simple's parent POM:
```bash
cd lib/simple && mvn install -DskipTests -N && cd ../..
```

**Problem: `mvn compile` fails with "Could not find artifact com.seaofnodes:chapter14:jar:1.0"**

You need to build chapter14:
```bash
cd lib/simple/chapter14 && mvn install -DskipTests && cd ../../..
```

**Problem: "DuplicateProjectException" when building Simple from root**

This is expected — all Simple chapters share the same artifactId. Always build
from `lib/simple/chapter14/`, never from `lib/simple/`.

**Problem: submodule shows as "modified" in `git status` but you didn't change it**

This usually happens when you `cd lib/simple && git checkout` a different commit.
Either commit the update (`git add lib/simple`) or reset to the pinned commit:
```bash
git submodule update --init
```

## Project Structure

```
nica/
├── .github/workflows/ci.yml   # GitHub Actions CI
├── lib/simple/                 # Simple IR (Git submodule)
├── src/
│   ├── main/java/org/mendrugo/nica/
│   │   ├── Nica.java           # Entry point
│   │   ├── asm/                # Assembly instruction model
│   │   ├── parser/             # Assembly parsers
│   │   ├── semantics/          # Instruction semantics
│   │   ├── ir/                 # Simple IR graph builder
│   │   ├── cheatsheet/         # Cheat Sheet view generator
│   │   ├── codegen/            # Java code generators (Literal, Explained)
│   │   └── recipe/             # Pattern matcher and recipes
│   └── test/
│       ├── java/org/mendrugo/nica/
│       └── resources/          # Test assembly snippets
├── docs/ideas/                 # Design documents
├── tasks/                      # Implementation plan and task list
├── pom.xml
├── CONTRIBUTING.md             # ← You are here
└── README.md
```

## Code Style

- Java 27 with `--enable-preview` enabled
- Use modern Java features: records, sealed interfaces, pattern matching in `switch`
- No Javadoc required on private methods, but public API should be documented
- Tests use JUnit 5

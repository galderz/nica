# Simple Issue: `_print1` is package-private, preventing Node subclasses in other packages

## Summary

`Node._print1(StringBuilder, BitSet)` has package-private (default) visibility in chapters 2 through 24. This prevents creating `Node` subclasses in packages outside `com.seaofnodes.simple.node`, since the subclass cannot override the abstract method.

Chapter 25 fixes this by declaring `_print1` as `public`, suggesting this was already recognized as an issue.

## Affected Chapters

- **Chapters 2–24**: `_print1` is package-private
- **Chapter 25**: Fixed — `abstract public StringBuilder _print1(...)`

## Details

In `Node.java` (chapter 14, line 109):

```java
abstract StringBuilder _print1(StringBuilder sb, BitSet visited);
```

No access modifier = package-private. Any class extending `Node` outside of `com.seaofnodes.simple.node` gets:

```
error: <SubClass> is not abstract and does not override abstract method
       _print1(java.lang.StringBuilder,java.util.BitSet) in com.seaofnodes.simple.node.Node
```

## Reproducer

Save as `external/pkg/MyNode.java` and compile against chapter 14:

```java
package external.pkg;

import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.BitSet;

public class MyNode extends Node {
    public MyNode(Node input) {
        super(null, input);
    }

    @Override public String label() { return "MyNode"; }
    @Override public Type compute() { return TypeInteger.BOT; }
    @Override public Node idealize() { return null; }

    // This CANNOT be declared because _print1 is package-private in Node:
    // @Override
    // StringBuilder _print1(StringBuilder sb, BitSet visited) {
    //     return sb.append("MyNode");
    // }
}
```

Compile:
```bash
javac -cp chapter14-1.0.jar external/pkg/MyNode.java
```

Result:
```
error: MyNode is not abstract and does not override abstract method
       _print1(StringBuilder,BitSet) in Node
```

## Suggested Fix

Change the visibility in all chapters to match chapter 25:

```java
// Before (chapters 2-24):
abstract StringBuilder _print1(StringBuilder sb, BitSet visited);

// After:
abstract public StringBuilder _print1(StringBuilder sb, BitSet visited);
```

Or, more conventionally:

```java
public abstract StringBuilder _print1(StringBuilder sb, BitSet visited);
```

## Workaround (used by Nica)

Place the subclass in `com.seaofnodes.simple.node` to get package-private access:

```java
package com.seaofnodes.simple.node;

public class ParamNode extends Node {
    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append("?param");
    }
    // ...
}
```

This works but forces an external project to inject classes into Simple's package, which is undesirable.

## Impact

This prevents using Simple as a library for building custom IR graphs with user-defined node types — which is exactly what Simple is designed to teach.

# Simple Issue: `Node.equals()`/`hashCode()` are `final` and structure-based, collapsing distinct nodes in standard collections

## Summary

`Node` overrides `equals()` and `hashCode()` as `final` methods implementing structural equality (same class, same inputs, same `eq()` result). This is correct for GVN (Global Value Numbering) inside the compiler, but it means standard Java collections like `HashMap<Node, V>` silently merge distinct `Node` instances that happen to be structurally identical.

This is particularly problematic for leaf nodes (like `ConstantNode` or custom parameter nodes) that have the same structure (same input = `START`) but represent different program values.

## Affected Chapters

All chapters from chapter 4 onward (when `equals`/`hashCode` were introduced for GVN).

## Details

In `Node.java` (chapter 14, lines 486–529):

```java
@Override public final boolean equals(Object o) {
    if (o == this) return true;
    if (o.getClass() != getClass()) return false;
    Node n = (Node) o;
    int len = _inputs.size();
    if (len != n._inputs.size()) return false;
    for (int i = 0; i < len; i++)
        if (in(i) != n.in(i))
            return false;
    return eq(n);
}

// Subclasses add extra checks
boolean eq(Node n) { return true; }

@Override public final int hashCode() {
    if (_hash != 0) return _hash;
    int hash = hash();
    for (Node n : _inputs)
        if (n != null)
            hash = hash ^ (hash << 17) ^ (hash >> 13) ^ n._nid;
    if (hash == 0) hash = 0xDEADBEEF;
    return (_hash = hash);
}

// Subclasses add extra hash info
int hash() { return 0; }
```

Key observations:
1. Both methods are `final` — subclasses cannot override them
2. `eq()` and `hash()` are package-private — subclasses outside the package cannot override them either (same issue as `_print1`)
3. Default `eq()` returns `true` and default `hash()` returns `0`, meaning **any two nodes of the same class with the same inputs are considered equal**

## Reproducer

```java
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.*;

public class NodeEqualsIssue {
    public static void main(String[] args) {
        Node.reset();
        Node._disablePeephole = true;
        Parser.START = new StartNode(new Type[]{Type.CONTROL, TypeInteger.BOT});
        Parser.START._type = Parser.START.compute();

        // Create two distinct ConstantNode(42) instances
        var c1 = new ConstantNode(TypeInteger.constant(42));
        c1._type = c1.compute();
        var c2 = new ConstantNode(TypeInteger.constant(42));
        c2._type = c2.compute();

        // They are different objects
        System.out.println("c1 == c2 (identity): " + (c1 == c2));           // false
        // But equals() says they're the same
        System.out.println("c1.equals(c2): " + c1.equals(c2));              // true
        System.out.println("c1.hashCode() == c2.hashCode(): " +
            (c1.hashCode() == c2.hashCode()));                               // true

        // HashMap collapses them into one entry
        var map = new HashMap<Node, String>();
        map.put(c1, "first");
        map.put(c2, "second");
        System.out.println("HashMap size: " + map.size());                   // 1 (!)
        System.out.println("map.get(c1): " + map.get(c1));                   // "second"

        // IdentityHashMap preserves both
        var imap = new IdentityHashMap<Node, String>();
        imap.put(c1, "first");
        imap.put(c2, "second");
        System.out.println("IdentityHashMap size: " + imap.size());          // 2
    }
}
```

Compile and run:
```bash
javac -cp chapter14-1.0.jar NodeEqualsIssue.java
java -cp .:chapter14-1.0.jar NodeEqualsIssue
```

Output:
```
c1 == c2 (identity): false
c1.equals(c2): true
c1.hashCode() == c2.hashCode(): true
HashMap size: 1
map.get(c1): second
IdentityHashMap size: 2
```

## Why This Matters

This is intentional for GVN — the compiler *wants* structurally identical nodes to be deduplicated. But it's a sharp edge for anyone using `Node` as a key in standard collections:

- `HashMap<Node, V>` silently loses entries
- `HashSet<Node>` silently deduplicates distinct nodes
- `Map.of(n1, v1, n2, v2)` throws `IllegalArgumentException` for "duplicate keys"

## Workaround (used by Nica)

Use `IdentityHashMap` whenever nodes are used as map keys:

```java
var bindings = new IdentityHashMap<ParamNode, Integer>();
```

## Suggestion

This is likely working-as-designed for Simple's internal use. However, a documentation note in `Node.java` warning that `equals()`/`hashCode()` implement structural equality (for GVN) and that `IdentityHashMap` should be used when identity-keying is needed would help library consumers.

Additionally, making `eq()` and `hash()` `protected` (instead of package-private) would allow subclasses in other packages to participate in structural equality correctly — which is needed if issue #001 (`_print1` visibility) is also fixed.

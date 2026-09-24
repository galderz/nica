# Simple Issue: chapter25's `pom.xml` has wrong artifactId (`chapter23` instead of `chapter25`)

## Summary

The `pom.xml` in the `chapter25/` directory declares `<artifactId>chapter23</artifactId>` instead of `<artifactId>chapter25</artifactId>`. This causes the Maven artifact to be installed under the wrong name, making it confusing for downstream consumers.

## Details

File: `chapter25/pom.xml`

```xml
<project>
    <parent>
        <groupId>com.seaofnodes</groupId>
        <artifactId>simple</artifactId>
        <version>1.0</version>
    </parent>
    <artifactId>chapter23</artifactId>   <!-- BUG: should be chapter25 -->
    <packaging>jar</packaging>
    <name>Chapter 23</name>              <!-- Also wrong -->
</project>
```

For comparison, chapter14's pom.xml is correct:

```xml
<project>
    <parent>
        <groupId>com.seaofnodes</groupId>
        <artifactId>simple</artifactId>
        <version>1.0</version>
    </parent>
    <artifactId>chapter14</artifactId>   <!-- Correct -->
    <packaging>jar</packaging>
    <name>Chapter 14</name>              <!-- Correct -->
</project>
```

## Reproducer

```bash
git clone https://github.com/SeaOfNodes/Simple.git
cd Simple

# Install parent POM
mvn install -DskipTests -N

# Build chapter25
cd chapter25
mvn install -DskipTests

# Check where the artifact was installed
ls ~/.m2/repository/com/seaofnodes/chapter23/1.0/
# → chapter23-1.0.jar  (contains chapter25 code!)

ls ~/.m2/repository/com/seaofnodes/chapter25/
# → No such file or directory
```

## Consequences

1. **Confusing dependency declaration**: A project depending on chapter25 must use:
   ```xml
   <dependency>
       <groupId>com.seaofnodes</groupId>
       <artifactId>chapter23</artifactId>  <!-- Actually chapter25 code -->
       <version>1.0</version>
   </dependency>
   ```

2. **Shadowing**: If both chapter23 and chapter25 are built, only one can exist in the local Maven repository since they share the same coordinates (`com.seaofnodes:chapter23:1.0`). Whichever was built last wins.

3. **Reactor conflict**: Building from the root `pom.xml` fails with `DuplicateProjectException` because both `chapter23/` and `chapter25/` declare `artifactId=chapter23`:
   ```
   [ERROR] Project 'com.seaofnodes:chapter23:1.0' is duplicated in the reactor
   ```

## Suggested Fix

```xml
<!-- chapter25/pom.xml -->
<artifactId>chapter25</artifactId>
<name>Chapter 25</name>
```

## Additional Note

The root `pom.xml` lists all chapters as reactor modules. Since all chapters share the parent `groupId=com.seaofnodes` and most have unique artifactIds (chapter01 through chapter22, chapter24), only chapter25 has this naming collision. A full audit of all chapter POMs would confirm whether any other chapters have similar issues:

```bash
for d in chapter*/; do
  dir_name=$(basename "$d")
  artifact=$(grep '<artifactId>' "$d/pom.xml" | grep -v 'simple' | head -1 | sed 's/.*<artifactId>//;s|</artifactId>||;s/ //g')
  if [ "$dir_name" != "$artifact" ]; then
    echo "MISMATCH: $dir_name/ has artifactId=$artifact"
  fi
done
```

Expected output:
```
MISMATCH: chapter25/ has artifactId=chapter23
```

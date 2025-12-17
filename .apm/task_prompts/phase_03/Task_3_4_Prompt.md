---
task_ref: "Task 3.4 - QueryWrapper Scanner Implementation"
agent_assignment: "Agent_Static_Scanner"
memory_log_path: ".apm/Memory/Phase_03_Static_Scanner/Task_3_4_QueryWrapper_Scanner_Implementation.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: QueryWrapper Scanner Implementation

## Task Reference
Implementation Plan: **Task 3.4 - QueryWrapper Scanner Implementation** assigned to **Agent_Static_Scanner**

## Context from Dependencies
Based on your Task 3.1 work:

**Core Framework Outputs:**
- Use `WrapperScanner` interface from `com.footstone.sqlguard.scanner.parser` package
- Use `WrapperUsage` class from `com.footstone.sqlguard.scanner.model` with fields: filePath, methodName, lineNumber, wrapperType, needsRuntimeCheck
- Set `needsRuntimeCheck=true` for all WrapperUsage entries (static analysis insufficient for dynamic queries)

**Integration Approach:**
Your QueryWrapperScanner will implement the WrapperScanner interface contract:
```java
public interface WrapperScanner {
    List<WrapperUsage> scan(File projectRoot) throws IOException;
}
```

SqlScanner will delegate project root to your scanner for recursive Java file processing.

## Objective
Implement MyBatis-Plus QueryWrapper usage scanner detecting QueryWrapper, LambdaQueryWrapper, UpdateWrapper, and LambdaUpdateWrapper instantiations in Java source code, marking usage locations for runtime interception since static analysis cannot determine dynamic query conditions, producing WrapperUsage entries for scan reports with performance optimization for large codebases.

## Detailed Instructions
Complete this task in **4 exchanges, one step per response**. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: Basic Wrapper Detection TDD

**Test First:**
Write test class `QueryWrapperScannerTest` in `com.footstone.sqlguard.scanner.wrapper` package covering:
- `testQueryWrapperDetection_shouldCreateUsage()` - Java file with `new QueryWrapper<User>()` creates WrapperUsage
- `testLambdaQueryWrapperDetection_shouldCreateUsage()` - Detect `new LambdaQueryWrapper<>()`
- `testUpdateWrapperDetection_shouldCreateUsage()` - Detect `new UpdateWrapper<>()`
- `testLambdaUpdateWrapperDetection_shouldCreateUsage()` - Detect `new LambdaUpdateWrapper<>()`
- `testNonWrapperObjectCreation_shouldSkip()` - new ArrayList(), new User() should not create WrapperUsage
- `testMultipleWrappersInFile_shouldDetectAll()` - File with 3 wrapper creations creates 3 WrapperUsage

**Sample Test Java File:**
Create test file `src/test/resources/services/UserService.java`:
```java
package com.example.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

public class UserService {
    public List<User> findByName(String name) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("name", name);
        return userMapper.selectList(wrapper);
    }

    public User findById(Long id) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getId, id);
        return userMapper.selectOne(wrapper);
    }
}
```

**Then Implement:**
1. Add JavaParser 3.25.7 dependency to `sql-scanner-core/pom.xml` (if not already added by Task 3.3)
2. Create `QueryWrapperScanner` class in `com.footstone.sqlguard.scanner.wrapper` package implementing WrapperScanner
3. Implement `scan(File projectRoot)` method:
   - Recursively find all .java files: `Files.walk(projectRoot.toPath()).filter(p -> p.toString().endsWith(".java") && p.toString().contains("src/main/java"))`
   - Parse each Java file: `CompilationUnit cu = StaticJavaParser.parse(file)`
   - Find all object creation expressions: `cu.findAll(ObjectCreationExpr.class)`
   - For each ObjectCreationExpr:
     - Get type name: `expr.getType().getNameAsString()`
     - Check if matches wrapper types: `Set.of("QueryWrapper", "LambdaQueryWrapper", "UpdateWrapper", "LambdaUpdateWrapper")`
     - Extract enclosing method: `expr.findAncestor(MethodDeclaration.class).map(m -> m.getNameAsString()).orElse("unknown")`
     - Get line number: `expr.getBegin().get().line`
     - Create WrapperUsage(filePath=file.getAbsolutePath(), methodName=enclosingMethod, lineNumber=lineNum, wrapperType=typeName, needsRuntimeCheck=true)
   - Collect all WrapperUsage into List
   - Return List<WrapperUsage>

**Constraints:**
- Only scan files under `src/main/java` (exclude test files)
- Skip files in `target/`, `build/`, `.git/` directories
- Handle files without package declarations gracefully
- Log warnings for parse errors but continue processing other files

### Step 2: Package Verification TDD

**Test First:**
Write test class `WrapperTypeVerificationTest` covering:
- `testMyBatisPlusWrapper_shouldDetect()` - Wrapper from com.baomidou.mybatisplus.core.conditions detected
- `testCustomWrapperClass_shouldNotDetect()` - Custom class named QueryWrapper in different package ignored
- `testFullyQualifiedName_shouldVerify()` - Verify wrapper package is MyBatis-Plus package
- `testSymbolResolutionFailure_shouldFallbackToSimpleName()` - If symbol resolution fails, use simple name matching with warning

**Sample Test Java File:**
Create `src/test/resources/services/MixedService.java`:
```java
package com.example.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.custom.QueryWrapper as CustomWrapper; // Custom class, should NOT detect

public class MixedService {
    public void useMyBatisPlusWrapper() {
        QueryWrapper<User> wrapper = new QueryWrapper<>(); // SHOULD detect
    }

    public void useCustomWrapper() {
        CustomWrapper wrapper = new CustomWrapper(); // Should NOT detect
    }
}
```

**Then Implement:**
Add package verification logic to QueryWrapperScanner:

1. **Define MyBatis-Plus wrapper package:**
   ```java
   private static final String MP_WRAPPER_PACKAGE = "com.baomidou.mybatisplus.core.conditions";
   private static final Set<String> WRAPPER_SIMPLE_NAMES = Set.of(
       "QueryWrapper", "LambdaQueryWrapper",
       "UpdateWrapper", "LambdaUpdateWrapper"
   );
   ```

2. **Verify wrapper type with symbol resolution:**
   ```java
   private boolean isMyBatisPlusWrapper(ObjectCreationExpr expr) {
       String simpleName = expr.getType().getNameAsString();

       // Quick check: simple name must match
       if (!WRAPPER_SIMPLE_NAMES.contains(simpleName)) {
           return false;
       }

       // Try symbol resolution for package verification
       try {
           ResolvedReferenceType resolvedType = expr.getType().resolve();
           String qualifiedName = resolvedType.getQualifiedName();
           return qualifiedName.startsWith(MP_WRAPPER_PACKAGE);
       } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
           // Symbol resolution failed (missing dependencies)
           // Fall back to simple name matching with warning
           log.warn("Symbol resolution failed for {}, using simple name matching", simpleName);
           return true; // Assume MyBatis-Plus wrapper
       }
   }
   ```

3. Update scan() to use isMyBatisPlusWrapper() filter before creating WrapperUsage

**Constraints:**
- Symbol resolution requires MyBatis-Plus dependency on classpath (may not be available)
- Fallback to simple name matching if resolution fails
- Log warning when falling back to simple name matching
- Prevent false positives from custom classes with same name

### Step 3: Method Context Extraction TDD

**Test First:**
Write test class `MethodContextExtractionTest` covering:
- `testMethodLevelWrapper_shouldExtractMethodName()` - Wrapper in method body extracts method name
- `testConstructorWrapper_shouldExtractInit()` - Wrapper in constructor extracts "<init>"
- `testStaticBlockWrapper_shouldExtractStatic()` - Wrapper in static block extracts "<static>"
- `testFieldInitializerWrapper_shouldExtractFieldName()` - Wrapper in field initializer extracts field name
- `testNestedMethodWrapper_shouldExtractOuterMethod()` - Wrapper in lambda/inner class extracts outer method

**Sample Test Java File:**
Create `src/test/resources/services/ContextTestService.java`:
```java
package com.example.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

public class ContextTestService {
    // Field initializer
    private QueryWrapper<User> fieldWrapper = new QueryWrapper<>();

    // Static block
    static {
        QueryWrapper<Config> staticWrapper = new QueryWrapper<>();
    }

    // Constructor
    public ContextTestService() {
        QueryWrapper<User> constructorWrapper = new QueryWrapper<>();
    }

    // Method
    public void findUsers() {
        QueryWrapper<User> methodWrapper = new QueryWrapper<>();
    }

    // Lambda expression
    public void processUsers() {
        users.forEach(u -> {
            QueryWrapper<User> lambdaWrapper = new QueryWrapper<>();
        });
    }
}
```

**Then Implement:**
Add method context extraction logic:

1. **Extract enclosing method name:**
   ```java
   private String extractEnclosingMethod(ObjectCreationExpr expr) {
       // Try method declaration first
       Optional<MethodDeclaration> method = expr.findAncestor(MethodDeclaration.class);
       if (method.isPresent()) {
           return method.get().getNameAsString();
       }

       // Try constructor
       Optional<ConstructorDeclaration> constructor = expr.findAncestor(ConstructorDeclaration.class);
       if (constructor.isPresent()) {
           return "<init>";
       }

       // Try static initializer
       Optional<InitializerDeclaration> initializer = expr.findAncestor(InitializerDeclaration.class);
       if (initializer.isPresent()) {
           return initializer.get().isStatic() ? "<static>" : "<instance_init>";
       }

       // Try field declaration
       Optional<FieldDeclaration> field = expr.findAncestor(FieldDeclaration.class);
       if (field.isPresent()) {
           return field.get().getVariable(0).getNameAsString() + "_initializer";
       }

       return "unknown";
   }
   ```

2. Update WrapperUsage creation to use extractEnclosingMethod()

**Constraints:**
- Handle all Java contexts where wrapper can be instantiated
- Provide meaningful context names for debugging
- Nested contexts should extract outermost method/constructor
- Lambda expressions should extract containing method, not lambda itself

### Step 4: Performance and Filtering Testing

**Create Performance Test:**
Write `QueryWrapperScannerPerformanceTest` with large-scale simulation:

1. **Large project simulation:**
   - Generate 1000 Java files with varying wrapper usage
   - Some with wrappers, most without
   - Execute scan() and measure execution time
   - **Target:** Complete in <10 seconds for 1000 files
   - Verify memory usage bounded (no JavaParser AST retention leaks)

2. **Parallel processing test (optional optimization):**
   - Test sequential processing baseline
   - Implement parallel stream processing: `Files.walk().parallel()`
   - Measure speedup vs sequential
   - Verify thread-safety (no race conditions)

**Create Filtering Test:**
Write `ScanFilteringTest` covering:
- `testSourceDirectoryFiltering_shouldIncludeMainOnly()` - Only process src/main/java, exclude src/test/java
- `testTargetDirectoryFiltering_shouldExcludeBuild()` - Exclude target/, build/, .git/ directories
- `testGeneratedCodeFiltering_shouldExcludeGenerated()` - Exclude target/generated-sources
- `testNonJavaFiles_shouldSkip()` - Skip .class, .xml, .properties files

**Create False Positive Prevention Test:**
Write `FalsePositivePreventionTest` covering:
- `testNonWrapperObjectCreation_shouldNotDetect()` - new ArrayList<>(), new HashMap<>(), new User() ignored
- `testWrapperTestClass_shouldBeExcluded()` - Test classes in src/test/java not scanned
- `testPreciseTypeMatching_shouldAvoidPartialMatch()` - QueryWrapperTest, CustomQueryWrapper not detected
- `testImportVerification_shouldCheckImports()` - Verify import statement is from MyBatis-Plus package

**Edge Cases:**
- Empty Java files (no classes)
- Files with parse errors (syntax errors)
- Very long files (>10000 lines)
- Files with many wrapper creations (>100 in one file)
- Unicode characters in file paths
- Symbolic links in directory structure

**Run Tests:**
Execute `mvn test -Dtest=*QueryWrapper*` ensuring all wrapper scanner tests pass (expect 30+ tests).

## Expected Output

**Deliverables:**
1. `QueryWrapperScanner` class implementing WrapperScanner interface
2. Recursive Java file discovery with Files.walk() traversal
3. JavaParser-based wrapper instantiation detection via ObjectCreationExpr AST nodes
4. Package verification preventing false positives from custom wrapper classes
5. Method context extraction (method/constructor/static block/field initializer)
6. Performance optimization for large codebases (1000+ Java files)
7. Filtering logic excluding test files and generated code
8. 30+ comprehensive tests covering detection, verification, context extraction, performance, filtering

**Success Criteria:**
- All MyBatis-Plus wrapper types detected (QueryWrapper, LambdaQueryWrapper, UpdateWrapper, LambdaUpdateWrapper)
- Package verification prevents false positives from custom classes
- Method context accurately extracted for all Java contexts
- Performance acceptable for large projects (<10s for 1000 files)
- Test files and generated code excluded from scanning
- All edge cases handled gracefully (parse errors, empty files, Unicode paths)
- All tests passing (no failures, no errors)

**File Locations:**
- Implementation: `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/wrapper/QueryWrapperScanner.java`
- Unit tests: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/wrapper/QueryWrapperScannerTest.java`
- Verification test: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/wrapper/WrapperTypeVerificationTest.java`
- Context extraction test: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/wrapper/MethodContextExtractionTest.java`
- Performance test: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/wrapper/QueryWrapperScannerPerformanceTest.java`
- Filtering test: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/wrapper/ScanFilteringTest.java`
- False positive test: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/wrapper/FalsePositivePreventionTest.java`
- Test resources: `sql-scanner-core/src/test/resources/services/*.java` (5+ files)

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_03_Static_Scanner/Task_3_4_QueryWrapper_Scanner_Implementation.md`

Follow `.apm/guides/Memory_Log_Guide.md` instructions including:
- Task completion status
- All deliverables created with file paths
- Test results (total count, pass/fail)
- Package verification strategy
- Performance optimization approach
- Filtering rules applied
- Any JavaParser symbol resolution learnings

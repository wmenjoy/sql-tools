---
task_ref: "Task 3.3 - Annotation Parser Implementation"
agent_assignment: "Agent_Static_Scanner"
memory_log_path: ".apm/Memory/Phase_03_Static_Scanner/Task_3_3_Annotation_Parser_Implementation.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: Annotation Parser Implementation

## Task Reference
Implementation Plan: **Task 3.3 - Annotation Parser Implementation** assigned to **Agent_Static_Scanner**

## Context from Dependencies
Based on your Task 3.1 work:

**Core Framework Outputs:**
- Use `SqlParser` interface from `com.footstone.sqlguard.scanner.parser` package
- Use `SqlEntry` class from `com.footstone.sqlguard.scanner.model` with fields: source, filePath, mapperId, sqlType, rawSql, lineNumber, dynamic flag, sqlVariants list
- Use `SourceType.ANNOTATION` enum value for all annotation-based entries
- Implement fail-fast validation matching SqlEntry constructor requirements

**Integration Approach:**
Your AnnotationParser will implement the SqlParser interface contract:
```java
public interface SqlParser {
    List<SqlEntry> parse(File file) throws IOException, ParseException;
}
```

SqlScanner will delegate Java files (`*.java` under `src/main/java`) to your parser.

## Objective
Implement comprehensive MyBatis annotation-based SQL parser extracting SQL statements from @Select, @Update, @Delete, @Insert annotations in Java mapper interfaces, handling multi-line SQL strings, annotation value arrays, and producing SqlEntry instances with accurate line numbers and mapperId references for static analysis.

## Detailed Instructions
Complete this task in **4 exchanges, one step per response**. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: Basic Annotation Parsing TDD

**Test First:**
Write test class `AnnotationParserTest` in `com.footstone.sqlguard.scanner.parser.impl` package covering:
- `testSelectAnnotation_shouldCreateSqlEntry()` - Parse @Select annotation, verify SqlEntry created with correct mapperId, SQL, line number
- `testMultipleAnnotations_shouldCreateMultipleEntries()` - Parse Java interface with @Select, @Update, @Delete, @Insert methods, verify all extracted
- `testNamespaceExtraction_shouldUseClassName()` - Verify fully qualified class name used as namespace for mapperId (format: "com.example.UserMapper.getUserById")
- `testLineNumberExtraction_shouldBeAccurate()` - Verify line numbers match Java file positions

**Sample Test Java File:**
Create test file `src/test/resources/mappers/SimpleMapper.java`:
```java
package com.example;

import org.apache.ibatis.annotations.*;

public interface SimpleMapper {
    @Select("SELECT * FROM user WHERE id = #{id}")
    User getUserById(Long id);

    @Update("UPDATE user SET name = #{name} WHERE id = #{id}")
    int updateUser(User user);
}
```

**Then Implement:**
1. Add JavaParser 3.25.7 dependency to `sql-scanner-core/pom.xml`
2. Create `AnnotationParser` class in `com.footstone.sqlguard.scanner.parser.impl` package implementing SqlParser
3. Implement `parse(File file)` method:
   - Read file with JavaParser: `CompilationUnit cu = StaticJavaParser.parse(file)`
   - Get package name: `String packageName = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("")`
   - Find all class declarations: `cu.findAll(ClassOrInterfaceDeclaration.class)`
   - For each class, build namespace: `String namespace = packageName + "." + className`
   - Find all method declarations: `classDecl.findAll(MethodDeclaration.class)`
   - For each method:
     - Check for SQL annotations: @Select, @Update, @Delete, @Insert
     - Extract annotation value: `annotation.asNormalAnnotationExpr().getPairs().stream().filter(p -> p.getNameAsString().equals("value")).findFirst()`
     - Determine SqlCommandType from annotation type (@Select→SELECT, @Update→UPDATE, etc.)
     - Extract SQL string from value (handle string literals and string arrays)
     - Get line number: `annotation.getBegin().get().line`
     - Build mapperId: `namespace + "." + methodName`
     - Create SqlEntry(source=SourceType.ANNOTATION, filePath=file.getAbsolutePath(), mapperId=mapperId, sqlType=commandType, rawSql=sql, lineNumber=lineNum, dynamic=false, sqlVariants=new ArrayList<>())
   - Return List<SqlEntry>

**Constraints:**
- MapperId format must be "fully.qualified.ClassName.methodName" matching MyBatis annotation runtime convention
- Line numbers must be accurate (JavaParser provides this via .getBegin().get().line)
- SQL text should be cleaned (remove quotes, handle escape sequences)
- Handle methods without SQL annotations gracefully (skip them)

### Step 2: Multi-Line SQL String Handling TDD

**Test First:**
Write test class `MultiLineSqlHandlingTest` covering:
- `testSingleLineString_shouldExtract()` - @Select("SELECT * FROM user") extracts correctly
- `testMultiLineArray_shouldConcatenate()` - @Select({"SELECT *", "FROM user"}) concatenates to single SQL
- `testValueAttribute_shouldExtract()` - @Select(value="SELECT...") extracts from named attribute
- `testMixedParameters_shouldExtractValue()` - @Select(value="SELECT...", fetchSize=100) extracts only SQL

**Sample Test Java File:**
Create `src/test/resources/mappers/MultiLineMapper.java`:
```java
package com.example;

import org.apache.ibatis.annotations.*;

public interface MultiLineMapper {
    @Select({
        "SELECT * FROM user",
        "WHERE name = #{name}",
        "ORDER BY id"
    })
    List<User> findByName(String name);

    @Update(value = "UPDATE user SET name = #{name}", timeout = 30)
    int updateUserName(String name, Long id);
}
```

**Then Implement:**
Add SQL string extraction helper methods to AnnotationParser:

1. **Extract annotation value method:**
   ```java
   private String extractSqlFromAnnotation(AnnotationExpr annotation) {
       if (annotation.isSingleMemberAnnotationExpr()) {
           // @Select("SQL") - single value
           return extractSqlValue(annotation.asSingleMemberAnnotationExpr().getMemberValue());
       } else if (annotation.isNormalAnnotationExpr()) {
           // @Select(value="SQL", ...) - named parameters
           return annotation.asNormalAnnotationExpr().getPairs().stream()
               .filter(p -> p.getNameAsString().equals("value"))
               .map(p -> extractSqlValue(p.getValue()))
               .findFirst()
               .orElse(null);
       }
       return null;
   }
   ```

2. **Extract SQL value (string or array):**
   ```java
   private String extractSqlValue(Expression expr) {
       if (expr.isStringLiteralExpr()) {
           // Single string: "SELECT * FROM user"
           return expr.asStringLiteralExpr().asString();
       } else if (expr.isArrayInitializerExpr()) {
           // String array: {"SELECT *", "FROM user"}
           return expr.asArrayInitializerExpr().getValues().stream()
               .filter(Expression::isStringLiteralExpr)
               .map(e -> e.asStringLiteralExpr().asString())
               .collect(Collectors.joining(" "));
       }
       return null;
   }
   ```

3. Update parse() method to use extractSqlFromAnnotation() for all SQL annotations

**Constraints:**
- Multi-line SQL arrays must be concatenated with space separator
- Named parameters (value=..., timeout=...) should extract only value attribute
- Escaped characters in strings should be preserved correctly
- Null/empty SQL should log warning and skip that method

### Step 3: Annotation Type Detection TDD

**Test First:**
Write test class `AnnotationTypeDetectionTest` covering:
- `testSelectAnnotation_shouldDetectSELECT()` - @Select maps to SqlCommandType.SELECT
- `testUpdateAnnotation_shouldDetectUPDATE()` - @Update maps to SqlCommandType.UPDATE
- `testDeleteAnnotation_shouldDetectDELETE()` - @Delete maps to SqlCommandType.DELETE
- `testInsertAnnotation_shouldDetectINSERT()` - @Insert maps to SqlCommandType.INSERT
- `testNonSqlAnnotation_shouldSkip()` - @Param, @ResultMap, @Options don't create SqlEntry
- `testMultipleAnnotationsOnMethod_shouldExtractAll()` - Method with multiple annotations (rare but possible)

**Sample Test Java File:**
Create `src/test/resources/mappers/ComplexMapper.java`:
```java
package com.example;

import org.apache.ibatis.annotations.*;

public interface ComplexMapper {
    @Select("SELECT * FROM user WHERE id = #{id}")
    @ResultMap("userResultMap")
    User getUserById(@Param("id") Long id);

    @Delete("DELETE FROM user WHERE id = #{id}")
    int deleteUser(Long id);

    @Insert("INSERT INTO user (name, email) VALUES (#{name}, #{email})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(User user);
}
```

**Then Implement:**
Add annotation detection logic to AnnotationParser:

1. **Define SQL annotation types:**
   ```java
   private static final Map<String, SqlCommandType> SQL_ANNOTATIONS = Map.of(
       "Select", SqlCommandType.SELECT,
       "Update", SqlCommandType.UPDATE,
       "Delete", SqlCommandType.DELETE,
       "Insert", SqlCommandType.INSERT
   );
   ```

2. **Detect SQL annotation type:**
   ```java
   private Optional<SqlCommandType> getSqlCommandType(AnnotationExpr annotation) {
       String annotationName = annotation.getNameAsString();
       return Optional.ofNullable(SQL_ANNOTATIONS.get(annotationName));
   }
   ```

3. **Filter and process only SQL annotations:**
   ```java
   for (MethodDeclaration method : methods) {
       for (AnnotationExpr annotation : method.getAnnotations()) {
           Optional<SqlCommandType> sqlType = getSqlCommandType(annotation);
           if (sqlType.isPresent()) {
               String sql = extractSqlFromAnnotation(annotation);
               if (sql != null && !sql.isEmpty()) {
                   // Create SqlEntry...
               }
           }
       }
   }
   ```

**Constraints:**
- Only process @Select, @Update, @Delete, @Insert annotations
- Ignore non-SQL annotations (@Param, @ResultMap, @Options, @Results, etc.)
- Handle fully qualified annotation names (org.apache.ibatis.annotations.Select)
- Handle methods with multiple annotations (extract SQL from SQL annotations only)

### Step 4: Comprehensive Testing

**Create Integration Test:**
Write `AnnotationParserIntegrationTest` with multiple test Java files in `src/test/resources/mappers/`:

1. **SimpleMapper.java** - Basic @Select, @Update, @Delete, @Insert
   - Verify 4 SqlEntry created
   - Verify source=SourceType.ANNOTATION
   - Verify dynamic=false (annotations are static)

2. **MultiLineMapper.java** - Multi-line SQL arrays
   - Verify SQL strings concatenated correctly
   - Verify line numbers point to annotation start

3. **ComplexMapper.java** - Mixed annotations
   - Verify only SQL annotations processed
   - Verify @Param, @Options don't create extra entries

4. **NestedClassMapper.java** - Nested interface with annotations
   - Verify namespace includes outer class name
   - Verify mapperId format correct

5. **EdgeCasesMapper.java** - Edge cases
   - Empty @Select("") - should log warning and skip
   - String concatenation: @Select("SELECT * " + "FROM user") - extract if literal, warn if complex
   - Escaped quotes: @Select("SELECT * FROM user WHERE name='O\\'Brien'")
   - Unicode characters in SQL

**Edge Cases:**
- Empty annotation value: @Select("") or @Select({})
- Null checks: methods without annotations
- Package-less classes: default package handling
- Multiple SQL annotations on same method (rare, but extract all)
- Line number accuracy: verify matches actual Java file line

**Run Tests:**
Execute `mvn test -Dtest=*Annotation*` ensuring all annotation parsing tests pass (expect 30+ tests).

## Expected Output

**Deliverables:**
1. `AnnotationParser` class implementing SqlParser interface
2. JavaParser 3.x-based annotation extraction with AST traversal
3. Multi-line SQL array concatenation logic
4. SQL annotation type detection (@Select/@Update/@Delete/@Insert)
5. 30+ comprehensive tests covering basic parsing, multi-line SQL, annotation types, edge cases
6. Sample test Java files (5+ mapper interfaces in src/test/resources/mappers/)

**Success Criteria:**
- All SQL annotations correctly extracted from Java mapper interfaces
- Multi-line SQL arrays concatenated with proper spacing
- Line numbers accurate pointing to annotation declarations
- MapperId format matches MyBatis convention (fully.qualified.ClassName.methodName)
- Non-SQL annotations ignored (@Param, @ResultMap, @Options)
- Edge cases handled gracefully (empty SQL, escaped quotes, Unicode)
- All tests passing (no failures, no errors)

**File Locations:**
- Implementation: `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/parser/impl/AnnotationParser.java`
- Unit tests: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/parser/impl/AnnotationParserTest.java`
- Multi-line test: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/parser/impl/MultiLineSqlHandlingTest.java`
- Type detection test: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/parser/impl/AnnotationTypeDetectionTest.java`
- Integration tests: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/parser/impl/AnnotationParserIntegrationTest.java`
- Test resources: `sql-scanner-core/src/test/resources/mappers/*.java` (5+ files)

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_03_Static_Scanner/Task_3_3_Annotation_Parser_Implementation.md`

Follow `.apm/guides/Memory_Log_Guide.md` instructions including:
- Task completion status
- All deliverables created with file paths
- Test results (total count, pass/fail)
- Multi-line SQL handling strategy
- Edge cases handled
- Any JavaParser API learnings

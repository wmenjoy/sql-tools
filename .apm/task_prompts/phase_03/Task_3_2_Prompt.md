---
task_ref: "Task 3.2 - XML Mapper Parser Implementation"
agent_assignment: "Agent_Static_Scanner"
memory_log_path: ".apm/Memory/Phase_03_Static_Scanner/Task_3_2_XML_Mapper_Parser_Implementation.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: XML Mapper Parser Implementation

## Task Reference
Implementation Plan: **Task 3.2 - XML Mapper Parser Implementation** assigned to **Agent_Static_Scanner**

## Context from Dependencies
Based on your Task 3.1 work:

**Core Framework Outputs:**
- Use `SqlParser` interface from `com.footstone.sqlguard.scanner.parser` package
- Use `SqlEntry` class from `com.footstone.sqlguard.scanner.model` with fields: source, filePath, mapperId, sqlType, rawSql, lineNumber, dynamic flag, sqlVariants list
- Use `SourceType.XML` enum value for all XML mapper entries
- Implement fail-fast validation matching SqlEntry constructor requirements

**Integration Approach:**
Your XmlMapperParser will implement the SqlParser interface contract:
```java
public interface SqlParser {
    List<SqlEntry> parse(File file) throws IOException, ParseException;
}
```

SqlScanner will delegate XML files (`*.xml` under `src/main/resources`) to your parser.

## Objective
Implement comprehensive MyBatis XML Mapper parser extracting SQL statements from mapper XML files, detecting dynamic tags (if/where/foreach/choose), generating SQL variants for dynamic scenarios, and producing SqlEntry instances with accurate line numbers and mapperId references for static analysis.

## Detailed Instructions
Complete this task in **4 exchanges, one step per response**. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: Basic XML Parsing TDD

**Test First:**
Write test class `XmlMapperParserTest` in `com.footstone.sqlguard.scanner.parser.impl` package covering:
- `testSimpleSelect_shouldCreateSqlEntry()` - Parse simple SELECT statement, verify SqlEntry created with correct mapperId, SQL, line number
- `testMultipleStatements_shouldCreateMultipleEntries()` - Parse XML with select/update/delete/insert, verify all extracted
- `testNamespaceExtraction_shouldPrefixMapperId()` - Verify namespace attribute used as mapperId prefix (format: "namespace.id")
- `testLineNumberExtraction_shouldBeAccurate()` - Verify line numbers match XML file positions

**Sample Test XML:**
Create test file `src/test/resources/mappers/simple-static.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.UserMapper">
    <select id="getUserById" resultType="User">
        SELECT * FROM user WHERE id = #{id}
    </select>
    <update id="updateUser">
        UPDATE user SET name = #{name} WHERE id = #{id}
    </update>
</mapper>
```

**Then Implement:**
1. Add DOM4J 2.1.4 dependency to `sql-scanner-core/pom.xml`
2. Create `XmlMapperParser` class in `com.footstone.sqlguard.scanner.parser.impl` package implementing SqlParser
3. Implement `parse(File file)` method:
   - Read file with DOM4J SAXReader: `Document doc = new SAXReader().read(file)`
   - Get root element: `Element root = doc.getRootElement()`
   - Extract namespace: `String namespace = root.attributeValue("namespace")`
   - Find all SQL statement elements: `root.selectNodes("//select|//update|//delete|//insert")`
   - For each element:
     - Extract id attribute
     - Determine SqlCommandType from tag name (select→SELECT, update→UPDATE, etc.)
     - Extract SQL text: `element.getTextTrim()`
     - Get line number: `((LocationAwareElement) element).getLineNumber()` (DOM4J feature)
     - Create SqlEntry(source=SourceType.XML, filePath=file.getAbsolutePath(), mapperId=namespace+"."+id, sqlType=commandType, rawSql=sql, lineNumber=lineNum, dynamic=false, sqlVariants=new ArrayList<>())
   - Return List<SqlEntry>

**Constraints:**
- MapperId format must be "namespace.id" matching MyBatis runtime convention
- Line numbers must be accurate (DOM4J provides this via LocationAwareElement)
- SQL text should be trimmed (remove leading/trailing whitespace)
- Handle missing namespace gracefully (use "unknown" as prefix)

### Step 2: Dynamic Tag Detection TDD

**Test First:**
Write test class `DynamicTagDetectionTest` covering:
- `testIfTag_shouldSetDynamicFlag()` - XML with `<if>` tag should mark SqlEntry.dynamic=true
- `testWhereTag_shouldSetDynamicFlag()` - XML with `<where>` tag should mark dynamic
- `testForeachTag_shouldSetDynamicFlag()` - XML with `<foreach>` should mark dynamic
- `testChooseWhenTag_shouldSetDynamicFlag()` - XML with `<choose><when>` should mark dynamic
- `testStaticSQL_shouldNotBeDynamic()` - XML without dynamic tags should have dynamic=false
- `testNestedDynamicTags_shouldDetect()` - Nested if inside where should detect

**Sample Test XML:**
Create `src/test/resources/mappers/if-condition.xml`:
```xml
<mapper namespace="com.example.UserMapper">
    <select id="findUsers" resultType="User">
        SELECT * FROM user
        <where>
            <if test="name != null">
                AND name = #{name}
            </if>
        </where>
    </select>
</mapper>
```

**Then Implement:**
Add `hasDynamicTags(Element element)` helper method to XmlMapperParser:
1. Define Set of dynamic tag names:
   ```java
   private static final Set<String> DYNAMIC_TAGS = Set.of(
       "if", "where", "foreach", "choose", "when", "otherwise", "set", "trim", "bind"
   );
   ```

2. Recursively check element and descendants:
   ```java
   private boolean hasDynamicTags(Element element) {
       // Check current element
       if (DYNAMIC_TAGS.contains(element.getName())) {
           return true;
       }
       // Check all descendants
       for (Element child : element.elements()) {
           if (hasDynamicTags(child)) {
               return true;
           }
       }
       return false;
   }
   ```

3. Call `hasDynamicTags()` during parse() and set SqlEntry.dynamic flag accordingly

**Constraints:**
- Recursive detection handles nested dynamic tags
- Check tag name only (ignore attributes for this step)
- Dynamic flag should be set before creating SqlEntry

### Step 3: Variant Generation TDD

**Test First:**
Write test class `VariantGenerationTest` covering:
- `testIfTagVariants_shouldGenerateTwoScenarios()` - If tag generates variant with condition true and condition false
- `testForeachVariants_shouldGenerateRepresentative()` - Foreach generates empty/single/multiple item variants
- `testChooseVariants_shouldGeneratePerBranch()` - Choose generates one variant per when branch plus otherwise
- `testStaticSql_shouldHaveEmptyVariantsList()` - Non-dynamic SQL has empty sqlVariants list

**Sample Test XML:**
Create `src/test/resources/mappers/foreach-loop.xml`:
```xml
<mapper namespace="com.example.UserMapper">
    <select id="findByIds" resultType="User">
        SELECT * FROM user WHERE id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </select>
</mapper>
```

**Then Implement:**
Add `generateVariants(Element element)` method returning `List<String>`:

1. **If-tag handling:**
   - Find all `<if>` elements
   - Generate 2 variants: (1) include if content, (2) exclude if content
   - Example: `WHERE id=1 <if test="name!=null">AND name=?</if>`
     - Variant 1: "WHERE id=1 AND name=?"
     - Variant 2: "WHERE id=1"

2. **Foreach-tag handling:**
   - Find `<foreach>` elements
   - Generate 3 representative variants:
     - Empty collection: Remove entire foreach clause
     - Single item: "WHERE id IN (?)"
     - Multiple items: "WHERE id IN (?, ?, ?)"

3. **Choose-when handling:**
   - Find `<choose>` elements
   - Generate one variant per `<when>` branch
   - Generate one variant for `<otherwise>` if present

4. **Simplification:**
   - For complex nested scenarios, generate representative samples (not exhaustive combinations)
   - Limit total variants to max 10 per SQL
   - Add variant descriptions as SQL comments: `-- Variant: with condition X`

**Constraints:**
- Generated variants must be syntactically valid SQL
- Keep variant count reasonable (avoid combinatorial explosion)
- Variants stored in SqlEntry.sqlVariants list
- Static SQL has empty sqlVariants list

### Step 4: Comprehensive Testing

**Create Integration Test:**
Write `XmlMapperParserIntegrationTest` with multiple test XML files in `src/test/resources/mappers/`:

1. **simple-static.xml** - Plain SQL without dynamic tags
   - Verify 2 SqlEntry created (select + update)
   - Verify dynamic=false
   - Verify sqlVariants.isEmpty()

2. **if-condition.xml** - SQL with `<if test="...">`
   - Verify dynamic=true
   - Verify 2 sqlVariants generated

3. **foreach-loop.xml** - SQL with `<foreach collection="list">`
   - Verify dynamic=true
   - Verify 3 representative variants

4. **where-tag.xml** - SQL with `<where>` wrapper
   - Verify dynamic=true
   - Verify WHERE keyword properly handled

5. **nested-dynamic.xml** - Nested if inside where inside foreach
   - Verify complex dynamic detection
   - Verify variant count reasonable (<10)

6. **complex-real-world.xml** - Realistic MyBatis mapper with multiple statements and dynamic tags
   - Verify all statements parsed
   - Verify line numbers accurate
   - Verify mapperIds correct

**Edge Cases:**
- CDATA sections: `<![CDATA[SELECT * FROM user WHERE id < 10]]>`
- XML comments: `<!-- This is a comment -->`
- Multi-line SQL: Preserve line breaks in rawSql
- Special characters: Handle quotes, `<`, `>`, `&` in SQL

**Run Tests:**
Execute `mvn test -Dtest=*XmlMapper*` ensuring all XML parsing tests pass (expect 30+ tests).

## Expected Output

**Deliverables:**
1. `XmlMapperParser` class implementing SqlParser interface
2. DOM4J-based XML parsing with line number extraction
3. Dynamic tag detection (if/where/foreach/choose/when/otherwise/set/trim/bind)
4. SQL variant generation for dynamic SQL (if/foreach/choose variants)
5. 30+ comprehensive tests covering static SQL, dynamic SQL, edge cases
6. Sample test XML files (6+ mapper files in src/test/resources/mappers/)

**Success Criteria:**
- All static SQL correctly extracted with accurate line numbers
- Dynamic SQL detected and flagged (dynamic=true)
- Variants generated for common dynamic patterns
- MapperId format matches MyBatis convention (namespace.id)
- CDATA sections, comments, multi-line SQL handled correctly
- All tests passing (no failures, no errors)

**File Locations:**
- Implementation: `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/parser/impl/XmlMapperParser.java`
- Unit tests: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/parser/impl/XmlMapperParserTest.java`
- Integration tests: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/parser/impl/XmlMapperParserIntegrationTest.java`
- Variant tests: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/parser/impl/VariantGenerationTest.java`
- Dynamic detection tests: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/parser/impl/DynamicTagDetectionTest.java`
- Test resources: `sql-scanner-core/src/test/resources/mappers/*.xml` (6+ files)

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_03_Static_Scanner/Task_3_2_XML_Mapper_Parser_Implementation.md`

Follow `.apm/guides/Memory_Log_Guide.md` instructions including:
- Task completion status
- All deliverables created with file paths
- Test results (total count, pass/fail)
- Variant generation strategy and sample variants
- Edge cases handled
- Any DOM4J API learnings

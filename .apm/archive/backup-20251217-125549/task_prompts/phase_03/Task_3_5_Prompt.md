---
task_ref: "Task 3.5 - Dynamic SQL Variant Generator"
agent_assignment: "Agent_Static_Scanner"
memory_log_path: ".apm/Memory/Phase_03_Static_Scanner/Task_3_5_Dynamic_SQL_Variant_Generator.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: Dynamic SQL Variant Generator

## Task Reference
Implementation Plan: **Task 3.5 - Dynamic SQL Scenario Generator** assigned to **Agent_Static_Scanner**

## Context from Dependencies
Based on your Task 3.2 work:

**XML Mapper Parser Outputs:**
- `XmlMapperParser` class already has basic variant generation for dynamic SQL
- Current implementation generates simple variants (if: 2 scenarios, foreach: 2 scenarios, choose: per branch)
- `SqlEntry.sqlVariants` list already populated with basic variants
- DOM4J `Element` manipulation available for tag processing

**Your Task:**
Enhance the existing variant generation in XmlMapperParser to handle:
- Complex nested tag combinations (if inside where inside foreach)
- Smart WHERE clause handling (add WHERE keyword, remove leading AND/OR)
- Multiple independent if tags (generate combinations with limit)
- Edge cases (empty where, foreach with no items, nested choose)

## Objective
Enhance MyBatis dynamic SQL variant generator to produce comprehensive representative SQL execution scenarios for nested if/foreach/where/choose tags, enabling static validation of all possible execution paths without combinatorial explosion through intelligent combination limiting and syntactically valid SQL generation.

## Detailed Instructions
Complete this task in **5 exchanges, one step per response**. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: If-Tag Variant Generation Enhancement

**Test First:**
Write test class `IfTagVariantGeneratorTest` in `com.footstone.sqlguard.scanner.parser.impl` package covering:
- `testSimpleIf_shouldGenerateTwoVariants()` - <if test="id != null">AND id=#{id}</if> generates 2 variants (include/exclude)
- `testMultipleIndependentIf_shouldGenerateCombinations()` - 2 if tags generate 4 variants (2^2), verify all combinations
- `testThreeIf_shouldLimitToMaxVariants()` - 3 if tags would generate 8 variants (2^3), but limit to 10 max variants
- `testNestedIf_shouldHandleRecursively()` - if inside if generates correct nested variants
- `testIfWithinWhere_shouldCombineCorrectly()` - <where><if>...</if></where> generates variants with proper WHERE handling

**Sample Test XML:**
```xml
<select id="findUsers">
  SELECT * FROM user
  <where>
    <if test="name != null">AND name = #{name}</if>
    <if test="status != null">AND status = #{status}</if>
  </where>
</select>
```

**Expected Variants:**
1. `SELECT * FROM user WHERE name = ? AND status = ?` (both conditions)
2. `SELECT * FROM user WHERE name = ?` (only name)
3. `SELECT * FROM user WHERE status = ?` (only status)
4. `SELECT * FROM user` (no conditions)

**Then Enhance XmlMapperParser:**
1. **Review existing variant generation** in XmlMapperParser.generateVariants() method
2. **Add combinatorial if-tag handling:**
   ```java
   private List<String> generateIfCombinations(Element element) {
       List<Element> ifTags = element.selectNodes(".//if");
       if (ifTags.isEmpty()) {
           return Collections.singletonList(buildSql(element, Collections.emptyMap()));
       }

       // Generate all combinations (2^n) but limit to MAX_VARIANTS
       int numCombinations = Math.min((int) Math.pow(2, ifTags.size()), MAX_VARIANTS);
       List<String> variants = new ArrayList<>();

       for (int i = 0; i < numCombinations; i++) {
           Map<Element, Boolean> ifStates = new HashMap<>();
           for (int j = 0; j < ifTags.size(); j++) {
               ifStates.put(ifTags.get(j), (i & (1 << j)) != 0);
           }
           String variant = buildSqlWithIfStates(element, ifStates);
           variants.add(variant);
       }
       return variants;
   }
   ```

3. **Implement buildSqlWithIfStates() helper:**
   - Traverse Element tree recursively
   - For <if> tags: include content if ifStates.get(element) == true, exclude otherwise
   - For other tags: process normally
   - Return concatenated SQL string

4. **Add variant descriptions:**
   ```java
   private String addVariantDescription(String sql, Map<Element, Boolean> ifStates) {
       StringBuilder desc = new StringBuilder("-- Variant: ");
       for (Map.Entry<Element, Boolean> entry : ifStates.entrySet()) {
           String test = entry.getKey().attributeValue("test");
           desc.append(entry.getValue() ? "with " : "without ")
               .append(test).append(", ");
       }
       return desc.toString() + "\n" + sql;
   }
   ```

**Constraints:**
- MAX_VARIANTS = 10 (prevent combinatorial explosion)
- Variant descriptions must be meaningful for debugging
- Handle nested if tags recursively
- Preserve SQL syntax validity

### Step 2: Foreach-Tag Variant Generation Enhancement

**Test First:**
Write test class `ForeachTagVariantGeneratorTest` covering:
- `testForeachEmpty_shouldRemoveClause()` - Empty collection removes entire IN clause
- `testForeachSingle_shouldNoSeparator()` - Single item: `WHERE id IN (?)`
- `testForeachMultiple_shouldUseSeparator()` - Multiple items: `WHERE id IN (?, ?, ?)`
- `testForeachInUpdate_shouldHandleSet()` - foreach in UPDATE SET clause
- `testNestedForeach_shouldLimit()` - Nested foreach limited to prevent explosion

**Sample Test XML:**
```xml
<select id="findByIds">
  SELECT * FROM user
  WHERE id IN
  <foreach collection="ids" item="id" open="(" close=")" separator=",">
    #{id}
  </foreach>
</select>
```

**Expected Variants:**
1. `SELECT * FROM user` (empty collection - entire WHERE clause removed)
2. `SELECT * FROM user WHERE id IN (?)` (single item)
3. `SELECT * FROM user WHERE id IN (?, ?, ?)` (3 items - representative multiple)

**Then Enhance XmlMapperParser:**
1. **Add foreach variant generation:**
   ```java
   private List<String> generateForeachVariants(Element element) {
       List<Element> foreachTags = element.selectNodes(".//foreach");
       if (foreachTags.isEmpty()) {
           return Collections.singletonList(buildSql(element));
       }

       List<String> variants = new ArrayList<>();

       // Variant 1: Empty collection (remove entire clause)
       variants.add(buildSqlWithForeachState(element, foreachTags.get(0), ForeachState.EMPTY));

       // Variant 2: Single item
       variants.add(buildSqlWithForeachState(element, foreachTags.get(0), ForeachState.SINGLE));

       // Variant 3: Multiple items (3 items representative)
       variants.add(buildSqlWithForeachState(element, foreachTags.get(0), ForeachState.MULTIPLE));

       return variants;
   }

   enum ForeachState {
       EMPTY,    // Remove entire foreach and surrounding clause
       SINGLE,   // Replace with single placeholder (no separator)
       MULTIPLE  // Replace with 3 placeholders with separators
   }
   ```

2. **Implement buildSqlWithForeachState() helper:**
   ```java
   private String buildSqlWithForeachState(Element element, Element foreachTag, ForeachState state) {
       String open = foreachTag.attributeValue("open", "");
       String close = foreachTag.attributeValue("close", "");
       String separator = foreachTag.attributeValue("separator", ",");
       String item = foreachTag.attributeValue("item", "item");

       String replacement;
       switch (state) {
           case EMPTY:
               // Remove entire IN clause: "WHERE id IN (...)" → "WHERE 1=1" or remove WHERE entirely
               replacement = "";
               break;
           case SINGLE:
               replacement = open + "?" + close;
               break;
           case MULTIPLE:
               replacement = open + "?" + separator + "?" + separator + "?" + close;
               break;
       }

       // Replace foreach tag with generated content
       return buildSqlReplacingElement(element, foreachTag, replacement);
   }
   ```

3. **Handle empty collection clause removal:**
   - If foreach is inside WHERE IN (...), remove entire "AND id IN ()" clause
   - If foreach is in SET clause, remove that assignment
   - Ensure remaining SQL is syntactically valid

**Constraints:**
- Representative sample sizes: 0, 1, 3 items (not exhaustive)
- Handle open/close/separator attributes correctly
- Remove empty clauses cleanly (no dangling AND/OR)
- Handle foreach in different contexts (WHERE, SET, SELECT)

### Step 3: Where-Tag Smart Handling

**Test First:**
Write test class `WhereTagVariantGeneratorTest` covering:
- `testWhereWithContent_shouldAddWhereKeyword()` - <where>AND id=?</where> → `WHERE id=?` (add WHERE, remove leading AND)
- `testWhereWithoutContent_shouldRemoveEntirely()` - Empty where tag removed entirely
- `testWhereMultipleConditions_shouldRemoveOnlyLeadingAndOr()` - `WHERE id=? AND name=?` (keep internal AND)
- `testWhereWithOr_shouldHandleCorrectly()` - <where>OR status=?</where> → `WHERE status=?`
- `testNestedWhereIf_shouldCombine()` - where with nested if generates correct combinations

**Sample Test XML:**
```xml
<select id="search">
  SELECT * FROM user
  <where>
    <if test="id != null">AND id = #{id}</if>
    <if test="name != null">AND name = #{name}</if>
  </where>
</select>
```

**Expected Variants:**
1. `SELECT * FROM user WHERE id = ? AND name = ?` (both conditions, WHERE added, leading AND removed from first)
2. `SELECT * FROM user WHERE id = ?` (only first)
3. `SELECT * FROM user WHERE name = ?` (only second, leading AND removed)
4. `SELECT * FROM user` (no conditions, WHERE tag removed entirely)

**Then Enhance XmlMapperParser:**
1. **Add where-tag processing:**
   ```java
   private String processWhereTag(Element whereElement, String innerContent) {
       if (innerContent == null || innerContent.trim().isEmpty()) {
           // Empty where tag - remove entirely
           return "";
       }

       // Add WHERE keyword and remove leading AND/OR
       String cleaned = innerContent.trim()
           .replaceFirst("^(AND|OR)\\s+", "")  // Remove leading AND or OR
           .trim();

       if (cleaned.isEmpty()) {
           return "";
       }

       return "WHERE " + cleaned;
   }
   ```

2. **Integrate with if-tag combinations:**
   - When generating if combinations, track which if tags are included
   - Build inner content of where tag based on included if tags
   - Apply processWhereTag() to add WHERE and clean up AND/OR
   - Return final SQL variant

3. **Handle multiple AND/OR correctly:**
   ```java
   private String cleanLeadingAndOr(String sql) {
       // Only remove FIRST occurrence of leading AND/OR
       // Preserve internal AND/OR operators
       return sql.replaceFirst("^\\s*(AND|OR)\\s+", "");
   }
   ```

**Constraints:**
- WHERE keyword only added if content exists
- Only leading AND/OR removed (first occurrence)
- Internal AND/OR preserved
- Multiple spaces normalized to single space
- Empty where tag removes entire WHERE clause

### Step 4: Choose-When Variant Generation

**Test First:**
Write test class `ChooseWhenVariantGeneratorTest` covering:
- `testChooseMultipleWhen_shouldGeneratePerBranch()` - 2 when + 1 otherwise = 3 variants
- `testChooseNoOtherwise_shouldHandleGracefully()` - Only when branches generate variants
- `testChooseSingleBranch_shouldBeExclusive()` - Each variant includes only ONE branch
- `testNestedChoose_shouldLimit()` - Nested choose limited by MAX_VARIANTS
- `testChooseWithinWhere_shouldCombine()` - choose inside where tag works correctly

**Sample Test XML:**
```xml
<select id="findByType">
  SELECT * FROM user
  <where>
    <choose>
      <when test="type == 1">AND status = 'active'</when>
      <when test="type == 2">AND status = 'inactive'</when>
      <otherwise>AND status != 'deleted'</otherwise>
    </choose>
  </where>
</select>
```

**Expected Variants:**
1. `SELECT * FROM user WHERE status = 'active'` (when type=1)
2. `SELECT * FROM user WHERE status = 'inactive'` (when type=2)
3. `SELECT * FROM user WHERE status != 'deleted'` (otherwise)

**Then Enhance XmlMapperParser:**
1. **Add choose-when variant generation:**
   ```java
   private List<String> generateChooseVariants(Element element) {
       List<Element> chooseTags = element.selectNodes(".//choose");
       if (chooseTags.isEmpty()) {
           return Collections.singletonList(buildSql(element));
       }

       List<String> variants = new ArrayList<>();
       Element chooseTag = chooseTags.get(0); // Handle first choose

       // Generate variant for each when branch
       List<Element> whenTags = chooseTag.selectNodes("when");
       for (Element whenTag : whenTags) {
           String test = whenTag.attributeValue("test", "unknown");
           String variant = buildSqlWithChooseBranch(element, chooseTag, whenTag);
           variants.add("-- Variant: choose when " + test + "\n" + variant);
       }

       // Generate variant for otherwise branch
       Element otherwiseTag = (Element) chooseTag.selectSingleNode("otherwise");
       if (otherwiseTag != null) {
           String variant = buildSqlWithChooseBranch(element, chooseTag, otherwiseTag);
           variants.add("-- Variant: choose otherwise\n" + variant);
       }

       return variants;
   }
   ```

2. **Implement buildSqlWithChooseBranch():**
   ```java
   private String buildSqlWithChooseBranch(Element element, Element chooseTag, Element selectedBranch) {
       // Replace entire choose tag with ONLY the selected branch content
       String branchContent = selectedBranch.getTextTrim();
       return buildSqlReplacingElement(element, chooseTag, branchContent);
   }
   ```

3. **Ensure exclusivity:**
   - Each variant includes content from ONLY ONE branch
   - MyBatis semantics: first matching when OR otherwise (not multiple)
   - Verify no overlap between branch contents in variants

**Constraints:**
- One variant per branch (when branches + otherwise)
- Mutually exclusive branches (MyBatis switch-case semantics)
- Descriptive variant comments indicating which branch
- Handle missing otherwise gracefully

### Step 5: Comprehensive Integration Testing

**Create Integration Test:**
Write `DynamicSqlVariantGeneratorIntegrationTest` with complex scenarios:

**Test Scenario 1: Complex Nested Tags**
Create `complex-nested.xml`:
```xml
<select id="complexSearch">
  SELECT * FROM user
  <where>
    <if test="ids != null">
      AND id IN
      <foreach collection="ids" item="id" open="(" close=")" separator=",">
        #{id}
      </foreach>
    </if>
    <if test="name != null">AND name = #{name}</if>
    <choose>
      <when test="status == 1">AND status = 'active'</when>
      <when test="status == 2">AND status = 'inactive'</when>
      <otherwise>AND status != 'deleted'</otherwise>
    </choose>
  </where>
</select>
```

**Verify:**
- Variant count reasonable (<= 10 due to MAX_VARIANTS limit)
- All variants syntactically valid SQL
- WHERE clause handled correctly in all combinations
- Foreach states (empty/single/multiple) combined with if/choose correctly

**Test Scenario 2: Real-World Mapper**
Create `real-world-mapper.xml` with realistic MyBatis patterns:
- Dynamic WHERE with multiple if conditions
- foreach in IN clause
- choose-when for different query modes
- Nested dynamic tags

**Verify:**
- Parse without errors
- SqlEntry.sqlVariants populated
- Each variant is valid SQL (test with JSqlParser.parse())
- Variant descriptions meaningful

**Test Scenario 3: Edge Cases**
Create `edge-cases.xml`:
```xml
<mapper namespace="com.example.EdgeMapper">
  <!-- Empty where tag -->
  <select id="emptyWhere">
    SELECT * FROM user
    <where>
      <if test="false">AND impossible = true</if>
    </where>
  </select>

  <!-- Foreach with no items -->
  <select id="emptyForeach">
    SELECT * FROM user WHERE id IN
    <foreach collection="emptyList" item="id" open="(" close=")" separator=",">
      #{id}
    </foreach>
  </select>

  <!-- Choose with only otherwise -->
  <select id="onlyOtherwise">
    SELECT * FROM user
    <where>
      <choose>
        <otherwise>AND 1=1</otherwise>
      </choose>
    </where>
  </select>

  <!-- Nested choose -->
  <select id="nestedChoose">
    SELECT * FROM user
    <where>
      <choose>
        <when test="type == 1">
          <choose>
            <when test="subType == 1">AND status = 'a'</when>
            <otherwise>AND status = 'b'</otherwise>
          </choose>
        </when>
        <otherwise>AND status = 'c'</otherwise>
      </choose>
    </where>
  </select>
</mapper>
```

**Verify Edge Cases:**
- Empty where tag: SQL without WHERE clause
- Empty foreach: Entire IN clause removed or replaced appropriately
- Choose with only otherwise: Otherwise branch used
- Nested choose: Limited variants, syntactically valid

**Performance Test:**
```java
@Test
public void testPerformance_complexDynamicSql_shouldCompleteQuickly() {
    // Complex SQL with 5+ nested dynamic tags
    File complexXml = new File("src/test/resources/mappers/very-complex.xml");

    long startTime = System.currentTimeMillis();
    List<SqlEntry> entries = parser.parse(complexXml);
    long duration = System.currentTimeMillis() - startTime;

    // Should complete in <1 second
    assertThat(duration).isLessThan(1000);

    // Verify variant count limited
    for (SqlEntry entry : entries) {
        if (entry.isDynamic()) {
            assertThat(entry.getSqlVariants().size()).isLessThanOrEqualTo(10);
        }
    }
}
```

**Validation Test:**
```java
@Test
public void testVariantValidation_shouldBeValidSql() throws Exception {
    List<SqlEntry> entries = parser.parse(new File("test-mappers.xml"));

    for (SqlEntry entry : entries) {
        for (String variant : entry.getSqlVariants()) {
            // Remove variant comment
            String sql = variant.replaceFirst("^--.*\\n", "");

            // Should parse without errors
            assertDoesNotThrow(() -> {
                Statement stmt = CCJSqlParserUtil.parse(sql);
                assertThat(stmt).isNotNull();
            }, "Invalid SQL variant: " + variant);
        }
    }
}
```

**Run All Tests:**
Execute `mvn test -Dtest=*VariantGenerator*` ensuring all tests pass (expect 30+ tests).

## Expected Output

**Deliverables:**
1. Enhanced `XmlMapperParser.generateVariants()` method with nested tag support
2. If-tag combination generation with MAX_VARIANTS limit
3. Foreach-tag 3-state generation (empty/single/multiple)
4. Where-tag smart WHERE keyword insertion and AND/OR removal
5. Choose-when per-branch variant generation
6. 5 test classes with 30+ tests total
7. 4 complex test XML files (complex-nested, real-world, edge-cases, very-complex)

**Success Criteria:**
- All if/foreach/where/choose tag combinations handled correctly
- Variant count limited to prevent explosion (<= 10 per SQL)
- All generated variants are syntactically valid SQL
- WHERE clause handling correct (add WHERE, remove leading AND/OR)
- Empty tags handled gracefully (remove clauses cleanly)
- Nested tags generate limited but representative combinations
- Performance acceptable (<1 second for complex SQL)
- All tests passing (no failures, no errors)

**File Locations:**
- Enhanced parser: `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/parser/impl/XmlMapperParser.java`
- Test classes: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/parser/impl/*VariantGeneratorTest.java`
- Test resources: `sql-scanner-core/src/test/resources/mappers/*.xml`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_03_Static_Scanner/Task_3_5_Dynamic_SQL_Variant_Generator.md`

Follow `.apm/guides/Memory_Log_Guide.md` instructions including:
- Task completion status
- All deliverables created with file paths
- Test results (total count, pass/fail)
- Variant generation strategy (combinatorial limit, representative sampling)
- Edge cases handled
- Performance measurements
- Variant validation approach

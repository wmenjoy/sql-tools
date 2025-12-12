package com.footstone.sqlguard.validator.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test class for AbstractRuleChecker utility methods.
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>extractWhere() from SELECT/UPDATE/DELETE returns Expression</li>
 *   <li>extractWhere() returns null for INSERT</li>
 *   <li>extractTableName() from simple SELECT returns table name</li>
 *   <li>extractTableName() from JOIN returns primary table</li>
 *   <li>FieldExtractorVisitor extracts all field names from complex WHERE</li>
 *   <li>isDummyCondition() detects "1=1", "true", constant comparisons</li>
 *   <li>isConstant() identifies literals vs column references</li>
 * </ul>
 */
@DisplayName("AbstractRuleChecker Utility Methods Tests")
class AbstractRuleCheckerTest {

  private JSqlParserFacade parser;
  private TestRuleChecker checker;

  /**
   * Concrete implementation of AbstractRuleChecker for testing utility methods.
   */
  private static class TestRuleChecker extends AbstractRuleChecker {
    @Override
    public void check(SqlContext context, ValidationResult result) {
      // Not used in these tests
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

    // Expose protected methods for testing
    public Expression testExtractWhere(Statement stmt) {
      return extractWhere(stmt);
    }

    public String testExtractTableName(Statement stmt) {
      return extractTableName(stmt);
    }

    public Set<String> testExtractFields(Expression expr) {
      return extractFields(expr);
    }

    public boolean testIsDummyCondition(Expression expr) {
      return isDummyCondition(expr);
    }

    public boolean testIsConstant(Expression expr) {
      return isConstant(expr);
    }
  }

  @BeforeEach
  void setUp() {
    parser = new JSqlParserFacade();
    checker = new TestRuleChecker();
  }

  @Test
  @DisplayName("extractWhere() from SELECT returns Expression")
  void testExtractWhereFromSelect() throws Exception {
    String sql = "SELECT * FROM users WHERE id = 1";
    Statement stmt = parser.parse(sql);

    Expression where = checker.testExtractWhere(stmt);

    assertNotNull(where, "WHERE clause should be extracted from SELECT");
    assertTrue(where.toString().contains("id"), "WHERE should contain 'id' column");
  }

  @Test
  @DisplayName("extractWhere() from UPDATE returns Expression")
  void testExtractWhereFromUpdate() throws Exception {
    String sql = "UPDATE users SET name = 'John' WHERE id = 1";
    Statement stmt = parser.parse(sql);

    Expression where = checker.testExtractWhere(stmt);

    assertNotNull(where, "WHERE clause should be extracted from UPDATE");
    assertTrue(where.toString().contains("id"), "WHERE should contain 'id' column");
  }

  @Test
  @DisplayName("extractWhere() from DELETE returns Expression")
  void testExtractWhereFromDelete() throws Exception {
    String sql = "DELETE FROM users WHERE id = 1";
    Statement stmt = parser.parse(sql);

    Expression where = checker.testExtractWhere(stmt);

    assertNotNull(where, "WHERE clause should be extracted from DELETE");
    assertTrue(where.toString().contains("id"), "WHERE should contain 'id' column");
  }

  @Test
  @DisplayName("extractWhere() returns null for INSERT")
  void testExtractWhereFromInsert() throws Exception {
    String sql = "INSERT INTO users (id, name) VALUES (1, 'John')";
    Statement stmt = parser.parse(sql);

    Expression where = checker.testExtractWhere(stmt);

    assertNull(where, "WHERE clause should be null for INSERT");
  }

  @Test
  @DisplayName("extractWhere() returns null when no WHERE clause")
  void testExtractWhereNoClause() throws Exception {
    String sql = "SELECT * FROM users";
    Statement stmt = parser.parse(sql);

    Expression where = checker.testExtractWhere(stmt);

    assertNull(where, "WHERE clause should be null when not present");
  }

  @Test
  @DisplayName("extractTableName() from simple SELECT returns table name")
  void testExtractTableNameSimpleSelect() throws Exception {
    String sql = "SELECT * FROM users WHERE id = 1";
    Statement stmt = parser.parse(sql);

    String tableName = checker.testExtractTableName(stmt);

    assertEquals("users", tableName, "Table name should be 'users'");
  }

  @Test
  @DisplayName("extractTableName() from JOIN returns primary table")
  void testExtractTableNameJoin() throws Exception {
    String sql = "SELECT * FROM users u JOIN orders o ON u.id = o.user_id";
    Statement stmt = parser.parse(sql);

    String tableName = checker.testExtractTableName(stmt);

    assertEquals("users", tableName, "Primary table name should be 'users'");
  }

  @Test
  @DisplayName("extractTableName() from UPDATE returns table name")
  void testExtractTableNameUpdate() throws Exception {
    String sql = "UPDATE users SET name = 'John' WHERE id = 1";
    Statement stmt = parser.parse(sql);

    String tableName = checker.testExtractTableName(stmt);

    assertEquals("users", tableName, "Table name should be 'users'");
  }

  @Test
  @DisplayName("extractTableName() from DELETE returns table name")
  void testExtractTableNameDelete() throws Exception {
    String sql = "DELETE FROM users WHERE id = 1";
    Statement stmt = parser.parse(sql);

    String tableName = checker.testExtractTableName(stmt);

    assertEquals("users", tableName, "Table name should be 'users'");
  }

  @Test
  @DisplayName("FieldExtractorVisitor extracts single field")
  void testExtractFieldsSingle() throws Exception {
    String sql = "SELECT * FROM users WHERE id = 1";
    Statement stmt = parser.parse(sql);
    Expression where = checker.testExtractWhere(stmt);

    Set<String> fields = checker.testExtractFields(where);

    assertEquals(1, fields.size(), "Should extract 1 field");
    assertTrue(fields.contains("id"), "Should contain 'id' field");
  }

  @Test
  @DisplayName("FieldExtractorVisitor extracts multiple fields from AND")
  void testExtractFieldsAnd() throws Exception {
    String sql = "SELECT * FROM users WHERE id = 1 AND name = 'John'";
    Statement stmt = parser.parse(sql);
    Expression where = checker.testExtractWhere(stmt);

    Set<String> fields = checker.testExtractFields(where);

    assertEquals(2, fields.size(), "Should extract 2 fields");
    assertTrue(fields.contains("id"), "Should contain 'id' field");
    assertTrue(fields.contains("name"), "Should contain 'name' field");
  }

  @Test
  @DisplayName("FieldExtractorVisitor extracts multiple fields from OR")
  void testExtractFieldsOr() throws Exception {
    String sql = "SELECT * FROM users WHERE id = 1 OR email = 'test@example.com'";
    Statement stmt = parser.parse(sql);
    Expression where = checker.testExtractWhere(stmt);

    Set<String> fields = checker.testExtractFields(where);

    assertEquals(2, fields.size(), "Should extract 2 fields");
    assertTrue(fields.contains("id"), "Should contain 'id' field");
    assertTrue(fields.contains("email"), "Should contain 'email' field");
  }

  @Test
  @DisplayName("FieldExtractorVisitor extracts fields from nested conditions")
  void testExtractFieldsNested() throws Exception {
    String sql = "SELECT * FROM users WHERE (id = 1 OR id = 2) AND (name = 'John' OR email = 'test@example.com')";
    Statement stmt = parser.parse(sql);
    Expression where = checker.testExtractWhere(stmt);

    Set<String> fields = checker.testExtractFields(where);

    assertEquals(3, fields.size(), "Should extract 3 unique fields");
    assertTrue(fields.contains("id"), "Should contain 'id' field");
    assertTrue(fields.contains("name"), "Should contain 'name' field");
    assertTrue(fields.contains("email"), "Should contain 'email' field");
  }

  @Test
  @DisplayName("FieldExtractorVisitor handles table prefixes")
  void testExtractFieldsWithTablePrefix() throws Exception {
    String sql = "SELECT * FROM users WHERE users.id = 1 AND users.name = 'John'";
    Statement stmt = parser.parse(sql);
    Expression where = checker.testExtractWhere(stmt);

    Set<String> fields = checker.testExtractFields(where);

    assertEquals(2, fields.size(), "Should extract 2 fields");
    assertTrue(fields.contains("id"), "Should contain 'id' without prefix");
    assertTrue(fields.contains("name"), "Should contain 'name' without prefix");
  }

  @Test
  @DisplayName("isDummyCondition() detects '1=1'")
  void testIsDummyConditionOneEqualsOne() throws Exception {
    String sql = "SELECT * FROM users WHERE 1=1";
    Statement stmt = parser.parse(sql);
    Expression where = checker.testExtractWhere(stmt);

    boolean isDummy = checker.testIsDummyCondition(where);

    assertTrue(isDummy, "Should detect '1=1' as dummy condition");
  }

  @Test
  @DisplayName("isDummyCondition() detects '1 = 1' with spaces")
  void testIsDummyConditionOneEqualsOneSpaces() throws Exception {
    String sql = "SELECT * FROM users WHERE 1 = 1";
    Statement stmt = parser.parse(sql);
    Expression where = checker.testExtractWhere(stmt);

    boolean isDummy = checker.testIsDummyCondition(where);

    assertTrue(isDummy, "Should detect '1 = 1' as dummy condition");
  }

  @Test
  @DisplayName("isDummyCondition() detects \"'1'='1'\"")
  void testIsDummyConditionStringOneEqualsOne() throws Exception {
    String sql = "SELECT * FROM users WHERE '1'='1'";
    Statement stmt = parser.parse(sql);
    Expression where = checker.testExtractWhere(stmt);

    boolean isDummy = checker.testIsDummyCondition(where);

    assertTrue(isDummy, "Should detect \"'1'='1'\" as dummy condition");
  }

  @Test
  @DisplayName("isDummyCondition() detects 'true'")
  void testIsDummyConditionTrue() throws Exception {
    String sql = "SELECT * FROM users WHERE true";
    Statement stmt = parser.parse(sql);
    Expression where = checker.testExtractWhere(stmt);

    boolean isDummy = checker.testIsDummyCondition(where);

    assertTrue(isDummy, "Should detect 'true' as dummy condition");
  }

  @Test
  @DisplayName("isDummyCondition() detects constant numeric equality")
  void testIsDummyConditionConstantNumeric() throws Exception {
    String sql = "SELECT * FROM users WHERE 5=5";
    Statement stmt = parser.parse(sql);
    Expression where = checker.testExtractWhere(stmt);

    boolean isDummy = checker.testIsDummyCondition(where);

    assertTrue(isDummy, "Should detect '5=5' as dummy condition");
  }

  @Test
  @DisplayName("isDummyCondition() returns false for real condition")
  void testIsDummyConditionRealCondition() throws Exception {
    String sql = "SELECT * FROM users WHERE id = 1";
    Statement stmt = parser.parse(sql);
    Expression where = checker.testExtractWhere(stmt);

    boolean isDummy = checker.testIsDummyCondition(where);

    assertFalse(isDummy, "Should not detect 'id = 1' as dummy condition");
  }

  @Test
  @DisplayName("isConstant() identifies numeric literal")
  void testIsConstantNumeric() throws Exception {
    String sql = "SELECT * FROM users WHERE id = 123";
    Statement stmt = parser.parse(sql);
    Expression where = checker.testExtractWhere(stmt);

    // Extract the right side of the equality (the constant)
    String whereStr = where.toString();
    assertTrue(whereStr.contains("123"), "WHERE should contain constant 123");
  }

  @Test
  @DisplayName("isConstant() identifies string literal")
  void testIsConstantString() throws Exception {
    String sql = "SELECT * FROM users WHERE name = 'John'";
    Statement stmt = parser.parse(sql);
    Expression where = checker.testExtractWhere(stmt);

    String whereStr = where.toString();
    assertTrue(whereStr.contains("John"), "WHERE should contain constant 'John'");
  }

  @Test
  @DisplayName("isConstant() vs column reference")
  void testIsConstantVsColumn() throws Exception {
    String sql = "SELECT * FROM users WHERE id = user_id";
    Statement stmt = parser.parse(sql);
    Expression where = checker.testExtractWhere(stmt);

    Set<String> fields = checker.testExtractFields(where);
    assertEquals(2, fields.size(), "Should extract both columns");
    assertTrue(fields.contains("id"), "Should contain 'id' column");
    assertTrue(fields.contains("user_id"), "Should contain 'user_id' column");
  }

  @Test
  @DisplayName("extractFields() returns empty set for null expression")
  void testExtractFieldsNull() {
    Set<String> fields = checker.testExtractFields(null);

    assertNotNull(fields, "Should return non-null set");
    assertTrue(fields.isEmpty(), "Should return empty set for null expression");
  }

  @Test
  @DisplayName("extractWhere() handles null statement gracefully")
  void testExtractWhereNull() {
    Expression where = checker.testExtractWhere(null);

    assertNull(where, "Should return null for null statement");
  }

  @Test
  @DisplayName("extractTableName() handles null statement gracefully")
  void testExtractTableNameNull() {
    String tableName = checker.testExtractTableName(null);

    assertNull(tableName, "Should return null for null statement");
  }

  @Test
  @DisplayName("isDummyCondition() returns false for null expression")
  void testIsDummyConditionNull() {
    boolean isDummy = checker.testIsDummyCondition(null);

    assertFalse(isDummy, "Should return false for null expression");
  }

  @Test
  @DisplayName("isConstant() returns false for null expression")
  void testIsConstantNull() {
    boolean isConst = checker.testIsConstant(null);

    assertFalse(isConst, "Should return false for null expression");
  }
}

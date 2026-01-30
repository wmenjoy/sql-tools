package com.footstone.sqlguard.validator.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PaginationSyntaxHelper.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>AST-based pagination clause detection (LIMIT, TOP, FETCH)</li>
 *   <li>String-based pagination keyword detection (all dialects)</li>
 *   <li>PageSize extraction from various pagination syntaxes</li>
 *   <li>Offset extraction from OFFSET keyword and MySQL comma syntax</li>
 * </ul>
 *
 * @since 1.2.0
 */
@DisplayName("PaginationSyntaxHelper Tests")
class PaginationSyntaxHelperTest {

  // ==================== hasPaginationClause Tests ====================

  @Nested
  @DisplayName("hasPaginationClause() - AST-based Detection")
  class HasPaginationClauseTests {

    @Test
    @DisplayName("Should return false for null PlainSelect")
    void testNullPlainSelect() {
      assertFalse(PaginationSyntaxHelper.hasPaginationClause(null));
    }

    @Test
    @DisplayName("Should return false for SELECT without pagination")
    void testSelectWithoutPagination() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT * FROM users");
      assertFalse(PaginationSyntaxHelper.hasPaginationClause(plainSelect));
    }

    @Test
    @DisplayName("Should detect MySQL LIMIT clause")
    void testMySqlLimit() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT * FROM users LIMIT 100");
      assertTrue(PaginationSyntaxHelper.hasPaginationClause(plainSelect));
    }

    @Test
    @DisplayName("Should detect MySQL LIMIT with OFFSET")
    void testMySqlLimitOffset() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT * FROM users LIMIT 100 OFFSET 20");
      assertTrue(PaginationSyntaxHelper.hasPaginationClause(plainSelect));
    }

    @Test
    @DisplayName("Should detect SQL Server TOP clause")
    void testSqlServerTop() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT TOP 100 * FROM users");
      assertTrue(PaginationSyntaxHelper.hasPaginationClause(plainSelect));
    }

    @Test
    @DisplayName("Should detect DB2 FETCH FIRST clause")
    void testDb2FetchFirst() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT * FROM users FETCH FIRST 100 ROWS ONLY");
      assertTrue(PaginationSyntaxHelper.hasPaginationClause(plainSelect));
    }

    @Test
    @DisplayName("Should detect SQL Server OFFSET/FETCH")
    void testSqlServerOffsetFetch() throws Exception {
      PlainSelect plainSelect = parsePlainSelect(
          "SELECT * FROM users ORDER BY id OFFSET 20 ROWS FETCH NEXT 100 ROWS ONLY");
      assertTrue(PaginationSyntaxHelper.hasPaginationClause(plainSelect));
    }
  }

  // ==================== hasPaginationKeyword Tests ====================

  @Nested
  @DisplayName("hasPaginationKeyword() - String-based Detection")
  class HasPaginationKeywordTests {

    @Test
    @DisplayName("Should return false for null SQL")
    void testNullSql() {
      assertFalse(PaginationSyntaxHelper.hasPaginationKeyword(null));
    }

    @Test
    @DisplayName("Should return false for SELECT without pagination")
    void testSelectWithoutPagination() {
      assertFalse(PaginationSyntaxHelper.hasPaginationKeyword("SELECT * FROM users"));
    }

    @Test
    @DisplayName("Should detect LIMIT keyword")
    void testLimitKeyword() {
      assertTrue(PaginationSyntaxHelper.hasPaginationKeyword("SELECT * FROM users LIMIT 100"));
    }

    @Test
    @DisplayName("Should detect LIMIT in nested query")
    void testLimitInNestedQuery() {
      assertTrue(PaginationSyntaxHelper.hasPaginationKeyword(
          "SELECT * FROM (SELECT * FROM users LIMIT 100) t"));
    }

    @Test
    @DisplayName("Should detect TOP keyword")
    void testTopKeyword() {
      assertTrue(PaginationSyntaxHelper.hasPaginationKeyword("SELECT TOP 100 * FROM users"));
    }

    @Test
    @DisplayName("Should detect TOP with parentheses")
    void testTopWithParentheses() {
      assertTrue(PaginationSyntaxHelper.hasPaginationKeyword("SELECT TOP(100) * FROM users"));
    }

    @Test
    @DisplayName("Should not detect TOP as part of word (e.g., STOPPED)")
    void testTopNotPartOfWord() {
      assertFalse(PaginationSyntaxHelper.hasPaginationKeyword(
          "SELECT * FROM users WHERE status = 'STOPPED'"));
    }

    @Test
    @DisplayName("Should detect FETCH FIRST keyword")
    void testFetchFirstKeyword() {
      assertTrue(PaginationSyntaxHelper.hasPaginationKeyword(
          "SELECT * FROM users FETCH FIRST 100 ROWS ONLY"));
    }

    @Test
    @DisplayName("Should detect FETCH NEXT keyword")
    void testFetchNextKeyword() {
      assertTrue(PaginationSyntaxHelper.hasPaginationKeyword(
          "SELECT * FROM users ORDER BY id OFFSET 20 ROWS FETCH NEXT 100 ROWS ONLY"));
    }

    @Test
    @DisplayName("Should detect ROWNUM keyword")
    void testRownumKeyword() {
      assertTrue(PaginationSyntaxHelper.hasPaginationKeyword(
          "SELECT * FROM users WHERE ROWNUM <= 100"));
    }

    @Test
    @DisplayName("Should detect ROW_NUMBER keyword")
    void testRowNumberKeyword() {
      assertTrue(PaginationSyntaxHelper.hasPaginationKeyword(
          "SELECT * FROM (SELECT *, ROW_NUMBER() OVER (ORDER BY id) as rn FROM users) t WHERE rn <= 100"));
    }

    @Test
    @DisplayName("Should detect pagination in UNION query")
    void testPaginationInUnion() {
      assertTrue(PaginationSyntaxHelper.hasPaginationKeyword(
          "SELECT * FROM users LIMIT 10 UNION SELECT * FROM admins"));
    }
  }

  // ==================== extractPageSize Tests ====================

  @Nested
  @DisplayName("extractPageSize() - Page Size Extraction")
  class ExtractPageSizeTests {

    @Test
    @DisplayName("Should return null for null PlainSelect")
    void testNullPlainSelect() {
      assertNull(PaginationSyntaxHelper.extractPageSize(null));
    }

    @Test
    @DisplayName("Should return null for SELECT without pagination")
    void testSelectWithoutPagination() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT * FROM users");
      assertNull(PaginationSyntaxHelper.extractPageSize(plainSelect));
    }

    @Test
    @DisplayName("Should extract pageSize from LIMIT clause")
    void testExtractFromLimit() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT * FROM users LIMIT 100");
      assertEquals(100L, PaginationSyntaxHelper.extractPageSize(plainSelect));
    }

    @Test
    @DisplayName("Should extract pageSize from LIMIT with OFFSET")
    void testExtractFromLimitWithOffset() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT * FROM users LIMIT 100 OFFSET 20");
      assertEquals(100L, PaginationSyntaxHelper.extractPageSize(plainSelect));
    }

    @Test
    @DisplayName("Should extract pageSize from MySQL comma syntax")
    void testExtractFromMySqlCommaSyntax() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT * FROM users LIMIT 20, 100");
      assertEquals(100L, PaginationSyntaxHelper.extractPageSize(plainSelect));
    }

    @Test
    @DisplayName("Should extract pageSize from TOP clause")
    void testExtractFromTop() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT TOP 100 * FROM users");
      assertEquals(100L, PaginationSyntaxHelper.extractPageSize(plainSelect));
    }

    @Test
    @DisplayName("Should extract pageSize from FETCH FIRST clause")
    void testExtractFromFetchFirst() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT * FROM users FETCH FIRST 100 ROWS ONLY");
      assertEquals(100L, PaginationSyntaxHelper.extractPageSize(plainSelect));
    }

    @Test
    @DisplayName("Should extract pageSize from FETCH NEXT clause")
    void testExtractFromFetchNext() throws Exception {
      PlainSelect plainSelect = parsePlainSelect(
          "SELECT * FROM users ORDER BY id OFFSET 20 ROWS FETCH NEXT 100 ROWS ONLY");
      assertEquals(100L, PaginationSyntaxHelper.extractPageSize(plainSelect));
    }

    @Test
    @DisplayName("Should return null for parameter placeholder in LIMIT")
    void testParameterPlaceholder() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT * FROM users LIMIT ?");
      assertNull(PaginationSyntaxHelper.extractPageSize(plainSelect));
    }
  }

  // ==================== extractOffset Tests ====================

  @Nested
  @DisplayName("extractOffset() - Offset Extraction")
  class ExtractOffsetTests {

    @Test
    @DisplayName("Should return null for null PlainSelect")
    void testNullPlainSelect() {
      assertNull(PaginationSyntaxHelper.extractOffset(null, null));
    }

    @Test
    @DisplayName("Should return null for SELECT without offset")
    void testSelectWithoutOffset() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT * FROM users LIMIT 100");
      assertNull(PaginationSyntaxHelper.extractOffset(plainSelect, plainSelect.getLimit()));
    }

    @Test
    @DisplayName("Should extract offset from OFFSET keyword")
    void testExtractFromOffsetKeyword() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT * FROM users LIMIT 100 OFFSET 20");
      assertEquals(20L, PaginationSyntaxHelper.extractOffset(plainSelect, plainSelect.getLimit()));
    }

    @Test
    @DisplayName("Should extract offset from MySQL comma syntax")
    void testExtractFromMySqlCommaSyntax() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT * FROM users LIMIT 20, 100");
      assertEquals(20L, PaginationSyntaxHelper.extractOffset(plainSelect, plainSelect.getLimit()));
    }

    @Test
    @DisplayName("Should extract offset from SQL Server OFFSET ROWS")
    void testExtractFromSqlServerOffsetRows() throws Exception {
      PlainSelect plainSelect = parsePlainSelect(
          "SELECT * FROM users ORDER BY id OFFSET 50 ROWS FETCH NEXT 100 ROWS ONLY");
      assertEquals(50L, PaginationSyntaxHelper.extractOffset(plainSelect, plainSelect.getLimit()));
    }

    @Test
    @DisplayName("Should return null for parameter placeholder in OFFSET")
    void testParameterPlaceholder() throws Exception {
      PlainSelect plainSelect = parsePlainSelect("SELECT * FROM users LIMIT 100 OFFSET ?");
      assertNull(PaginationSyntaxHelper.extractOffset(plainSelect, plainSelect.getLimit()));
    }
  }

  // ==================== Helper Methods ====================

  private PlainSelect parsePlainSelect(String sql) throws Exception {
    Select select = (Select) CCJSqlParserUtil.parse(sql);
    return (PlainSelect) select.getSelectBody();
  }
}

package com.footstone.sqlguard.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for enum classes (RiskLevel and SqlCommandType).
 * Tests ordering, comparison, and utility methods.
 */
@DisplayName("Enums Tests")
class EnumsTest {

  // RiskLevel Tests

  @Test
  @DisplayName("RiskLevel ordering: SAFE < LOW < MEDIUM < HIGH < CRITICAL")
  void testRiskLevelOrdering() {
    // Assert natural ordering
    assertTrue(RiskLevel.SAFE.compareTo(RiskLevel.LOW) < 0);
    assertTrue(RiskLevel.LOW.compareTo(RiskLevel.MEDIUM) < 0);
    assertTrue(RiskLevel.MEDIUM.compareTo(RiskLevel.HIGH) < 0);
    assertTrue(RiskLevel.HIGH.compareTo(RiskLevel.CRITICAL) < 0);
  }

  @Test
  @DisplayName("RiskLevel.compareTo works correctly for all combinations")
  void testRiskLevelCompareTo() {
    // Test equal
    assertEquals(0, RiskLevel.MEDIUM.compareTo(RiskLevel.MEDIUM));

    // Test less than
    assertTrue(RiskLevel.SAFE.compareTo(RiskLevel.CRITICAL) < 0);
    assertTrue(RiskLevel.LOW.compareTo(RiskLevel.HIGH) < 0);

    // Test greater than
    assertTrue(RiskLevel.CRITICAL.compareTo(RiskLevel.SAFE) > 0);
    assertTrue(RiskLevel.HIGH.compareTo(RiskLevel.LOW) > 0);
  }

  @Test
  @DisplayName("RiskLevel.getSeverity returns correct ordinal values")
  void testRiskLevelGetSeverity() {
    assertEquals(0, RiskLevel.SAFE.getSeverity());
    assertEquals(1, RiskLevel.LOW.getSeverity());
    assertEquals(2, RiskLevel.MEDIUM.getSeverity());
    assertEquals(3, RiskLevel.HIGH.getSeverity());
    assertEquals(4, RiskLevel.CRITICAL.getSeverity());
  }

  @Test
  @DisplayName("RiskLevel ordinal matches severity")
  void testRiskLevelOrdinalMatchesSeverity() {
    for (RiskLevel level : RiskLevel.values()) {
      assertEquals(level.ordinal(), level.getSeverity());
    }
  }

  // SqlCommandType Tests

  @Test
  @DisplayName("SqlCommandType has expected values")
  void testSqlCommandTypeValues() {
    SqlCommandType[] types = SqlCommandType.values();
    assertEquals(5, types.length);

    // Verify all expected types exist
    boolean hasSelect = false;
    boolean hasUpdate = false;
    boolean hasDelete = false;
    boolean hasInsert = false;
    boolean hasUnknown = false;

    for (SqlCommandType type : types) {
      if (type == SqlCommandType.SELECT) {
        hasSelect = true;
      }
      if (type == SqlCommandType.UPDATE) {
        hasUpdate = true;
      }
      if (type == SqlCommandType.DELETE) {
        hasDelete = true;
      }
      if (type == SqlCommandType.INSERT) {
        hasInsert = true;
      }
      if (type == SqlCommandType.UNKNOWN) {
        hasUnknown = true;
      }
    }

    assertTrue(hasSelect);
    assertTrue(hasUpdate);
    assertTrue(hasDelete);
    assertTrue(hasInsert);
    assertTrue(hasUnknown);
  }

  @Test
  @DisplayName("SqlCommandType.fromString works with uppercase")
  void testSqlCommandTypeFromStringUppercase() {
    assertEquals(SqlCommandType.SELECT, SqlCommandType.fromString("SELECT"));
    assertEquals(SqlCommandType.UPDATE, SqlCommandType.fromString("UPDATE"));
    assertEquals(SqlCommandType.DELETE, SqlCommandType.fromString("DELETE"));
    assertEquals(SqlCommandType.INSERT, SqlCommandType.fromString("INSERT"));
  }

  @Test
  @DisplayName("SqlCommandType.fromString works with lowercase")
  void testSqlCommandTypeFromStringLowercase() {
    assertEquals(SqlCommandType.SELECT, SqlCommandType.fromString("select"));
    assertEquals(SqlCommandType.UPDATE, SqlCommandType.fromString("update"));
    assertEquals(SqlCommandType.DELETE, SqlCommandType.fromString("delete"));
    assertEquals(SqlCommandType.INSERT, SqlCommandType.fromString("insert"));
  }

  @Test
  @DisplayName("SqlCommandType.fromString works with mixed case")
  void testSqlCommandTypeFromStringMixedCase() {
    assertEquals(SqlCommandType.SELECT, SqlCommandType.fromString("SeLeCt"));
    assertEquals(SqlCommandType.UPDATE, SqlCommandType.fromString("UpDaTe"));
    assertEquals(SqlCommandType.DELETE, SqlCommandType.fromString("DeLeTe"));
    assertEquals(SqlCommandType.INSERT, SqlCommandType.fromString("InSeRt"));
  }

  @Test
  @DisplayName("SqlCommandType.fromString returns null for unknown type")
  void testSqlCommandTypeFromStringUnknown() {
    assertEquals(SqlCommandType.UNKNOWN, SqlCommandType.fromString("UNKNOWN"));
    assertEquals(null, SqlCommandType.fromString("DROP"));
    assertEquals(null, SqlCommandType.fromString(""));
    assertEquals(null, SqlCommandType.fromString(null));
  }

  @Test
  @DisplayName("SqlCommandType.fromString handles whitespace")
  void testSqlCommandTypeFromStringWithWhitespace() {
    assertEquals(SqlCommandType.SELECT, SqlCommandType.fromString("  SELECT  "));
    assertEquals(SqlCommandType.UPDATE, SqlCommandType.fromString(" UPDATE "));
  }

  @Test
  @DisplayName("All SqlCommandType values are non-null")
  void testSqlCommandTypeValuesNonNull() {
    for (SqlCommandType type : SqlCommandType.values()) {
      assertNotNull(type);
      assertNotNull(type.name());
    }
  }
}









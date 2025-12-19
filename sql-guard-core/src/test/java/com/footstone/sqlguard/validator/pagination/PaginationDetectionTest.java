package com.footstone.sqlguard.validator.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Test class for pagination detection infrastructure.
 * Tests PaginationType enum and related pagination detection functionality.
 */
public class PaginationDetectionTest {

  /**
   * Test that PaginationType enum has exactly 3 constants: LOGICAL, PHYSICAL, NONE.
   */
  @Test
  public void testPaginationTypeEnum() {
    // Verify enum has exactly 3 constants
    PaginationType[] types = PaginationType.values();
    assertNotNull(types);
    assertEquals(3, types.length, "PaginationType should have exactly 3 constants");

    // Verify each constant exists and can be accessed
    assertEquals(PaginationType.LOGICAL, PaginationType.valueOf("LOGICAL"));
    assertEquals(PaginationType.PHYSICAL, PaginationType.valueOf("PHYSICAL"));
    assertEquals(PaginationType.NONE, PaginationType.valueOf("NONE"));
  }
}













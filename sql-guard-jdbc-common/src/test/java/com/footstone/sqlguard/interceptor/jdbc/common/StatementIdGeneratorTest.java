package com.footstone.sqlguard.interceptor.jdbc.common;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StatementIdGenerator}.
 *
 * <p>Validates the uniqueness and consistency of generated statementId.</p>
 */
class StatementIdGeneratorTest {

    @Test
    void testGenerateUniqueStatementId() {
        // Different SQL should generate different statementId
        String sql1 = "SELECT * FROM users WHERE id = ?";
        String sql2 = "SELECT * FROM orders WHERE user_id = ?";
        String sql3 = "UPDATE users SET name = ? WHERE id = ?";

        String id1 = StatementIdGenerator.generate("druid", "masterDB", sql1);
        String id2 = StatementIdGenerator.generate("druid", "masterDB", sql2);
        String id3 = StatementIdGenerator.generate("druid", "masterDB", sql3);

        // All should be different
        assertNotEquals(id1, id2, "Different SQL should have different statementId");
        assertNotEquals(id1, id3, "Different SQL should have different statementId");
        assertNotEquals(id2, id3, "Different SQL should have different statementId");

        // Verify format: jdbc.{type}:{datasource}:{hash}
        assertTrue(id1.startsWith("jdbc.druid:masterDB:"), "Should have correct format");
        assertTrue(id2.startsWith("jdbc.druid:masterDB:"), "Should have correct format");
        assertTrue(id3.startsWith("jdbc.druid:masterDB:"), "Should have correct format");

        // Verify hash length (8 hex characters)
        String hash1 = id1.substring("jdbc.druid:masterDB:".length());
        String hash2 = id2.substring("jdbc.druid:masterDB:".length());
        String hash3 = id3.substring("jdbc.druid:masterDB:".length());

        assertEquals(8, hash1.length(), "Hash should be 8 characters");
        assertEquals(8, hash2.length(), "Hash should be 8 characters");
        assertEquals(8, hash3.length(), "Hash should be 8 characters");
    }

    @Test
    void testGenerateConsistentForSameSql() {
        // Same SQL should always generate same statementId
        String sql = "SELECT * FROM users WHERE id = ?";

        String id1 = StatementIdGenerator.generate("druid", "masterDB", sql);
        String id2 = StatementIdGenerator.generate("druid", "masterDB", sql);
        String id3 = StatementIdGenerator.generate("druid", "masterDB", sql);

        assertEquals(id1, id2, "Same SQL should generate same statementId");
        assertEquals(id2, id3, "Same SQL should generate same statementId");
    }

    @Test
    void testGenerateDifferentDatasources() {
        // Same SQL in different datasources should have different statementId
        String sql = "SELECT * FROM users WHERE id = ?";

        String id1 = StatementIdGenerator.generate("druid", "masterDB", sql);
        String id2 = StatementIdGenerator.generate("druid", "slaveDB", sql);

        assertNotEquals(id1, id2, "Different datasources should have different statementId");

        assertTrue(id1.contains("masterDB"), "Should contain datasource name");
        assertTrue(id2.contains("slaveDB"), "Should contain datasource name");
    }

    @Test
    void testGenerateDifferentInterceptorTypes() {
        // Same SQL in different interceptors should have different statementId
        String sql = "SELECT * FROM users WHERE id = ?";

        String id1 = StatementIdGenerator.generate("druid", "masterDB", sql);
        String id2 = StatementIdGenerator.generate("hikari", "masterDB", sql);
        String id3 = StatementIdGenerator.generate("p6spy", "masterDB", sql);

        assertNotEquals(id1, id2, "Different interceptors should have different statementId");
        assertNotEquals(id1, id3, "Different interceptors should have different statementId");
        assertNotEquals(id2, id3, "Different interceptors should have different statementId");

        assertTrue(id1.contains("druid"), "Should contain interceptor type");
        assertTrue(id2.contains("hikari"), "Should contain interceptor type");
        assertTrue(id3.contains("p6spy"), "Should contain interceptor type");
    }

    @Test
    void testGenerateShortHash() {
        String sql = "SELECT * FROM users WHERE id = ?";
        String hash = StatementIdGenerator.generateShortHash(sql);

        assertNotNull(hash, "Hash should not be null");
        assertEquals(8, hash.length(), "Hash should be 8 characters");
        assertTrue(hash.matches("[0-9a-f]{8}"), "Hash should be 8 hex characters");
    }

    @Test
    void testGenerateShortHashConsistency() {
        String sql = "SELECT * FROM users WHERE id = ?";

        String hash1 = StatementIdGenerator.generateShortHash(sql);
        String hash2 = StatementIdGenerator.generateShortHash(sql);
        String hash3 = StatementIdGenerator.generateShortHash(sql);

        assertEquals(hash1, hash2, "Same SQL should produce same hash");
        assertEquals(hash2, hash3, "Same SQL should produce same hash");
    }

    @Test
    void testGenerateShortHashForEmptySql() {
        String hash1 = StatementIdGenerator.generateShortHash(null);
        String hash2 = StatementIdGenerator.generateShortHash("");

        assertEquals("unknown", hash1, "Null SQL should return 'unknown'");
        assertEquals("unknown", hash2, "Empty SQL should return 'unknown'");
    }

    @Test
    void testGenerateFullHash() {
        String sql = "SELECT * FROM users WHERE id = ?";
        String fullHash = StatementIdGenerator.generateFullHash(sql);

        assertNotNull(fullHash, "Full hash should not be null");
        assertEquals(32, fullHash.length(), "Full MD5 hash should be 32 characters");
        assertTrue(fullHash.matches("[0-9a-f]{32}"), "Hash should be 32 hex characters");
    }

    @Test
    void testCollisionProbability() {
        // Test with 1000 different SQL statements to verify low collision rate
        Map<String, String> statementIdMap = new HashMap<>();
        int collisions = 0;

        for (int i = 0; i < 1000; i++) {
            String sql = "SELECT * FROM table" + i + " WHERE id = " + i;
            String statementId = StatementIdGenerator.generate("druid", "masterDB", sql);

            if (statementIdMap.containsKey(statementId)) {
                collisions++;
                System.out.println("Collision detected: " + sql + " -> " + statementId);
                System.out.println("  Conflicts with: " + statementIdMap.get(statementId));
            } else {
                statementIdMap.put(statementId, sql);
            }
        }

        // With 8-character hash (4 bytes), collision probability is ~1 in 4 billion
        // For 1000 samples, we expect 0 collisions
        assertTrue(collisions < 5,
            "Should have very few collisions (expected 0, got " + collisions + ")");
    }

    @Test
    void testNullAndEmptyHandling() {
        // Test with null SQL
        String id1 = StatementIdGenerator.generate("druid", "masterDB", null);
        assertTrue(id1.endsWith(":unknown"), "Null SQL should use 'unknown' hash");

        // Test with empty SQL
        String id2 = StatementIdGenerator.generate("druid", "masterDB", "");
        assertTrue(id2.endsWith(":unknown"), "Empty SQL should use 'unknown' hash");

        // Test with null datasource (should use default)
        String id3 = StatementIdGenerator.generate("druid", null, "SELECT 1");
        assertTrue(id3.contains("default"), "Null datasource should use 'default'");

        // Test with null interceptor type (should use default)
        String id4 = StatementIdGenerator.generate(null, "masterDB", "SELECT 1");
        assertTrue(id4.startsWith("jdbc.jdbc:"), "Null interceptor should use 'jdbc'");
    }

    @Test
    void testFormatValidation() {
        String sql = "SELECT * FROM users";
        String statementId = StatementIdGenerator.generate("druid", "masterDB", sql);

        // Validate format: jdbc.{interceptor}:{datasource}:{hash}
        String[] parts = statementId.split(":");
        assertEquals(3, parts.length, "StatementId should have 3 parts separated by ':'");

        assertTrue(parts[0].startsWith("jdbc."), "First part should start with 'jdbc.'");
        assertEquals("masterDB", parts[1], "Second part should be datasource name");
        assertEquals(8, parts[2].length(), "Third part should be 8-character hash");
    }

    @Test
    void testComparisonWithOldFormat() {
        // Old format: jdbc.druid:masterDB (no hash)
        // New format: jdbc.druid:masterDB:a3f4b2c1 (with hash)

        String sql1 = "SELECT * FROM users WHERE id = 1";
        String sql2 = "SELECT * FROM users WHERE id = 2";

        String newId1 = StatementIdGenerator.generate("druid", "masterDB", sql1);
        String newId2 = StatementIdGenerator.generate("druid", "masterDB", sql2);

        // Verify new format makes them unique
        assertNotEquals(newId1, newId2,
            "New format should make different SQL have unique IDs");

        // Verify format difference
        assertTrue(newId1.split(":").length == 3,
            "New format should have 3 colon-separated parts");
        assertTrue(newId2.split(":").length == 3,
            "New format should have 3 colon-separated parts");
    }
}

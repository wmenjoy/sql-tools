package com.footstone.sqlguard.scanner.mybatis;

import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.scanner.mybatis.model.RiskLevel;
import com.footstone.sqlguard.scanner.mybatis.model.SecurityRisk;
import com.footstone.sqlguard.scanner.mybatis.model.SqlPosition;
import com.footstone.sqlguard.scanner.mybatis.util.SecurityRiskConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SecurityRiskConverter.
 */
public class SecurityRiskConverterTest {

    @Test
    public void testBasicConversion() {
        // Arrange
        SecurityRisk risk = new SecurityRisk(
            "sortColumn",
            RiskLevel.CRITICAL,
            "String parameter in ORDER BY clause can lead to SQL injection",
            "Use whitelist validation or enum",
            SqlPosition.ORDER_BY,
            "String"
        );
        String mapperId = "com.example.UserMapper.selectUsers";

        // Act
        ViolationInfo violation = SecurityRiskConverter.toViolationInfo(risk, mapperId);

        // Assert
        assertNotNull(violation);
        assertEquals(com.footstone.sqlguard.core.model.RiskLevel.CRITICAL, violation.getRiskLevel());
        assertTrue(violation.getMessage().contains(mapperId));
        assertTrue(violation.getMessage().contains("sortColumn"));
        assertTrue(violation.getMessage().contains("ORDER_BY"));
        assertEquals("Use whitelist validation or enum", violation.getSuggestion());
    }

    @Test
    public void testAllRiskLevels() {
        String mapperId = "com.example.UserMapper.selectUsers";

        // Test CRITICAL
        SecurityRisk critical = new SecurityRisk("param", RiskLevel.CRITICAL, "msg", "rec", SqlPosition.WHERE, "String");
        ViolationInfo criticalViolation = SecurityRiskConverter.toViolationInfo(critical, mapperId);
        assertEquals(com.footstone.sqlguard.core.model.RiskLevel.CRITICAL, criticalViolation.getRiskLevel());

        // Test HIGH
        SecurityRisk high = new SecurityRisk("param", RiskLevel.HIGH, "msg", "rec", SqlPosition.WHERE, "String");
        ViolationInfo highViolation = SecurityRiskConverter.toViolationInfo(high, mapperId);
        assertEquals(com.footstone.sqlguard.core.model.RiskLevel.HIGH, highViolation.getRiskLevel());

        // Test MEDIUM
        SecurityRisk medium = new SecurityRisk("param", RiskLevel.MEDIUM, "msg", "rec", SqlPosition.WHERE, "String");
        ViolationInfo mediumViolation = SecurityRiskConverter.toViolationInfo(medium, mapperId);
        assertEquals(com.footstone.sqlguard.core.model.RiskLevel.MEDIUM, mediumViolation.getRiskLevel());

        // Test LOW
        SecurityRisk low = new SecurityRisk("param", RiskLevel.LOW, "msg", "rec", SqlPosition.WHERE, "String");
        ViolationInfo lowViolation = SecurityRiskConverter.toViolationInfo(low, mapperId);
        assertEquals(com.footstone.sqlguard.core.model.RiskLevel.LOW, lowViolation.getRiskLevel());

        // Test INFO (maps to LOW)
        SecurityRisk info = new SecurityRisk("param", RiskLevel.INFO, "msg", "rec", SqlPosition.WHERE, "String");
        ViolationInfo infoViolation = SecurityRiskConverter.toViolationInfo(info, mapperId);
        assertEquals(com.footstone.sqlguard.core.model.RiskLevel.LOW, infoViolation.getRiskLevel());
    }

    @Test
    public void testMessageFormatting() {
        String mapperId = "com.example.UserMapper.selectUsers";

        SecurityRisk risk = new SecurityRisk(
            "sortColumn",
            RiskLevel.CRITICAL,
            "Potential SQL injection",
            "Use whitelist",
            SqlPosition.ORDER_BY,
            "String"
        );

        ViolationInfo violation = SecurityRiskConverter.toViolationInfo(risk, mapperId);

        // Message should contain all context
        String message = violation.getMessage();
        assertTrue(message.contains("com.example.UserMapper.selectUsers"), "Should contain mapper ID");
        assertTrue(message.contains("sortColumn"), "Should contain parameter name");
        assertTrue(message.contains("String"), "Should contain parameter type");
        assertTrue(message.contains("ORDER_BY"), "Should contain position");
        assertTrue(message.contains("Potential SQL injection"), "Should contain original message");
    }

    @Test
    public void testMessageWithoutParameterType() {
        String mapperId = "com.example.UserMapper.selectUsers";

        SecurityRisk risk = new SecurityRisk(
            "id",
            RiskLevel.LOW,
            "Test message",
            "Test recommendation",
            SqlPosition.WHERE,
            null  // No parameter type
        );

        ViolationInfo violation = SecurityRiskConverter.toViolationInfo(risk, mapperId);

        // Should still format correctly
        assertTrue(violation.getMessage().contains("id"));
        assertTrue(violation.getMessage().contains("WHERE"));
    }

    @Test
    public void testMessageWithoutParameterName() {
        String mapperId = "com.example.UserMapper.selectUsers";

        SecurityRisk risk = new SecurityRisk(
            null,  // No parameter name
            RiskLevel.INFO,
            "General message",
            "General recommendation",
            SqlPosition.SELECT,
            null
        );

        ViolationInfo violation = SecurityRiskConverter.toViolationInfo(risk, mapperId);

        // Should still contain mapper ID and message
        assertTrue(violation.getMessage().contains(mapperId));
        assertTrue(violation.getMessage().contains("General message"));
    }

    @Test
    public void testAllSqlPositions() {
        String mapperId = "com.example.UserMapper.selectUsers";

        for (SqlPosition position : SqlPosition.values()) {
            SecurityRisk risk = new SecurityRisk(
                "param",
                RiskLevel.MEDIUM,
                "Test message",
                "Test recommendation",
                position,
                "String"
            );

            ViolationInfo violation = SecurityRiskConverter.toViolationInfo(risk, mapperId);

            // Should contain position in message
            assertTrue(violation.getMessage().contains(position.toString()));
        }
    }

    @Test
    public void testNullRiskThrowsException() {
        String mapperId = "com.example.UserMapper.selectUsers";

        assertThrows(IllegalArgumentException.class, () -> {
            SecurityRiskConverter.toViolationInfo(null, mapperId);
        });
    }

    @Test
    public void testNullMapperIdThrowsException() {
        SecurityRisk risk = new SecurityRisk("param", RiskLevel.INFO, "msg", "rec", SqlPosition.WHERE, "String");

        assertThrows(IllegalArgumentException.class, () -> {
            SecurityRiskConverter.toViolationInfo(risk, null);
        });
    }

    @Test
    public void testEmptyMapperIdThrowsException() {
        SecurityRisk risk = new SecurityRisk("param", RiskLevel.INFO, "msg", "rec", SqlPosition.WHERE, "String");

        assertThrows(IllegalArgumentException.class, () -> {
            SecurityRiskConverter.toViolationInfo(risk, "");
        });
    }
}

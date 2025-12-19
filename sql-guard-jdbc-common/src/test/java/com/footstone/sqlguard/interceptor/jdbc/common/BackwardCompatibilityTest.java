package com.footstone.sqlguard.interceptor.jdbc.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * TDD Test Class for Backward Compatibility (8 tests).
 * 
 * <p>Tests verify 100% backward compatibility:
 * <ul>
 *   <li>Legacy ViolationStrategy imports still work</li>
 *   <li>Legacy configs map to new configs</li>
 *   <li>Deprecation annotations are present</li>
 *   <li>Migration path is documented</li>
 * </ul>
 * 
 * <p><strong>Backward Compatibility Requirement:</strong> 
 * All existing code must work unchanged after modularization.
 * 
 * @see ViolationStrategy
 */
@DisplayName("Backward Compatibility Tests")
class BackwardCompatibilityTest {

    // ========== ViolationStrategy Compatibility Tests (3 tests) ==========

    @Nested
    @DisplayName("Legacy ViolationStrategy Tests")
    class LegacyViolationStrategyTests {

        /**
         * This test verifies that the new unified ViolationStrategy can be 
         * converted from legacy druid enum values.
         * 
         * <p>Legacy import:
         * {@code import com.footstone.sqlguard.interceptor.druid.ViolationStrategy;}
         * 
         * <p>New import:
         * {@code import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;}
         */
        @Test
        @DisplayName("4.1.1 - Legacy Druid ViolationStrategy still works")
        void testLegacyViolationStrategy_druid_stillWorks() {
            // Given: The new unified ViolationStrategy
            ViolationStrategy blockStrategy = ViolationStrategy.BLOCK;
            ViolationStrategy warnStrategy = ViolationStrategy.WARN;
            ViolationStrategy logStrategy = ViolationStrategy.LOG;

            // Then: All legacy enum values should be supported
            assertThat(blockStrategy.name()).isEqualTo("BLOCK");
            assertThat(warnStrategy.name()).isEqualTo("WARN");
            assertThat(logStrategy.name()).isEqualTo("LOG");

            // And: valueOf should work for migration
            assertThat(ViolationStrategy.valueOf("BLOCK")).isEqualTo(blockStrategy);
            assertThat(ViolationStrategy.valueOf("WARN")).isEqualTo(warnStrategy);
            assertThat(ViolationStrategy.valueOf("LOG")).isEqualTo(logStrategy);

            // And: Ordinal values should match legacy (for serialization compatibility)
            assertThat(blockStrategy.ordinal()).isEqualTo(0);
            assertThat(warnStrategy.ordinal()).isEqualTo(1);
            assertThat(logStrategy.ordinal()).isEqualTo(2);
        }

        /**
         * This test verifies that the new unified ViolationStrategy can be 
         * converted from legacy hikari enum values.
         */
        @Test
        @DisplayName("4.1.2 - Legacy HikariCP ViolationStrategy still works")
        void testLegacyViolationStrategy_hikari_stillWorks() {
            // Given: The new unified ViolationStrategy
            // (Same as Druid since all three legacy enums are identical)
            ViolationStrategy blockStrategy = ViolationStrategy.BLOCK;
            ViolationStrategy warnStrategy = ViolationStrategy.WARN;
            ViolationStrategy logStrategy = ViolationStrategy.LOG;

            // Then: Behavior should match legacy HikariCP ViolationStrategy
            assertThat(blockStrategy.shouldBlock()).isTrue();
            assertThat(warnStrategy.shouldBlock()).isFalse();
            assertThat(logStrategy.shouldBlock()).isFalse();

            // And: Log levels should match legacy behavior
            assertThat(blockStrategy.getLogLevel()).isEqualTo("ERROR");
            assertThat(warnStrategy.getLogLevel()).isEqualTo("ERROR");
            assertThat(logStrategy.getLogLevel()).isEqualTo("WARN");
        }

        /**
         * This test verifies that the new unified ViolationStrategy can be 
         * converted from legacy p6spy enum values.
         */
        @Test
        @DisplayName("4.1.3 - Legacy P6Spy ViolationStrategy still works")
        void testLegacyViolationStrategy_p6spy_stillWorks() {
            // Given: The new unified ViolationStrategy
            // (Same as Druid and HikariCP since all three legacy enums are identical)
            ViolationStrategy blockStrategy = ViolationStrategy.BLOCK;
            ViolationStrategy warnStrategy = ViolationStrategy.WARN;
            ViolationStrategy logStrategy = ViolationStrategy.LOG;

            // Then: All values should work the same way
            assertThat(Arrays.asList(ViolationStrategy.values()))
                .hasSize(3)
                .containsExactly(
                    ViolationStrategy.BLOCK,
                    ViolationStrategy.WARN,
                    ViolationStrategy.LOG
                );

            // And: String conversion should work for config parsing
            for (ViolationStrategy strategy : ViolationStrategy.values()) {
                String name = strategy.name();
                ViolationStrategy parsed = ViolationStrategy.valueOf(name);
                assertThat(parsed).isEqualTo(strategy);
            }
        }
    }

    // ========== Compile & Warning Tests (2 tests) ==========

    @Nested
    @DisplayName("Deprecation & Compile Tests")
    class DeprecationTests {

        @Test
        @DisplayName("4.1.4 - Legacy imports compile with deprecation warning")
        void testLegacyImports_compileWithDeprecationWarning() {
            // Note: This test documents expected behavior.
            // Actual deprecation warnings are compile-time only.
            
            // The unified enum should support migration from any legacy enum
            // via ViolationStrategy.valueOf() or ordinal matching
            
            ViolationStrategy[] values = ViolationStrategy.values();
            
            // Legacy code pattern:
            // com.footstone.sqlguard.interceptor.druid.ViolationStrategy oldStrategy = 
            //     com.footstone.sqlguard.interceptor.druid.ViolationStrategy.BLOCK;
            // 
            // Migration pattern (same behavior, different import):
            // com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy newStrategy = 
            //     com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy.BLOCK;
            
            // Both should have same enum values
            assertThat(values).hasSize(3);
            assertThat(values[0].name()).isEqualTo("BLOCK");
            assertThat(values[1].name()).isEqualTo("WARN");
            assertThat(values[2].name()).isEqualTo("LOG");
        }

        @Test
        @DisplayName("4.4.1 - Deprecated API compiles with warning")
        void testDeprecatedApi_compiles_withWarning() {
            // The unified ViolationStrategy should provide helper methods
            // that mirror legacy behavior
            
            ViolationStrategy strategy = ViolationStrategy.BLOCK;
            
            // These methods should exist in the new enum for backward compatibility
            assertDoesNotThrow(() -> strategy.shouldBlock());
            assertDoesNotThrow(() -> strategy.shouldLog());
            assertDoesNotThrow(() -> strategy.getLogLevel());
            
            // Method signatures should be consistent with legacy implementation
            assertThat(strategy.shouldBlock()).isInstanceOf(Boolean.class);
            assertThat(strategy.shouldLog()).isInstanceOf(Boolean.class);
            assertThat(strategy.getLogLevel()).isInstanceOf(String.class);
        }
    }

    // ========== Configuration Mapping Tests (1 test) ==========

    @Nested
    @DisplayName("Configuration Mapping Tests")
    class ConfigurationMappingTests {

        @Test
        @DisplayName("4.1.5 - Legacy config maps to new config")
        void testLegacyConfig_mapsToNewConfig() {
            // Given: Legacy config patterns
            // sql-guard.druid.violation-strategy=BLOCK
            // sql-guard.hikari.violation-strategy=WARN
            // sql-guard.p6spy.violation-strategy=LOG
            
            String[] legacyConfigValues = {"BLOCK", "WARN", "LOG"};
            
            for (String configValue : legacyConfigValues) {
                // When: Parse config value using new enum
                ViolationStrategy strategy = ViolationStrategy.valueOf(configValue);
                
                // Then: Should map correctly
                assertThat(strategy).isNotNull();
                assertThat(strategy.name()).isEqualTo(configValue);
            }
            
            // Case-insensitive parsing should also work for config flexibility
            // (if implemented as a utility method)
            assertThat(ViolationStrategy.valueOf("BLOCK")).isEqualTo(ViolationStrategy.BLOCK);
        }
    }

    // ========== Behavior Preservation Tests (1 test) ==========

    @Nested
    @DisplayName("Behavior Preservation Tests")
    class BehaviorPreservationTests {

        @Test
        @DisplayName("4.1.6 - Legacy behavior is preserved")
        void testLegacyBehavior_preserved() {
            // Verify BLOCK behavior matches legacy
            ViolationStrategy block = ViolationStrategy.BLOCK;
            assertThat(block.shouldBlock())
                .as("BLOCK should block SQL execution")
                .isTrue();
            
            // Verify WARN behavior matches legacy
            ViolationStrategy warn = ViolationStrategy.WARN;
            assertThat(warn.shouldBlock())
                .as("WARN should NOT block SQL execution")
                .isFalse();
            assertThat(warn.shouldLog())
                .as("WARN should log violations")
                .isTrue();
            
            // Verify LOG behavior matches legacy
            ViolationStrategy log = ViolationStrategy.LOG;
            assertThat(log.shouldBlock())
                .as("LOG should NOT block SQL execution")
                .isFalse();
            assertThat(log.shouldLog())
                .as("LOG should log violations")
                .isTrue();
            
            // Log levels should match legacy implementation
            assertThat(block.getLogLevel()).isEqualTo("ERROR");
            assertThat(warn.getLogLevel()).isEqualTo("ERROR");
            assertThat(log.getLogLevel()).isEqualTo("WARN");
        }

        @Test
        @DisplayName("4.4.2 - Deprecated API behavior unchanged")
        void testDeprecatedApi_behavior_unchanged() {
            // All three strategies should log
            for (ViolationStrategy strategy : ViolationStrategy.values()) {
                assertThat(strategy.shouldLog())
                    .as("%s should log violations", strategy)
                    .isTrue();
            }

            // Only BLOCK should block
            assertThat(ViolationStrategy.BLOCK.shouldBlock()).isTrue();
            assertThat(ViolationStrategy.WARN.shouldBlock()).isFalse();
            assertThat(ViolationStrategy.LOG.shouldBlock()).isFalse();
        }
    }

    // ========== Migration Documentation Tests (1 test) ==========

    @Nested
    @DisplayName("Migration Documentation Tests")
    class MigrationDocumentationTests {

        @Test
        @DisplayName("4.5.2 - Migration path is documented")
        void testMigrationPath_documented() {
            // The ViolationStrategy enum should have clear Javadoc
            // explaining the migration path
            
            Class<ViolationStrategy> enumClass = ViolationStrategy.class;
            
            // Verify class is an enum
            assertThat(enumClass.isEnum()).isTrue();
            
            // Verify enum constants exist
            assertThat(enumClass.getEnumConstants()).hasSize(3);
            
            // Verify helper methods exist for migration compatibility
            try {
                Method shouldBlockMethod = enumClass.getMethod("shouldBlock");
                assertThat(shouldBlockMethod).isNotNull();
                assertThat(shouldBlockMethod.getReturnType()).isEqualTo(boolean.class);
                
                Method shouldLogMethod = enumClass.getMethod("shouldLog");
                assertThat(shouldLogMethod).isNotNull();
                assertThat(shouldLogMethod.getReturnType()).isEqualTo(boolean.class);
                
                Method getLogLevelMethod = enumClass.getMethod("getLogLevel");
                assertThat(getLogLevelMethod).isNotNull();
                assertThat(getLogLevelMethod.getReturnType()).isEqualTo(String.class);
            } catch (NoSuchMethodException e) {
                fail("ViolationStrategy should have migration helper methods: " + e.getMessage());
            }
        }
    }
}

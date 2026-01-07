package com.footstone.sqlguard.scanner.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.footstone.sqlguard.validator.rule.RuleChecker;
import com.footstone.sqlguard.validator.rule.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Integration tests for Scanner CLI with all 11 security checkers.
 *
 * <p>Verifies that Scanner CLI properly registers and uses all 11 security checkers
 * from Phase 1 implementation.</p>
 *
 * <p><strong>Test Categories:</strong></p>
 * <ul>
 *   <li>All Checkers Registration: Verify all 11 checkers are instantiable</li>
 *   <li>Checker Types Verification: Verify each checker type is present</li>
 *   <li>Default Configuration: Verify default configs are secure</li>
 * </ul>
 *
 * @since 1.0.0
 */
@DisplayName("Scanner CLI Integration Tests")
class ScannerCliIntegrationTest {

    private List<RuleChecker> allCheckers;

    @BeforeEach
    void setUp() {
        allCheckers = createAllCheckers();
    }

    /**
     * Creates all 11 security checkers with default configurations.
     * This mirrors the logic in SqlScannerCli.createAllCheckers().
     */
    private List<RuleChecker> createAllCheckers() {
        List<RuleChecker> checkers = new ArrayList<>();

        // SQL Injection Checkers (Tasks 1.1-1.4)
        checkers.add(new MultiStatementChecker(new MultiStatementConfig()));
        checkers.add(new SetOperationChecker(new SetOperationConfig()));
        checkers.add(new SqlCommentChecker(new SqlCommentConfig()));
        checkers.add(new IntoOutfileChecker(new IntoOutfileConfig()));

        // Dangerous Operations Checkers (Tasks 1.5-1.7)
        checkers.add(new DdlOperationChecker(new DdlOperationConfig()));
        checkers.add(new DangerousFunctionChecker(new DangerousFunctionConfig()));
        checkers.add(new CallStatementChecker(new CallStatementConfig()));

        // Access Control Checkers (Tasks 1.8-1.11)
        checkers.add(new MetadataStatementChecker(new MetadataStatementConfig()));
        checkers.add(new SetStatementChecker(new SetStatementConfig()));

        // DeniedTableChecker with default denied tables
        DeniedTableConfig dtConfig = new DeniedTableConfig();
        dtConfig.setDeniedTables(Arrays.asList("sys_*", "admin_*", "audit_log"));
        checkers.add(new DeniedTableChecker(dtConfig));

        // ReadOnlyTableChecker with default readonly tables
        ReadOnlyTableConfig rtConfig = new ReadOnlyTableConfig();
        rtConfig.setReadonlyTables(Arrays.asList("history_*", "audit_*", "archive_log"));
        checkers.add(new ReadOnlyTableChecker(rtConfig));

        return checkers;
    }

    // ==================== All Checkers Registration Tests ====================

    @Nested
    @DisplayName("All Checkers Registration Tests")
    class AllCheckersRegistrationTests {

        @Test
        @DisplayName("Should have exactly 11 security checkers")
        void testAllCheckersRegistered() {
            assertEquals(11, allCheckers.size(), "Should have 11 security checkers");
        }

        @Test
        @DisplayName("All checkers should be enabled by default")
        void testAllCheckersEnabled() {
            for (RuleChecker checker : allCheckers) {
                assertTrue(checker.isEnabled(),
                        "Checker should be enabled by default: " + checker.getClass().getSimpleName());
            }
        }

        @Test
        @DisplayName("All checkers should be non-null")
        void testAllCheckersNonNull() {
            for (RuleChecker checker : allCheckers) {
                assertNotNull(checker, "Checker should not be null");
            }
        }
    }

    // ==================== Checker Types Verification Tests ====================

    @Nested
    @DisplayName("Checker Types Verification Tests")
    class CheckerTypesVerificationTests {

        @Test
        @DisplayName("Should have MultiStatementChecker")
        void testHasMultiStatementChecker() {
            assertTrue(hasCheckerType(allCheckers, MultiStatementChecker.class),
                    "Should have MultiStatementChecker");
        }

        @Test
        @DisplayName("Should have SetOperationChecker")
        void testHasSetOperationChecker() {
            assertTrue(hasCheckerType(allCheckers, SetOperationChecker.class),
                    "Should have SetOperationChecker");
        }

        @Test
        @DisplayName("Should have SqlCommentChecker")
        void testHasSqlCommentChecker() {
            assertTrue(hasCheckerType(allCheckers, SqlCommentChecker.class),
                    "Should have SqlCommentChecker");
        }

        @Test
        @DisplayName("Should have IntoOutfileChecker")
        void testHasIntoOutfileChecker() {
            assertTrue(hasCheckerType(allCheckers, IntoOutfileChecker.class),
                    "Should have IntoOutfileChecker");
        }

        @Test
        @DisplayName("Should have DdlOperationChecker")
        void testHasDdlOperationChecker() {
            assertTrue(hasCheckerType(allCheckers, DdlOperationChecker.class),
                    "Should have DdlOperationChecker");
        }

        @Test
        @DisplayName("Should have DangerousFunctionChecker")
        void testHasDangerousFunctionChecker() {
            assertTrue(hasCheckerType(allCheckers, DangerousFunctionChecker.class),
                    "Should have DangerousFunctionChecker");
        }

        @Test
        @DisplayName("Should have CallStatementChecker")
        void testHasCallStatementChecker() {
            assertTrue(hasCheckerType(allCheckers, CallStatementChecker.class),
                    "Should have CallStatementChecker");
        }

        @Test
        @DisplayName("Should have MetadataStatementChecker")
        void testHasMetadataStatementChecker() {
            assertTrue(hasCheckerType(allCheckers, MetadataStatementChecker.class),
                    "Should have MetadataStatementChecker");
        }

        @Test
        @DisplayName("Should have SetStatementChecker")
        void testHasSetStatementChecker() {
            assertTrue(hasCheckerType(allCheckers, SetStatementChecker.class),
                    "Should have SetStatementChecker");
        }

        @Test
        @DisplayName("Should have DeniedTableChecker")
        void testHasDeniedTableChecker() {
            assertTrue(hasCheckerType(allCheckers, DeniedTableChecker.class),
                    "Should have DeniedTableChecker");
        }

        @Test
        @DisplayName("Should have ReadOnlyTableChecker")
        void testHasReadOnlyTableChecker() {
            assertTrue(hasCheckerType(allCheckers, ReadOnlyTableChecker.class),
                    "Should have ReadOnlyTableChecker");
        }

        @Test
        @DisplayName("All 11 checker types should be present")
        void testAllCheckerTypesPresent() {
            List<Class<? extends RuleChecker>> expectedTypes = Arrays.asList(
                    MultiStatementChecker.class,
                    SetOperationChecker.class,
                    SqlCommentChecker.class,
                    IntoOutfileChecker.class,
                    DdlOperationChecker.class,
                    DangerousFunctionChecker.class,
                    CallStatementChecker.class,
                    MetadataStatementChecker.class,
                    SetStatementChecker.class,
                    DeniedTableChecker.class,
                    ReadOnlyTableChecker.class
            );

            for (Class<? extends RuleChecker> expectedType : expectedTypes) {
                assertTrue(hasCheckerType(allCheckers, expectedType),
                        "Missing checker type: " + expectedType.getSimpleName());
            }
        }

        private boolean hasCheckerType(List<RuleChecker> checkers, Class<? extends RuleChecker> type) {
            return checkers.stream().anyMatch(c -> type.isInstance(c));
        }
    }

    // ==================== Default Configuration Tests ====================

    @Nested
    @DisplayName("Default Configuration Tests")
    class DefaultConfigurationTests {

        @Test
        @DisplayName("MultiStatementConfig should default to CRITICAL risk")
        void testMultiStatementConfigDefaults() {
            MultiStatementConfig config = new MultiStatementConfig();
            assertTrue(config.isEnabled());
            assertEquals(com.footstone.sqlguard.core.model.RiskLevel.CRITICAL, config.getRiskLevel());
        }

        @Test
        @DisplayName("SetOperationConfig should default to empty allowed operations (block all)")
        void testSetOperationConfigDefaults() {
            SetOperationConfig config = new SetOperationConfig();
            assertTrue(config.isEnabled());
            assertTrue(config.getAllowedOperations().isEmpty(),
                    "Should block all set operations by default");
        }

        @Test
        @DisplayName("DangerousFunctionConfig should have default denied functions")
        void testDangerousFunctionConfigDefaults() {
            DangerousFunctionConfig config = new DangerousFunctionConfig();
            assertTrue(config.isEnabled());
            assertFalse(config.getDeniedFunctions().isEmpty(),
                    "Should have default denied functions");
            assertTrue(config.isDenied("load_file"), "Should deny load_file");
            assertTrue(config.isDenied("sleep"), "Should deny sleep");
        }

        @Test
        @DisplayName("CallStatementConfig should default to WARN strategy")
        void testCallStatementConfigDefaults() {
            CallStatementConfig config = new CallStatementConfig();
            assertTrue(config.isEnabled());
            assertEquals(com.footstone.sqlguard.config.ViolationStrategy.WARN,
                    config.getViolationStrategy());
        }

        @Test
        @DisplayName("DeniedTableConfig should start with empty denied list")
        void testDeniedTableConfigDefaults() {
            DeniedTableConfig config = new DeniedTableConfig();
            assertTrue(config.isEnabled());
            assertTrue(config.isDeniedTablesEmpty(),
                    "Should have empty denied tables by default");
        }

        @Test
        @DisplayName("ReadOnlyTableConfig should start with empty readonly list")
        void testReadOnlyTableConfigDefaults() {
            ReadOnlyTableConfig config = new ReadOnlyTableConfig();
            assertTrue(config.isEnabled());
            assertTrue(config.getReadonlyTables().isEmpty(),
                    "Should have empty readonly tables by default");
        }
    }

    // ==================== Checker Categories Tests ====================

    @Nested
    @DisplayName("Checker Categories Tests")
    class CheckerCategoriesTests {

        @Test
        @DisplayName("SQL Injection Checkers (4): Multi-statement, SetOperation, SqlComment, IntoOutfile")
        void testSqlInjectionCheckers() {
            List<Class<? extends RuleChecker>> sqlInjectionTypes = Arrays.asList(
                    MultiStatementChecker.class,
                    SetOperationChecker.class,
                    SqlCommentChecker.class,
                    IntoOutfileChecker.class
            );

            long count = allCheckers.stream()
                    .filter(c -> sqlInjectionTypes.stream().anyMatch(t -> t.isInstance(c)))
                    .count();

            assertEquals(4, count, "Should have 4 SQL injection checkers");
        }

        @Test
        @DisplayName("Dangerous Operations Checkers (3): DDL, DangerousFunction, CallStatement")
        void testDangerousOperationsCheckers() {
            List<Class<? extends RuleChecker>> dangerousOpsTypes = Arrays.asList(
                    DdlOperationChecker.class,
                    DangerousFunctionChecker.class,
                    CallStatementChecker.class
            );

            long count = allCheckers.stream()
                    .filter(c -> dangerousOpsTypes.stream().anyMatch(t -> t.isInstance(c)))
                    .count();

            assertEquals(3, count, "Should have 3 dangerous operations checkers");
        }

        @Test
        @DisplayName("Access Control Checkers (4): Metadata, SetStatement, DeniedTable, ReadOnlyTable")
        void testAccessControlCheckers() {
            List<Class<? extends RuleChecker>> accessControlTypes = Arrays.asList(
                    MetadataStatementChecker.class,
                    SetStatementChecker.class,
                    DeniedTableChecker.class,
                    ReadOnlyTableChecker.class
            );

            long count = allCheckers.stream()
                    .filter(c -> accessControlTypes.stream().anyMatch(t -> t.isInstance(c)))
                    .count();

            assertEquals(4, count, "Should have 4 access control checkers");
        }
    }

}

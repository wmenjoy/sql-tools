package com.footstone.audit.service.core.engine;

import com.footstone.sqlguard.audit.checker.AbstractAuditChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckerRegistryTest {

    @Mock
    private AbstractAuditChecker checker1;
    @Mock
    private AbstractAuditChecker checker2;

    @Test
    void testRegistry_autoDiscovery_shouldFindAllCheckers() {
        // Given
        when(checker1.getCheckerId()).thenReturn("c1");
        when(checker2.getCheckerId()).thenReturn("c2");
        List<AbstractAuditChecker> checkers = List.of(checker1, checker2);
        CheckerRegistry registry = new CheckerRegistry(checkers);

        // When
        List<AbstractAuditChecker> allCheckers = registry.getAllCheckers();

        // Then
        assertEquals(2, allCheckers.size());
    }
    
    @Test
    void testRegistry_getCheckerByName_shouldFind() {
        // Given
        when(checker1.getCheckerId()).thenReturn("c1");
        List<AbstractAuditChecker> checkers = List.of(checker1);
        CheckerRegistry registry = new CheckerRegistry(checkers);

        // When
        Optional<AbstractAuditChecker> result = registry.getCheckerById("c1");

        // Then
        assertTrue(result.isPresent());
        assertEquals(checker1, result.get());
    }
}

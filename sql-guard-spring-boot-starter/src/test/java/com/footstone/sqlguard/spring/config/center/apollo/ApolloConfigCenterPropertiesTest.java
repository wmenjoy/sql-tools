package com.footstone.sqlguard.spring.config.center.apollo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for ApolloConfigCenterProperties defaults.
 */
class ApolloConfigCenterPropertiesTest {

    @Test
    void testDefaultNamespaces_shouldBeMutable() {
        ApolloConfigCenterProperties properties = new ApolloConfigCenterProperties();

        List<String> namespaces = properties.getNamespaces();
        namespaces.add("sql-guard");

        assertTrue(namespaces.contains("sql-guard"));
    }
}

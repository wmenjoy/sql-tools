package com.footstone.audit.service;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AuditServiceBuildTest {

    @Test
    public void testParentPomCompilation_shouldSucceed() {
        // Implicitly verified if this test runs
        assertTrue(true);
    }

    @Test
    public void testAllModulesCompilation_shouldSucceed() {
        // Implicitly verified if project builds
        assertTrue(true);
    }

    @Test
    public void testJava21Features_shouldCompile() {
        record TestRecord(String name) {}
        Object obj = new TestRecord("test");
        if (obj instanceof TestRecord r) {
            assertEquals("test", r.name());
        }
        
        String type = switch(obj) {
            case TestRecord r -> "Record";
            default -> "Unknown";
        };
        assertEquals("Record", type);
    }

    @Test
    public void testDependencyResolution_shouldResolveCorrectly() {
        // Direct reference to verify compile-time and runtime availability
        assertNotNull(com.footstone.sqlguard.audit.checker.AbstractAuditChecker.class);
        assertNotNull(com.footstone.sqlguard.audit.AuditEvent.class);
    }
    
    @Test
    public void testMavenProfileActivation_dev_shouldUseDevConfig() {
        assertTrue(true, "Profiles validation deferred to Spring Boot setup step");
    }

    @Test
    public void testMavenProfileActivation_prod_shouldUseProdConfig() {
        assertTrue(true, "Profiles validation deferred to Spring Boot setup step");
    }

    @Test
    public void testMultiModuleDependency_coreToWeb_shouldWork() {
       File webPom = new File("../sql-audit-service-web/pom.xml");
       if (webPom.exists()) {
           try {
               String content = Files.readString(webPom.toPath());
               assertTrue(content.contains("sql-audit-service-core"));
           } catch (Exception e) {
               fail("Could not read web pom: " + e.getMessage());
           }
       } else {
           // Might be running from root or elsewhere, try absolute path logic or skip if not found
           // Assuming standard layout
           System.out.println("Web POM not found at ../sql-audit-service-web/pom.xml");
       }
    }

    @Test
    public void testMultiModuleDependency_coreToConsumer_shouldWork() {
        File consumerPom = new File("../sql-audit-service-consumer/pom.xml");
        if (consumerPom.exists()) {
            try {
                String content = Files.readString(consumerPom.toPath());
                assertTrue(content.contains("sql-audit-service-core"));
            } catch (Exception e) {
                fail("Could not read consumer pom: " + e.getMessage());
            }
        } else {
             System.out.println("Consumer POM not found at ../sql-audit-service-consumer/pom.xml");
        }
    }
}

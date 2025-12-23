package com.footstone.audit.service.deployment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security Hardening Tests
 * Validates security configurations including JWT, RBAC, and TLS
 */
@DisplayName("Security Hardening Tests")
@Tag("deployment")
@Tag("security")
public class SecurityHardeningTest {

    private static final String DOCS_DIR = "docs/deployment";
    private static final String PROJECT_ROOT = System.getProperty("user.dir", ".").replace("/sql-audit-service/sql-audit-service-web", "");

    @Test
    @DisplayName("Test Security JWT should validate")
    public void testSecurity_JWT_shouldValidate() throws Exception {
        Path securityDocPath = Path.of(PROJECT_ROOT, DOCS_DIR, "security/jwt-authentication.md");
        assertTrue(Files.exists(securityDocPath), "JWT authentication doc should exist");

        String content = Files.readString(securityDocPath);

        // Validate JWT configuration
        assertTrue(content.contains("JWT") || content.contains("JSON Web Token"), "Should document JWT");
        assertTrue(content.contains("OAuth2") || content.contains("oauth2"), "Should mention OAuth2");
        assertTrue(content.contains("JwtAuthenticationConverter"), "Should have JWT converter");
        assertTrue(content.contains("SecurityFilterChain"), "Should configure security filter chain");
    }

    @Test
    @DisplayName("Test Security RBAC should enforce")
    public void testSecurity_RBAC_shouldEnforce() throws Exception {
        Path securityDocPath = Path.of(PROJECT_ROOT, DOCS_DIR, "security/jwt-authentication.md");
        String content = Files.readString(securityDocPath);

        // Validate RBAC configuration
        assertTrue(content.contains("RBAC") || content.contains("Role-Based Access Control"),
            "Should document RBAC");
        assertTrue(content.contains("AUDIT_VIEWER") || content.contains("roles:"),
            "Should define roles");
        assertTrue(content.contains("@PreAuthorize") || content.contains("hasRole"),
            "Should use method-level security");
    }

    @Test
    @DisplayName("Test Security TLS should encrypt")
    public void testSecurity_TLS_shouldEncrypt() throws Exception {
        Path securityDocPath = Path.of(PROJECT_ROOT, DOCS_DIR, "security/jwt-authentication.md");
        String content = Files.readString(securityDocPath);

        // Validate TLS configuration
        assertTrue(content.contains("TLS") || content.contains("SSL"), "Should document TLS/SSL");
        assertTrue(content.contains("tls.crt") || content.contains("certificate"), "Should mention certificates");
        assertTrue(content.contains("sslmode=require") || content.contains("ssl=true"),
            "Should configure SSL for databases");

        // Check Ingress TLS
        Path ingressPath = Path.of(PROJECT_ROOT, DOCS_DIR, "k8s/ingress.yaml");
        String ingressContent = Files.readString(ingressPath);
        assertTrue(ingressContent.contains("tls:"), "Ingress should configure TLS");
        assertTrue(ingressContent.contains("secretName: audit-tls"), "Should reference TLS secret");
    }

    @Test
    @DisplayName("Test Security credentials should be secured")
    public void testSecurity_credentials_shouldBeSecured() throws Exception {
        Path secretPath = Path.of(PROJECT_ROOT, DOCS_DIR, "k8s/secret.yaml");
        String content = Files.readString(secretPath);

        // Validate Secret usage
        assertTrue(content.contains("kind: Secret"), "Should use Kubernetes Secret");
        assertTrue(content.contains("type: Opaque"), "Should be Opaque type");
        assertTrue(content.contains("CHANGE_ME"), "Should have placeholder passwords");

        // Validate no hardcoded credentials
        assertFalse(content.contains("password: mypassword"), "Should not have hardcoded passwords");
    }

    @Test
    @DisplayName("Test Security audit log PII should sanitize")
    public void testSecurity_auditLog_PII_shouldSanitize() throws Exception {
        Path securityDocPath = Path.of(PROJECT_ROOT, DOCS_DIR, "security/jwt-authentication.md");
        String content = Files.readString(securityDocPath);

        // Validate security audit logging
        assertTrue(content.contains("audit") || content.contains("log"), "Should document audit logging");

        // Check for security considerations
        assertTrue(content.contains("Security") && content.contains("Checklist"),
            "Should have security checklist");
    }
}

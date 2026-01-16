# JWT Authentication and Security Configuration

## Overview

This guide describes how to configure JWT (JSON Web Token) authentication and implement comprehensive security measures for the SQL Audit Service.

## Table of Contents

1. [JWT Authentication](#jwt-authentication)
2. [RBAC (Role-Based Access Control)](#rbac)
3. [TLS/SSL Encryption](#tlsssl-encryption)
4. [API Security](#api-security)
5. [Secrets Management](#secrets-management)
6. [Network Security](#network-security)
7. [Audit Logging](#audit-logging)

---

## 1. JWT Authentication

### 1.1 Architecture

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│   Client     │         │ OAuth2/OIDC  │         │ SQL Audit    │
│ Application  │────────>│   Provider   │<────────│   Service    │
└──────────────┘         │ (Keycloak,   │         │              │
                         │  Okta, etc.) │         └──────────────┘
                         └──────────────┘
                                │
                                │ JWT Token
                                │ (RS256/HS256)
                                ▼
                         ┌──────────────┐
                         │ JWT Payload: │
                         │ - sub: user  │
                         │ - roles: []  │
                         │ - exp: time  │
                         └──────────────┘
```

### 1.2 Spring Security Configuration

```java
package com.footstone.audit.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless API
            .csrf(csrf -> csrf.disable())

            // Stateless session management
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // Protected endpoints - require authentication
                .requestMatchers("/api/audit/**").authenticated()
                .requestMatchers("/actuator/**").hasRole("ADMIN")

                // Default: deny all
                .anyRequest().authenticated()
            )

            // JWT OAuth2 Resource Server
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * Convert JWT claims to Spring Security authorities
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

        // Extract roles from "roles" claim
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

        return jwtAuthenticationConverter;
    }
}
```

### 1.3 Application Configuration

```yaml
# application-prod.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # Option 1: Use JWK Set URI (recommended for production)
          jwk-set-uri: https://auth.example.com/.well-known/jwks.json

          # Option 2: Use issuer URI (auto-discovers JWK Set)
          issuer-uri: https://auth.example.com/realms/audit

          # Option 3: Use public key (for symmetric signing)
          # public-key-location: classpath:public-key.pem

# JWT validation settings
jwt:
  validation:
    # Validate issuer
    issuer: https://auth.example.com/realms/audit

    # Validate audience
    audience: sql-audit-service

    # Clock skew tolerance (seconds)
    clock-skew: 60
```

### 1.4 JWT Token Structure

**Header:**
```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "rsa-key-1"
}
```

**Payload:**
```json
{
  "sub": "user@example.com",
  "iss": "https://auth.example.com/realms/audit",
  "aud": "sql-audit-service",
  "exp": 1735689600,
  "iat": 1735686000,
  "roles": ["AUDIT_VIEWER", "AUDIT_ADMIN"],
  "email": "user@example.com",
  "name": "John Doe"
}
```

**Signature:**
```
RSASHA256(
  base64UrlEncode(header) + "." +
  base64UrlEncode(payload),
  private-key
)
```

### 1.5 Obtaining JWT Token

#### Using Client Credentials Flow (Service-to-Service)

```bash
curl -X POST https://auth.example.com/realms/audit/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=audit-service-client" \
  -d "client_secret=CHANGE_ME_CLIENT_SECRET"
```

**Response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in": 3600,
  "token_type": "Bearer"
}
```

#### Using Password Flow (User Authentication)

```bash
curl -X POST https://auth.example.com/realms/audit/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=audit-web-app" \
  -d "username=user@example.com" \
  -d "password=user_password"
```

### 1.6 Using JWT Token in API Requests

```bash
# Store token
export JWT_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."

# Make authenticated request
curl -H "Authorization: Bearer $JWT_TOKEN" \
  https://audit.example.com/api/audit/reports?startTime=2025-01-01T00:00:00
```

---

## 2. RBAC (Role-Based Access Control)

### 2.1 Role Definitions

```yaml
# Role hierarchy
roles:
  # Read-only access to audit logs
  - name: AUDIT_VIEWER
    permissions:
      - read:audit-logs
      - read:statistics
      - read:checker-config

  # Can view and modify checker configuration
  - name: AUDIT_OPERATOR
    permissions:
      - read:audit-logs
      - read:statistics
      - read:checker-config
      - write:checker-config
      - write:whitelist-rules

  # Full administrative access
  - name: AUDIT_ADMIN
    permissions:
      - read:audit-logs
      - read:statistics
      - read:checker-config
      - write:checker-config
      - write:whitelist-rules
      - delete:audit-logs
      - manage:users
      - view:actuator

  # System service account
  - name: AUDIT_SERVICE
    permissions:
      - write:audit-events
      - read:checker-config
```

### 2.2 Method-Level Security

```java
package com.footstone.audit.service.web.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
public class AuditReportController {

    /**
     * View audit reports - requires AUDIT_VIEWER role
     */
    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('AUDIT_VIEWER', 'AUDIT_OPERATOR', 'AUDIT_ADMIN')")
    public List<AuditReportDto> getAuditReports(
        @RequestParam LocalDateTime startTime,
        @RequestParam LocalDateTime endTime
    ) {
        // Implementation
    }

    /**
     * Update checker configuration - requires AUDIT_OPERATOR role
     */
    @PutMapping("/checkers/{checkerName}")
    @PreAuthorize("hasAnyRole('AUDIT_OPERATOR', 'AUDIT_ADMIN')")
    public void updateCheckerConfig(
        @PathVariable String checkerName,
        @RequestBody CheckerConfigUpdateDto config
    ) {
        // Implementation
    }

    /**
     * Delete audit logs - requires AUDIT_ADMIN role
     */
    @DeleteMapping("/logs")
    @PreAuthorize("hasRole('AUDIT_ADMIN')")
    public void deleteAuditLogs(
        @RequestParam LocalDateTime before
    ) {
        // Implementation
    }
}
```

### 2.3 Fine-Grained Access Control

```java
/**
 * Custom expression handler for complex authorization rules
 */
@Component("auditSecurity")
public class AuditSecurityExpressionHandler {

    /**
     * Check if user can access specific application's audit logs
     */
    public boolean canAccessApplication(Authentication authentication, String applicationName) {
        // Extract user's allowed applications from JWT claims
        Map<String, Object> claims = ((JwtAuthenticationToken) authentication).getTokenAttributes();
        List<String> allowedApps = (List<String>) claims.get("allowed_applications");

        return allowedApps != null && allowedApps.contains(applicationName);
    }

    /**
     * Check if user can modify sensitive configuration
     */
    public boolean canModifySensitiveConfig(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_AUDIT_ADMIN"));
    }
}
```

**Usage:**
```java
@GetMapping("/reports/{applicationName}")
@PreAuthorize("@auditSecurity.canAccessApplication(authentication, #applicationName)")
public List<AuditReportDto> getApplicationReports(
    @PathVariable String applicationName
) {
    // Implementation
}
```

---

## 3. TLS/SSL Encryption

### 3.1 API Endpoints (HTTPS)

**Ingress TLS Configuration** (see `docs/deployment/k8s/ingress.yaml`):

```yaml
spec:
  tls:
  - hosts:
    - audit.example.com
    secretName: audit-tls
```

**Generate Self-Signed Certificate (Development Only):**

```bash
# Generate private key
openssl genrsa -out tls.key 2048

# Generate certificate signing request
openssl req -new -key tls.key -out tls.csr -subj "/CN=audit.example.com"

# Generate self-signed certificate
openssl x509 -req -days 365 -in tls.csr -signkey tls.key -out tls.crt

# Create Kubernetes secret
kubectl create secret tls audit-tls \
  --cert=tls.crt \
  --key=tls.key \
  -n sqlguard
```

**Production: Use cert-manager with Let's Encrypt:**

```yaml
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: audit-tls
  namespace: sqlguard
spec:
  secretName: audit-tls
  issuerRef:
    name: letsencrypt-prod
    kind: ClusterIssuer
  dnsNames:
  - audit.example.com
```

### 3.2 Kafka TLS

```yaml
# application-prod.yml
spring:
  kafka:
    bootstrap-servers: kafka:9093
    properties:
      security.protocol: SSL
      ssl.truststore.location: /etc/kafka/certs/truststore.jks
      ssl.truststore.password: ${KAFKA_TRUSTSTORE_PASSWORD}
      ssl.keystore.location: /etc/kafka/certs/keystore.jks
      ssl.keystore.password: ${KAFKA_KEYSTORE_PASSWORD}
      ssl.key.password: ${KAFKA_KEY_PASSWORD}
```

### 3.3 PostgreSQL TLS

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/audit?sslmode=require&ssl=true&sslrootcert=/etc/certs/postgres-ca.crt
```

**PostgreSQL Server Configuration:**

```ini
# postgresql.conf
ssl = on
ssl_cert_file = '/var/lib/postgresql/server.crt'
ssl_key_file = '/var/lib/postgresql/server.key'
ssl_ca_file = '/var/lib/postgresql/ca.crt'
```

### 3.4 ClickHouse TLS

```yaml
spring:
  clickhouse:
    url: jdbc:clickhouse://clickhouse:8443/audit?ssl=true&sslmode=strict
```

**ClickHouse Server Configuration:**

```xml
<clickhouse>
    <openSSL>
        <server>
            <certificateFile>/etc/clickhouse-server/server.crt</certificateFile>
            <privateKeyFile>/etc/clickhouse-server/server.key</privateKeyFile>
            <caConfig>/etc/clickhouse-server/ca.crt</caConfig>
            <verificationMode>relaxed</verificationMode>
            <loadDefaultCAFile>true</loadDefaultCAFile>
        </server>
    </openSSL>
</clickhouse>
```

---

## 4. API Security

### 4.1 Rate Limiting

```yaml
# Ingress rate limiting (already configured in ingress.yaml)
nginx.ingress.kubernetes.io/limit-rps: "100"
nginx.ingress.kubernetes.io/limit-connections: "50"
```

**Application-level rate limiting:**

```java
@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimiter rateLimiter() {
        return RateLimiter.create(100); // 100 requests per second
    }
}

@RestController
public class AuditReportController {

    @Autowired
    private RateLimiter rateLimiter;

    @GetMapping("/api/audit/reports")
    public ResponseEntity<?> getReports() {
        if (!rateLimiter.tryAcquire()) {
            return ResponseEntity.status(429).body("Rate limit exceeded");
        }
        // Process request
    }
}
```

### 4.2 Input Validation

```java
@RestController
@Validated
public class AuditReportController {

    @GetMapping("/api/audit/reports")
    public List<AuditReportDto> getReports(
        @RequestParam @NotNull @PastOrPresent LocalDateTime startTime,
        @RequestParam @NotNull @FutureOrPresent LocalDateTime endTime,
        @RequestParam(required = false) @Pattern(regexp = "^[a-zA-Z0-9-_]+$") String applicationName,
        @RequestParam(defaultValue = "10") @Min(1) @Max(1000) int limit
    ) {
        // Implementation
    }
}
```

### 4.3 SQL Injection Prevention

**Already handled by ClickHouse JDBC driver**:

```java
// Safe - uses parameterized query
String sql = "SELECT * FROM audit.audit_logs WHERE application_name = ? AND timestamp >= ?";
jdbcTemplate.query(sql, new Object[]{applicationName, startTime}, rowMapper);
```

### 4.4 CORS Configuration

```java
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("https://dashboard.example.com")
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
            }
        };
    }
}
```

---

## 5. Secrets Management

### 5.1 Kubernetes Secrets

```bash
# Create secrets from literal values
kubectl create secret generic audit-secrets \
  --from-literal=postgres.password=CHANGE_ME \
  --from-literal=clickhouse.password=CHANGE_ME \
  --from-literal=jwt.secret=CHANGE_ME \
  -n sqlguard

# Create secrets from files
kubectl create secret generic audit-certs \
  --from-file=tls.crt=./certs/server.crt \
  --from-file=tls.key=./certs/server.key \
  -n sqlguard
```

### 5.2 External Secrets Operator (Recommended)

**Install External Secrets Operator:**

```bash
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets -n external-secrets-system --create-namespace
```

**AWS Secrets Manager Integration:**

```yaml
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: aws-secretsmanager
  namespace: sqlguard
spec:
  provider:
    aws:
      service: SecretsManager
      region: us-west-2
      auth:
        jwt:
          serviceAccountRef:
            name: external-secrets-sa

---
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: audit-secrets
  namespace: sqlguard
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secretsmanager
    kind: SecretStore
  target:
    name: audit-secrets
    creationPolicy: Owner
  data:
  - secretKey: postgres.password
    remoteRef:
      key: /sqlguard/audit/postgres
      property: password
  - secretKey: clickhouse.password
    remoteRef:
      key: /sqlguard/audit/clickhouse
      property: password
```

### 5.3 HashiCorp Vault Integration

```yaml
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: vault-backend
  namespace: sqlguard
spec:
  provider:
    vault:
      server: "https://vault.example.com"
      path: "secret"
      version: "v2"
      auth:
        kubernetes:
          mountPath: "kubernetes"
          role: "audit-service"
          serviceAccountRef:
            name: sql-audit-service
```

---

## 6. Network Security

### 6.1 Network Policies

See `docs/deployment/k8s/ingress.yaml` for Network Policy configuration.

**Key Points:**
- Restrict ingress to Ingress Controller and Prometheus
- Restrict egress to Kafka, PostgreSQL, ClickHouse, and DNS
- Deny all other traffic

### 6.2 Service Mesh (Istio)

```yaml
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: sql-audit-service
  namespace: sqlguard
spec:
  hosts:
  - audit.example.com
  gateways:
  - audit-gateway
  http:
  - match:
    - uri:
        prefix: "/api/audit"
    route:
    - destination:
        host: sql-audit-service
        port:
          number: 8090
    retries:
      attempts: 3
      perTryTimeout: 2s
    timeout: 10s

---
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: sql-audit-service
  namespace: sqlguard
spec:
  selector:
    matchLabels:
      app: sql-audit-service
  mtls:
    mode: STRICT
```

---

## 7. Audit Logging

### 7.1 Security Audit Events

```java
@Aspect
@Component
public class SecurityAuditAspect {

    private static final Logger logger = LoggerFactory.getLogger("SECURITY_AUDIT");

    @AfterReturning("@annotation(org.springframework.security.access.prepost.PreAuthorize)")
    public void logAuthorizedAccess(JoinPoint joinPoint) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        logger.info("Authorized access: user={}, method={}",
            auth.getName(), joinPoint.getSignature());
    }

    @AfterThrowing(pointcut = "@annotation(org.springframework.security.access.prepost.PreAuthorize)",
                   throwing = "ex")
    public void logUnauthorizedAccess(JoinPoint joinPoint, Exception ex) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        logger.warn("Unauthorized access attempt: user={}, method={}, error={}",
            auth != null ? auth.getName() : "anonymous",
            joinPoint.getSignature(),
            ex.getMessage());
    }
}
```

### 7.2 Security Event Types

```
SECURITY_AUDIT_EVENT_TYPES:
  - LOGIN_SUCCESS
  - LOGIN_FAILURE
  - LOGOUT
  - ACCESS_GRANTED
  - ACCESS_DENIED
  - CONFIGURATION_CHANGE
  - DATA_DELETION
  - UNAUTHORIZED_API_ACCESS
```

---

## 8. Security Checklist

### Pre-Deployment

- [ ] JWT authentication configured
- [ ] RBAC roles defined
- [ ] TLS enabled for all services
- [ ] Secrets stored in Kubernetes Secrets or external vault
- [ ] Network policies applied
- [ ] Rate limiting configured
- [ ] Input validation implemented
- [ ] CORS configured

### Post-Deployment

- [ ] Verify HTTPS works (no certificate errors)
- [ ] Test JWT authentication (valid and invalid tokens)
- [ ] Test RBAC (different roles access different endpoints)
- [ ] Verify TLS connections to Kafka, PostgreSQL, ClickHouse
- [ ] Check security audit logs
- [ ] Run security scan (e.g., Trivy, Snyk)

---

## 9. Security Testing

### Test JWT Authentication

```bash
# Test without token (should fail)
curl https://audit.example.com/api/audit/reports

# Expected: 401 Unauthorized

# Test with invalid token (should fail)
curl -H "Authorization: Bearer invalid-token" \
  https://audit.example.com/api/audit/reports

# Expected: 401 Unauthorized

# Test with valid token (should succeed)
curl -H "Authorization: Bearer $JWT_TOKEN" \
  https://audit.example.com/api/audit/reports

# Expected: 200 OK
```

### Test RBAC

```bash
# AUDIT_VIEWER trying to delete logs (should fail)
curl -X DELETE -H "Authorization: Bearer $VIEWER_TOKEN" \
  https://audit.example.com/api/audit/logs?before=2025-01-01

# Expected: 403 Forbidden

# AUDIT_ADMIN deleting logs (should succeed)
curl -X DELETE -H "Authorization: Bearer $ADMIN_TOKEN" \
  https://audit.example.com/api/audit/logs?before=2025-01-01

# Expected: 204 No Content
```

---

## References

- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [JWT.io](https://jwt.io/)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [cert-manager Documentation](https://cert-manager.io/docs/)

---

**Document Version**: 1.0
**Last Updated**: 2025-01-01
**Maintainer**: Security Team

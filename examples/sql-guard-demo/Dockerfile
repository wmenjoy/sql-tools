# Multi-stage build for SQL Guard Demo Application

# Stage 1: Build
FROM maven:3.8.8-eclipse-temurin-11 AS builder

WORKDIR /build

# Copy parent POM and all module POMs
COPY pom.xml .
COPY sql-guard-core/pom.xml sql-guard-core/
COPY sql-guard-mybatis/pom.xml sql-guard-mybatis/
COPY sql-guard-mp/pom.xml sql-guard-mp/
COPY sql-guard-jdbc/pom.xml sql-guard-jdbc/
COPY sql-guard-spring-boot-starter/pom.xml sql-guard-spring-boot-starter/
COPY sql-scanner-core/pom.xml sql-scanner-core/
COPY sql-scanner-cli/pom.xml sql-scanner-cli/
COPY sql-scanner-maven/pom.xml sql-scanner-maven/
COPY sql-scanner-gradle/pom.xml sql-scanner-gradle/
COPY examples/sql-guard-demo/pom.xml examples/sql-guard-demo/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B -pl examples/sql-guard-demo -am

# Copy source code
COPY sql-guard-core/src sql-guard-core/src
COPY sql-guard-mybatis/src sql-guard-mybatis/src
COPY sql-guard-mp/src sql-guard-mp/src
COPY sql-guard-jdbc/src sql-guard-jdbc/src
COPY sql-guard-spring-boot-starter/src sql-guard-spring-boot-starter/src
COPY examples/sql-guard-demo/src examples/sql-guard-demo/src

# Build application (skip tests for faster builds)
RUN mvn clean package -pl examples/sql-guard-demo -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:11-jre-alpine

WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /build/examples/sql-guard-demo/target/sql-guard-demo-*.jar app.jar

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]















package com.footstone.sqlguard.demo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SQL Guard Demo Application.
 *
 * <p>Interactive demonstration of SQL Safety Guard System with real-world MyBatis/MyBatis-Plus
 * usage patterns. This application showcases all 10 validation rules with REST endpoints that
 * trigger violations on demand.</p>
 *
 * <p><strong>Features Demonstrated:</strong></p>
 * <ul>
 *   <li>Zero-configuration integration via sql-guard-spring-boot-starter</li>
 *   <li>All 10 validation rules (NoWhereClause, DummyCondition, etc.)</li>
 *   <li>Three violation strategies: BLOCK, WARN, LOG</li>
 *   <li>MyBatis XML mappers, annotation mappers, and MyBatis-Plus services</li>
 *   <li>Runtime strategy switching via REST endpoint</li>
 *   <li>Violation dashboard with recent violations</li>
 * </ul>
 *
 * <p><strong>Quick Start:</strong></p>
 * <pre>{@code
 * # Start with Docker Compose
 * docker-compose up
 *
 * # Or run locally
 * mvn spring-boot:run
 *
 * # Access demo endpoints
 * curl http://localhost:8080/violations/no-where-clause
 * }</pre>
 *
 * @see com.footstone.sqlguard.demo.controller.DemoController
 */
@SpringBootApplication
@MapperScan("com.footstone.sqlguard.demo.mapper")
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}




package com.footstone.audit.service;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class DockerComposeIT {

    @Container
    public static DockerComposeContainer<?> environment =
        new DockerComposeContainer<>(new File("docker-compose.yml"))
            .withExposedService("audit-service", 8080, Wait.forHttp("/actuator/health").forStatusCode(200))
            .withExposedService("prometheus", 9090, Wait.forHttp("/-/healthy").forStatusCode(200));

    @Test
    public void testServicesUp() {
        // If the container starts and wait strategies pass, this test passes.
        // DockerComposeContainer will throw an exception during startup if services fail to start
        // or wait strategies time out.
        assertTrue(true, "Docker Compose environment started successfully");
    }
}

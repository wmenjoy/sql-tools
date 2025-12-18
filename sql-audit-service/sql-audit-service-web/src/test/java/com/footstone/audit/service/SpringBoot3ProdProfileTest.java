package com.footstone.audit.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("prod")
public class SpringBoot3ProdProfileTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Value("${environment.name:unknown}")
    private String environmentName;

    @Test
    public void testApplicationContext_shouldLoad() {
        assertNotNull(applicationContext);
    }

    @Test
    public void testApplicationYaml_prodProfile_shouldLoad() {
        assertEquals("prod", environmentName);
    }
}

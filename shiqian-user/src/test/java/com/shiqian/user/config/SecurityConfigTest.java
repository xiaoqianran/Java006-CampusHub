package com.shiqian.user.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security 配置测试
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OpenAPI openAPI;

    @Test
    void shouldLoadSecurityConfig() {
        assertNotNull(context.getBean(SecurityConfig.class));
    }

    @Test
    void shouldEnableMethodSecurity() {
        assertTrue(SecurityConfig.class.isAnnotationPresent(EnableMethodSecurity.class));
    }

    @Test
    void shouldDocumentJwtBearerAuthentication() {
        assertNotNull(openAPI.getComponents());
        assertNotNull(openAPI.getComponents().getSecuritySchemes().get("bearerAuth"));
    }
}

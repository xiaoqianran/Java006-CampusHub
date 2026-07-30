package com.shiqian.resource.config;

import com.shiqian.resource.BaseResourceTest;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security 配置测试
 */
class SecurityConfigTest extends BaseResourceTest {

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
        assertTrue(context.containsBean("org.springframework.security.config.annotation.method.configuration.PrePostMethodSecurityConfiguration"));
    }

    @Test
    void shouldDocumentJwtBearerAuthentication() {
        assertNotNull(openAPI.getComponents());
        assertNotNull(openAPI.getComponents().getSecuritySchemes().get("bearerAuth"));
    }
}

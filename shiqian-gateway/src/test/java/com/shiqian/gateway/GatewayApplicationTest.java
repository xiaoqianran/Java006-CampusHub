package com.shiqian.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class GatewayApplicationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void shouldLoadGatewayContext() {
        assertNotNull(routeLocator);
    }

    @Test
    void shouldLoadUserAndResourceRoutes() {
        List<String> routeIds = routeLocator.getRoutes()
                .map(Route::getId)
                .collectList()
                .block();

        assertNotNull(routeIds);
        assertEquals(2, routeIds.size());
        assertTrue(routeIds.contains("shiqian-user"));
        assertTrue(routeIds.contains("shiqian-resource"));
    }
}

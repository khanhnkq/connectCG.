package org.example.connectcg_be.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigurationTest {

    @Test
    @DisplayName("OpenApiConfig should produce OpenAPI bean with valid info and security schemes")
    void testCustomOpenAPIBean() {
        OpenApiConfig config = new OpenApiConfig();
        ReflectionTestUtils.setField(config, "serverPort", "8080");

        OpenAPI openAPI = config.customOpenAPI();

        assertNotNull(openAPI, "OpenAPI bean must not be null");
        assertNotNull(openAPI.getInfo(), "OpenAPI info must not be null");
        assertEquals("Connect Social Network API", openAPI.getInfo().getTitle());
        assertEquals("1.0.0", openAPI.getInfo().getVersion());

        // Verify security schemes
        assertNotNull(openAPI.getComponents(), "Components must not be null");
        assertNotNull(openAPI.getComponents().getSecuritySchemes(), "SecuritySchemes must not be null");

        SecurityScheme bearerScheme = openAPI.getComponents().getSecuritySchemes().get("bearerAuth");
        assertNotNull(bearerScheme, "bearerAuth scheme must be registered");
        assertEquals(SecurityScheme.Type.HTTP, bearerScheme.getType());
        assertEquals("bearer", bearerScheme.getScheme());
        assertEquals("JWT", bearerScheme.getBearerFormat());

        SecurityScheme cookieScheme = openAPI.getComponents().getSecuritySchemes().get("cookieAuth");
        assertNotNull(cookieScheme, "cookieAuth scheme must be registered");
        assertEquals(SecurityScheme.Type.APIKEY, cookieScheme.getType());
        assertEquals(SecurityScheme.In.COOKIE, cookieScheme.getIn());
        assertEquals("connect_access", cookieScheme.getName());

        // Verify security requirements
        assertNotNull(openAPI.getSecurity(), "Security requirements must not be null");
        boolean hasBearerReq = openAPI.getSecurity().stream()
                .anyMatch(req -> req.containsKey("bearerAuth"));
        boolean hasCookieReq = openAPI.getSecurity().stream()
                .anyMatch(req -> req.containsKey("cookieAuth"));

        assertTrue(hasBearerReq, "OpenAPI should declare bearerAuth security requirement");
        assertTrue(hasCookieReq, "OpenAPI should declare cookieAuth security requirement");
    }
}

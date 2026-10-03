package org.example.connectcg_be.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        final String bearerAuthSchemeName = "bearerAuth";
        final String cookieAuthSchemeName = "cookieAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Connect Social Network API")
                        .description("Tài liệu API RESTful của mạng xã hội Connect - hỗ trợ tự động sinh Client SDK cho Frontend")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Connect Development Team")
                                .email("dev@connect.com"))
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")))
                .servers(List.of(
                        new Server().url("/").description("Current Server (Relative)"),
                        new Server().url("http://localhost:" + serverPort).description("Local Development Server")
                ))
                .addSecurityItem(new SecurityRequirement()
                        .addList(bearerAuthSchemeName)
                        .addList(cookieAuthSchemeName))
                .components(new Components()
                        .addSecuritySchemes(bearerAuthSchemeName,
                                new SecurityScheme()
                                        .name(bearerAuthSchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Nhập Bearer Token JWT để xác thực"))
                        .addSecuritySchemes(cookieAuthSchemeName,
                                new SecurityScheme()
                                        .name("connect_access")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .description("Cookie xác thực HttpOnly connect_access")));
    }
}

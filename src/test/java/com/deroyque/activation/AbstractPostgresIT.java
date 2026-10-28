package com.deroyque.activation;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base para testes de integração que exigem um PostgreSQL real (ex.: {@code FOR UPDATE SKIP
 * LOCKED} do worker e o teste de concorrência de duplicidade, que o H2 não reproduz fielmente).
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractPostgresIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Segredo HMAC para a emissão de tokens nos testes.
        registry.add("security.dev.jwt.hmac-secret", () -> "it-only-secret-change-me-please-32bytes!!");
    }
}

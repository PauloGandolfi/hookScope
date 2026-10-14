package com.hookscope.api;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class DatabaseIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    DataSource dataSource;

    @Test
    void flywayCreatesSchemaAndAppliesFirstMigration() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("select exists (select 1 from information_schema.schemata where schema_name = 'hookscope')", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from hookscope.flyway_schema_history where version = '1' and success", Integer.class)).isEqualTo(1);
    }
}

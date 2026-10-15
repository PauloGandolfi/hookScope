package com.hookscope.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
    void flywayCreatesSchemaAndAppliesMigrations() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("select exists (select 1 from information_schema.schemata where schema_name = 'hookscope')", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from hookscope.flyway_schema_history where version in ('1', '2', '3') and success", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = 'hookscope' and table_name in ('app_user', 'project', 'api_key', 'event')", Integer.class)).isEqualTo(4);
    }

    @Test
    void eventMigrationPersistsJsonAndEnforcesContractConstraints() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        jdbc.update("""
            insert into hookscope.app_user (id, name, email, password_hash, created_at, updated_at)
            values (?, 'Migration Test', ?, 'not-a-real-password-hash', current_timestamp, current_timestamp)
            """, userId, userId + "@example.test");
        jdbc.update("""
            insert into hookscope.project (id, owner_id, name, created_at, updated_at)
            values (?, ?, 'Event Migration', current_timestamp, current_timestamp)
            """, projectId, userId);
        jdbc.update("""
            insert into hookscope.event
                (project_id, event_id, event_type, status, duration_ms, occurred_at, metadata)
            values (?, ?, ?, ?, ?, ?, ?::jsonb)
            """, projectId, eventId, "payment.authorization", "SUCCESS", 184,
            OffsetDateTime.parse("2026-10-15T14:32:41.583Z"),
            "{\"provider\":\"example-pay\",\"retry\":false}");

        assertThat(jdbc.queryForObject("""
            select metadata ->> 'provider'
            from hookscope.event
            where project_id = ? and event_id = ?
            """, String.class, projectId, eventId)).isEqualTo("example-pay");
        assertThat(jdbc.queryForObject("""
            select ingested_at is not null
            from hookscope.event
            where project_id = ? and event_id = ?
            """, Boolean.class, projectId, eventId)).isTrue();

        assertThatThrownBy(() -> jdbc.update("""
            insert into hookscope.event
                (project_id, event_id, event_type, status, duration_ms, occurred_at)
            values (?, ?, ?, ?, ?, current_timestamp)
            """, projectId, UUID.randomUUID(), "INVALID TYPE", "UNKNOWN", -1))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("""
            select count(*)
            from pg_indexes
            where schemaname = 'hookscope'
              and tablename = 'event'
              and indexname in (
                  'event_project_occurred_idx',
                  'event_project_status_occurred_idx',
                  'event_project_type_occurred_idx'
              )
            """, Integer.class)).isEqualTo(3);
    }
}

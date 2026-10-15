package com.hookscope.api.adapters.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventStore {
    private final JdbcTemplate database;
    private final ObjectMapper json;

    public EventStore(JdbcTemplate database, ObjectMapper json) {
        this.database = database;
        this.json = json;
    }

    public boolean insert(StoredEvent event) {
        int inserted = database.update("""
            insert into hookscope.event
                (project_id, event_id, event_type, status, duration_ms, occurred_at, ingested_at, metadata)
            values (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            on conflict (project_id, event_id) do nothing
            """,
            event.projectId(), event.eventId(), event.type(), event.status(), event.durationMs(),
            OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC),
            OffsetDateTime.ofInstant(event.ingestedAt(), ZoneOffset.UTC),
            write(event.metadata()));
        return inserted == 1;
    }

    public Optional<StoredEvent> find(UUID projectId, UUID eventId) {
        return database.query("""
            select project_id, event_id, event_type, status, duration_ms, occurred_at, ingested_at, metadata
            from hookscope.event
            where project_id = ? and event_id = ?
            """, (result, row) -> new StoredEvent(
                result.getObject("project_id", UUID.class),
                result.getObject("event_id", UUID.class),
                result.getString("event_type"),
                result.getString("status"),
                result.getInt("duration_ms"),
                result.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                result.getObject("ingested_at", OffsetDateTime.class).toInstant(),
                read(result.getString("metadata"))),
            projectId, eventId).stream().findFirst();
    }

    private String write(JsonNode metadata) {
        try {
            return json.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid event metadata.", exception);
        }
    }

    private JsonNode read(String metadata) throws SQLException {
        try {
            return json.readTree(metadata.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Stored event metadata is invalid.", exception);
        }
    }

    public record StoredEvent(
        UUID projectId,
        UUID eventId,
        String type,
        String status,
        int durationMs,
        Instant occurredAt,
        Instant ingestedAt,
        JsonNode metadata
    ) {}
}

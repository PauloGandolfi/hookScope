package com.hookscope.api.adapters.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
            timestamp(event.occurredAt()), timestamp(event.ingestedAt()), write(event.metadata()));
        return inserted == 1;
    }

    public Optional<StoredEvent> find(UUID projectId, UUID eventId) {
        return database.query("""
            select project_id, event_id, event_type, status, duration_ms, occurred_at, ingested_at, metadata
            from hookscope.event
            where project_id = ? and event_id = ?
            """, this::map, projectId, eventId).stream().findFirst();
    }

    public EventPage search(EventFilter filter, int page, int size) {
        StringBuilder where = new StringBuilder(" where project_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(filter.projectId());

        if (filter.type() != null) {
            where.append(" and event_type = ?");
            parameters.add(filter.type());
        }
        if (filter.status() != null) {
            where.append(" and status = ?");
            parameters.add(filter.status());
        }
        if (filter.from() != null) {
            where.append(" and occurred_at >= ?");
            parameters.add(timestamp(filter.from()));
        }
        if (filter.to() != null) {
            where.append(" and occurred_at < ?");
            parameters.add(timestamp(filter.to()));
        }

        Long total = database.queryForObject(
            "select count(*) from hookscope.event" + where,
            Long.class,
            parameters.toArray());

        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        List<StoredEvent> content = database.query("""
            select project_id, event_id, event_type, status, duration_ms, occurred_at, ingested_at, metadata
            from hookscope.event
            """ + where + " order by occurred_at desc, event_id desc limit ? offset ?",
            this::map,
            pageParameters.toArray());

        return new EventPage(content, total == null ? 0 : total);
    }

    private StoredEvent map(ResultSet result, int row) throws SQLException {
        return new StoredEvent(
            result.getObject("project_id", UUID.class),
            result.getObject("event_id", UUID.class),
            result.getString("event_type"),
            result.getString("status"),
            result.getInt("duration_ms"),
            result.getObject("occurred_at", OffsetDateTime.class).toInstant(),
            result.getObject("ingested_at", OffsetDateTime.class).toInstant(),
            read(result.getString("metadata")));
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
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
            return json.readTree(metadata);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Stored event metadata is invalid.", exception);
        }
    }

    public record EventFilter(UUID projectId, String type, String status, Instant from, Instant to) {}
    public record EventPage(List<StoredEvent> content, long totalElements) {}

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

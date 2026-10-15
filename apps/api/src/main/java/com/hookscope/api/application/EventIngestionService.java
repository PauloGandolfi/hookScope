package com.hookscope.api.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookscope.api.adapters.out.persistence.EventStore;
import com.hookscope.api.adapters.out.persistence.EventStore.StoredEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventIngestionService {
    private static final int MAX_METADATA_BYTES = 16 * 1024;
    private static final int MAX_METADATA_KEYS = 50;
    private static final Set<String> SENSITIVE_KEYS = Set.of("password", "authorization", "apikey", "token", "secret");

    private final ApiKeyValidationService apiKeys;
    private final EventStore events;
    private final ObjectMapper json;

    public EventIngestionService(ApiKeyValidationService apiKeys, EventStore events, ObjectMapper json) {
        this.apiKeys = apiKeys;
        this.events = events;
        this.json = json;
    }

    @Transactional
    public IngestionResult ingest(String rawKey, EventCommand command) {
        UUID projectId = apiKeys.validate(rawKey)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid_api_key", "API Key inválida."));

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant occurredAt = command.occurredAt().truncatedTo(ChronoUnit.MICROS);
        if (occurredAt.isAfter(now.plus(5, ChronoUnit.MINUTES))) {
            throw invalidEvent("occurredAt não pode estar mais de 5 minutos no futuro.");
        }

        JsonNode metadata = normalizeMetadata(command.metadata());
        StoredEvent candidate = new StoredEvent(
            projectId,
            command.eventId(),
            command.type(),
            command.status().name(),
            command.durationMs(),
            occurredAt,
            now,
            metadata);

        if (events.insert(candidate)) {
            return new IngestionResult(candidate.eventId(), projectId, candidate.ingestedAt(), false);
        }

        StoredEvent existing = events.find(projectId, command.eventId())
            .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Evento não pôde ser recuperado."));

        if (!equivalent(existing, candidate)) {
            throw new ApiException(HttpStatus.CONFLICT, "event_conflict", "eventId já utilizado com conteúdo diferente.");
        }
        return new IngestionResult(existing.eventId(), projectId, existing.ingestedAt(), true);
    }

    private JsonNode normalizeMetadata(JsonNode supplied) {
        JsonNode metadata = supplied == null || supplied.isNull() ? json.createObjectNode() : supplied.deepCopy();
        if (!metadata.isObject()) {
            throw invalidEvent("metadata deve ser um objeto JSON.");
        }
        if (metadata.size() > MAX_METADATA_KEYS) {
            throw invalidEvent("metadata excede o limite de 50 chaves no primeiro nível.");
        }
        if (containsSensitiveKey(metadata)) {
            throw invalidEvent("metadata contém uma chave sensível não permitida.");
        }
        try {
            if (json.writeValueAsBytes(metadata).length > MAX_METADATA_BYTES) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "event_too_large", "metadata excede o limite de 16 KiB.");
            }
        } catch (JsonProcessingException exception) {
            throw invalidEvent("metadata não contém JSON válido.");
        }
        return metadata;
    }

    private boolean containsSensitiveKey(JsonNode node) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (SENSITIVE_KEYS.contains(field.getKey().toLowerCase(Locale.ROOT)) || containsSensitiveKey(field.getValue())) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsSensitiveKey(child)) return true;
            }
        }
        return false;
    }

    private static boolean equivalent(StoredEvent existing, StoredEvent candidate) {
        return existing.type().equals(candidate.type())
            && existing.status().equals(candidate.status())
            && existing.durationMs() == candidate.durationMs()
            && existing.occurredAt().equals(candidate.occurredAt())
            && existing.metadata().equals(candidate.metadata());
    }

    private static ApiException invalidEvent(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_event", message);
    }

    public enum EventStatus { SUCCESS, ERROR, TIMEOUT, CANCELLED }

    public record EventCommand(
        UUID eventId,
        String type,
        EventStatus status,
        int durationMs,
        Instant occurredAt,
        JsonNode metadata
    ) {}

    public record IngestionResult(UUID eventId, UUID projectId, Instant ingestedAt, boolean duplicate) {}
}

package com.hookscope.api.adapters.in.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.hookscope.api.application.ApiException;
import com.hookscope.api.application.EventIngestionService;
import com.hookscope.api.application.EventIngestionService.EventCommand;
import com.hookscope.api.application.EventIngestionService.EventStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final EventIngestionService ingestion;

    public EventController(EventIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @PostMapping
    public ResponseEntity<EventResponse> ingest(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @Valid @RequestBody EventRequest request
    ) {
        String rawKey = bearerToken(authorization);
        var result = ingestion.ingest(rawKey, new EventCommand(
            request.eventId(),
            request.type(),
            request.status(),
            request.durationMs(),
            request.occurredAt(),
            request.metadata()));

        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(new EventResponse(result.eventId(), result.projectId(), result.ingestedAt(), result.duplicate()));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw invalidApiKey();
        }
        String value = authorization.substring(7).trim();
        if (value.isEmpty()) throw invalidApiKey();
        return value;
    }

    private static ApiException invalidApiKey() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_api_key", "API Key inválida.");
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record EventRequest(
        @NotNull UUID eventId,
        @NotBlank @Size(max = 120)
        @Pattern(regexp = "^[a-z][a-z0-9]*(\\.[a-z0-9]+)*$") String type,
        @NotNull EventStatus status,
        @NotNull @Min(0) @Max(86400000) Integer durationMs,
        @NotNull Instant occurredAt,
        JsonNode metadata
    ) {}

    public record EventResponse(UUID eventId, UUID projectId, Instant ingestedAt, boolean duplicate) {}
}

package com.hookscope.api.adapters.in.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.hookscope.api.adapters.out.persistence.EventStore.EventPage;
import com.hookscope.api.adapters.out.persistence.EventStore.StoredEvent;
import com.hookscope.api.application.EventIngestionService.EventStatus;
import com.hookscope.api.application.EventQueryService;
import com.hookscope.api.config.AppPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/events")
public class ProjectEventController {
    private final EventQueryService queries;

    public ProjectEventController(EventQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public EventPageResponse search(
        @AuthenticationPrincipal AppPrincipal principal,
        @PathVariable UUID projectId,
        @RequestParam(required = false)
        @Size(max = 120)
        @Pattern(regexp = "^[a-z][a-z0-9]*(\\.[a-z0-9]+)*$") String type,
        @RequestParam(required = false) EventStatus status,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        EventPage result = queries.search(
            principal.id(),
            projectId,
            type,
            status == null ? null : status.name(),
            from,
            to,
            page,
            size);
        return EventPageResponse.from(result, page, size);
    }

    public record EventResponse(
        UUID eventId,
        UUID projectId,
        String type,
        String status,
        int durationMs,
        Instant occurredAt,
        Instant ingestedAt,
        JsonNode metadata
    ) {
        static EventResponse from(StoredEvent event) {
            return new EventResponse(
                event.eventId(),
                event.projectId(),
                event.type(),
                event.status(),
                event.durationMs(),
                event.occurredAt(),
                event.ingestedAt(),
                event.metadata());
        }
    }

    public record EventPageResponse(
        List<EventResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        static EventPageResponse from(EventPage result, int page, int size) {
            int totalPages = result.totalElements() == 0
                ? 0
                : (int) ((result.totalElements() + size - 1) / size);
            return new EventPageResponse(
                result.content().stream().map(EventResponse::from).toList(),
                page,
                size,
                result.totalElements(),
                totalPages);
        }
    }
}

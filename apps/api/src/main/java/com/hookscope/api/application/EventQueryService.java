package com.hookscope.api.application;

import com.hookscope.api.adapters.out.persistence.EventStore;
import com.hookscope.api.adapters.out.persistence.EventStore.EventFilter;
import com.hookscope.api.adapters.out.persistence.EventStore.EventPage;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventQueryService {
    private final ProjectService projects;
    private final EventStore events;

    public EventQueryService(ProjectService projects, EventStore events) {
        this.projects = projects;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public EventPage search(
        UUID ownerId,
        UUID projectId,
        String type,
        String status,
        Instant from,
        Instant to,
        int page,
        int size
    ) {
        projects.owned(ownerId, projectId);
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "invalid_event_query",
                "O início do intervalo deve ser anterior ao fim.");
        }
        return events.search(new EventFilter(projectId, type, status, from, to), page, size);
    }
}

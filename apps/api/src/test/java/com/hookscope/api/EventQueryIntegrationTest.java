package com.hookscope.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookscope.api.adapters.out.persistence.EventStore;
import com.hookscope.api.adapters.out.persistence.EventStore.StoredEvent;
import com.hookscope.api.adapters.out.persistence.ProjectEntity;
import com.hookscope.api.adapters.out.persistence.ProjectRepository;
import com.hookscope.api.adapters.out.persistence.UserEntity;
import com.hookscope.api.adapters.out.persistence.UserRepository;
import com.hookscope.api.config.AppPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = HookScopeApplication.class)
@AutoConfigureMockMvc
class EventQueryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired EventStore events;

    @Test
    void paginatesAndOrdersEventsByOccurrence() throws Exception {
        Fixture fixture = fixture();
        UUID oldest = add(fixture.project().id, "payment.authorization", "SUCCESS", "2026-10-15T10:00:00Z");
        UUID middle = add(fixture.project().id, "payment.authorization", "ERROR", "2026-10-15T11:00:00Z");
        UUID newest = add(fixture.project().id, "webhook.delivery", "SUCCESS", "2026-10-15T12:00:00Z");

        MvcResult firstPage = http.perform(get(path(fixture.project().id))
                .with(user(fixture.principal()))
                .param("page", "0")
                .param("size", "2"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode body = body(firstPage);
        assertThat(body.get("totalElements").asLong()).isEqualTo(3);
        assertThat(body.get("totalPages").asInt()).isEqualTo(2);
        assertThat(body.get("content").size()).isEqualTo(2);
        assertThat(body.get("content").get(0).get("eventId").asText()).isEqualTo(newest.toString());
        assertThat(body.get("content").get(1).get("eventId").asText()).isEqualTo(middle.toString());

        MvcResult secondPage = http.perform(get(path(fixture.project().id))
                .with(user(fixture.principal()))
                .param("page", "1")
                .param("size", "2"))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(body(secondPage).get("content").get(0).get("eventId").asText()).isEqualTo(oldest.toString());
    }

    @Test
    void filtersByTypeStatusAndHalfOpenTimeRange() throws Exception {
        Fixture fixture = fixture();
        UUID expected = add(fixture.project().id, "payment.authorization", "SUCCESS", "2026-10-15T10:00:00Z");
        add(fixture.project().id, "payment.authorization", "ERROR", "2026-10-15T10:30:00Z");
        add(fixture.project().id, "payment.authorization", "SUCCESS", "2026-10-15T11:00:00Z");
        add(fixture.project().id, "webhook.delivery", "SUCCESS", "2026-10-15T10:15:00Z");

        MvcResult result = http.perform(get(path(fixture.project().id))
                .with(user(fixture.principal()))
                .param("type", "payment.authorization")
                .param("status", "SUCCESS")
                .param("from", "2026-10-15T09:00:00Z")
                .param("to", "2026-10-15T11:00:00Z"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode body = body(result);
        assertThat(body.get("totalElements").asLong()).isEqualTo(1);
        assertThat(body.get("content").get(0).get("eventId").asText()).isEqualTo(expected.toString());
    }

    @Test
    void protectsProjectOwnershipAndValidatesTimeRange() throws Exception {
        Fixture owner = fixture();
        Fixture stranger = fixture();
        add(stranger.project().id, "payment.authorization", "SUCCESS", "2026-10-15T10:00:00Z");

        MvcResult hidden = http.perform(get(path(stranger.project().id))
                .with(user(owner.principal())))
            .andExpect(status().isNotFound())
            .andReturn();
        assertThat(body(hidden).get("code").asText()).isEqualTo("project_not_found");

        MvcResult invalidRange = http.perform(get(path(owner.project().id))
                .with(user(owner.principal()))
                .param("from", "2026-10-15T12:00:00Z")
                .param("to", "2026-10-15T11:00:00Z"))
            .andExpect(status().isBadRequest())
            .andReturn();
        assertThat(body(invalidRange).get("code").asText()).isEqualTo("invalid_event_query");
    }

    private Fixture fixture() {
        UUID suffix = UUID.randomUUID();
        UserEntity user = users.saveAndFlush(new UserEntity(
            "Event Viewer",
            suffix + "@example.test",
            "not-a-real-password-hash"));
        ProjectEntity project = projects.saveAndFlush(new ProjectEntity(user.id, "Event Query", null));
        return new Fixture(
            project,
            new AppPrincipal(user.id, user.name, user.email, user.passwordHash, user.enabled));
    }

    private UUID add(UUID projectId, String type, String status, String occurredAt) {
        UUID eventId = UUID.randomUUID();
        Instant occurred = Instant.parse(occurredAt);
        boolean inserted = events.insert(new StoredEvent(
            projectId,
            eventId,
            type,
            status,
            100,
            occurred,
            occurred.plusMillis(10),
            json.createObjectNode().put("source", "integration-test")));
        assertThat(inserted).isTrue();
        return eventId;
    }

    private static String path(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/events";
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private record Fixture(ProjectEntity project, AppPrincipal principal) {}
}

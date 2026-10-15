package com.hookscope.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hookscope.api.adapters.out.persistence.ApiKeyEntity;
import com.hookscope.api.adapters.out.persistence.ApiKeyRepository;
import com.hookscope.api.adapters.out.persistence.ProjectEntity;
import com.hookscope.api.adapters.out.persistence.ProjectRepository;
import com.hookscope.api.adapters.out.persistence.UserEntity;
import com.hookscope.api.adapters.out.persistence.UserRepository;
import com.hookscope.api.domain.ApiKeyMaterial;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
class EventIngestionIntegrationTest {
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
    @Autowired JdbcTemplate database;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ApiKeyRepository apiKeys;

    @Test
    void acceptsAndDeduplicatesEventsWithoutSessionOrCsrf() throws Exception {
        KeyFixture fixture = key();
        UUID eventId = UUID.randomUUID();
        ObjectNode payload = event(eventId);

        MvcResult created = send(fixture.rawKey(), payload)
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode createdBody = body(created);
        assertThat(createdBody.get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(createdBody.get("projectId").asText()).isEqualTo(fixture.projectId().toString());
        assertThat(createdBody.get("duplicate").asBoolean()).isFalse();
        assertThat(created.getRequest().getSession(false)).isNull();
        assertThat(eventCount(fixture.projectId(), eventId)).isEqualTo(1);

        MvcResult duplicate = send(fixture.rawKey(), payload)
            .andExpect(status().isOk())
            .andReturn();
        assertThat(body(duplicate).get("duplicate").asBoolean()).isTrue();
        assertThat(body(duplicate).get("ingestedAt").asText()).isEqualTo(createdBody.get("ingestedAt").asText());
        assertThat(eventCount(fixture.projectId(), eventId)).isEqualTo(1);

        ObjectNode conflicting = payload.deepCopy().put("durationMs", 185);
        MvcResult conflict = send(fixture.rawKey(), conflicting)
            .andExpect(status().isConflict())
            .andReturn();
        assertThat(body(conflict).get("code").asText()).isEqualTo("event_conflict");
        assertThat(eventCount(fixture.projectId(), eventId)).isEqualTo(1);
    }

    @Test
    void rejectsMissingInvalidAndRevokedApiKeys() throws Exception {
        ObjectNode payload = event(UUID.randomUUID());

        MvcResult missing = http.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(payload)))
            .andExpect(status().isUnauthorized())
            .andReturn();
        assertThat(body(missing).get("code").asText()).isEqualTo("invalid_api_key");

        MvcResult malformed = send("not-a-hookscope-key", payload)
            .andExpect(status().isUnauthorized())
            .andReturn();
        assertThat(body(malformed).get("code").asText()).isEqualTo("invalid_api_key");

        KeyFixture fixture = key();
        ApiKeyEntity revoked = apiKeys.findByKeyPrefix(fixture.prefix()).orElseThrow();
        revoked.revokedAt = Instant.now();
        apiKeys.saveAndFlush(revoked);

        MvcResult rejected = send(fixture.rawKey(), payload)
            .andExpect(status().isUnauthorized())
            .andReturn();
        assertThat(body(rejected).get("code").asText()).isEqualTo("invalid_api_key");
    }

    @Test
    void validatesTimestampAndSensitiveMetadata() throws Exception {
        KeyFixture fixture = key();

        ObjectNode future = event(UUID.randomUUID())
            .put("occurredAt", Instant.now().plusSeconds(301).toString());
        MvcResult invalidTime = send(fixture.rawKey(), future)
            .andExpect(status().isBadRequest())
            .andReturn();
        assertThat(body(invalidTime).get("code").asText()).isEqualTo("invalid_event");

        ObjectNode sensitive = event(UUID.randomUUID());
        sensitive.withObject("metadata").put("Authorization", "Bearer secret");
        MvcResult invalidMetadata = send(fixture.rawKey(), sensitive)
            .andExpect(status().isBadRequest())
            .andReturn();
        assertThat(body(invalidMetadata).get("code").asText()).isEqualTo("invalid_event");
    }

    @Test
    void rejectsExpiredApiKeys() throws Exception {
        KeyFixture fixture = key();
        ApiKeyEntity expired = apiKeys.findByKeyPrefix(fixture.prefix()).orElseThrow();
        expired.createdAt = Instant.now().minusSeconds(120);
        expired.expiresAt = Instant.now().minusSeconds(60);
        apiKeys.saveAndFlush(expired);

        MvcResult rejected = send(fixture.rawKey(), event(UUID.randomUUID()))
            .andExpect(status().isUnauthorized())
            .andReturn();
        assertThat(body(rejected).get("code").asText()).isEqualTo("invalid_api_key");
    }

    @Test
    void scopesEventIdempotencyToEachProject() throws Exception {
        KeyFixture first = key();
        KeyFixture second = key();
        UUID eventId = UUID.randomUUID();
        ObjectNode payload = event(eventId);

        send(first.rawKey(), payload)
            .andExpect(status().isCreated());
        send(second.rawKey(), payload)
            .andExpect(status().isCreated());

        assertThat(eventCount(first.projectId(), eventId)).isEqualTo(1);
        assertThat(eventCount(second.projectId(), eventId)).isEqualTo(1);
        assertThat(database.queryForObject(
            "select count(*) from hookscope.event where event_id = ?",
            Integer.class, eventId)).isEqualTo(2);
    }

    @Test
    void enforcesMetadataAndPayloadLimits() throws Exception {
        KeyFixture fixture = key();

        ObjectNode maximumKeys = event(UUID.randomUUID());
        ObjectNode acceptedMetadata = (ObjectNode) maximumKeys.get("metadata");
        acceptedMetadata.removeAll();
        for (int index = 0; index < 50; index++) {
            acceptedMetadata.put("key" + index, index);
        }
        send(fixture.rawKey(), maximumKeys)
            .andExpect(status().isCreated());

        ObjectNode tooManyKeys = event(UUID.randomUUID());
        ObjectNode rejectedMetadata = (ObjectNode) tooManyKeys.get("metadata");
        rejectedMetadata.removeAll();
        for (int index = 0; index < 51; index++) {
            rejectedMetadata.put("key" + index, index);
        }
        MvcResult invalidKeys = send(fixture.rawKey(), tooManyKeys)
            .andExpect(status().isBadRequest())
            .andReturn();
        assertThat(body(invalidKeys).get("code").asText()).isEqualTo("invalid_event");

        ObjectNode oversized = event(UUID.randomUUID());
        ((ObjectNode) oversized.get("metadata")).removeAll().put("payload", "x".repeat(16 * 1024));
        MvcResult tooLarge = send(fixture.rawKey(), oversized)
            .andExpect(status().isPayloadTooLarge())
            .andReturn();
        assertThat(body(tooLarge).get("code").asText()).isEqualTo("event_too_large");
    }

    @Test
    void rejectsUndocumentedRequestFields() throws Exception {
        KeyFixture fixture = key();
        ObjectNode payload = event(UUID.randomUUID())
            .put("projectId", UUID.randomUUID().toString());

        MvcResult rejected = send(fixture.rawKey(), payload)
            .andExpect(status().isBadRequest())
            .andReturn();
        assertThat(body(rejected).get("code").asText()).isEqualTo("invalid_event");
    }

    private org.springframework.test.web.servlet.ResultActions send(String rawKey, JsonNode payload) throws Exception {
        return http.perform(post("/api/v1/events")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + rawKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(payload)));
    }

    private ObjectNode event(UUID eventId) {
        ObjectNode metadata = json.createObjectNode()
            .put("provider", "example-pay")
            .put("retry", false);
        return json.createObjectNode()
            .put("eventId", eventId.toString())
            .put("type", "payment.authorization")
            .put("status", "SUCCESS")
            .put("durationMs", 184)
            .put("occurredAt", Instant.now().minusSeconds(1).toString())
            .set("metadata", metadata);
    }

    private KeyFixture key() {
        UUID suffix = UUID.randomUUID();
        UserEntity user = users.saveAndFlush(new UserEntity("Event Producer", suffix + "@example.test", "not-a-real-password-hash"));
        ProjectEntity project = projects.saveAndFlush(new ProjectEntity(user.id, "Event Ingestion", null));
        var generated = ApiKeyMaterial.generate();
        apiKeys.saveAndFlush(new ApiKeyEntity(project.id, "Integration Test", generated.prefix(), generated.hash(), null));
        return new KeyFixture(project.id, generated.prefix(), generated.value());
    }

    private int eventCount(UUID projectId, UUID eventId) {
        return database.queryForObject(
            "select count(*) from hookscope.event where project_id = ? and event_id = ?",
            Integer.class, projectId, eventId);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private record KeyFixture(UUID projectId, String prefix, String rawKey) {}
}

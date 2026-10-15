package com.hookscope.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookscope.api.adapters.out.persistence.ApiKeyRepository;
import com.hookscope.api.application.ApiKeyValidationService;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class PhaseOneIntegrationTest {
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
    @Autowired ApiKeyRepository keyRepository;
    @Autowired ApiKeyValidationService validation;

    @Test
    void registrationValidatesAndNormalizesEmail() throws Exception {
        Session session = csrf();
        String email = uniqueEmail();
        http.perform(post("/api/v1/auth/register").session(session.cookie()).header("X-CSRF-TOKEN", session.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "Ana", "email", email, "password", "short"))))
            .andExpect(status().isBadRequest());

        MvcResult created = register(session, email.toUpperCase());
        assertThat(body(created).get("email").asText()).isEqualTo(email);
        assertThat(created.getResponse().getContentAsString()).doesNotContain("passwordHash");

        MvcResult duplicate = http.perform(post("/api/v1/auth/register").session(session.cookie()).header("X-CSRF-TOKEN", session.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "Outra", "email", email, "password", "strong-password-123"))))
            .andExpect(status().isConflict()).andReturn();
        assertThat(body(duplicate).get("code").asText()).isEqualTo("registration_failed");
        assertThat(duplicate.getResponse().getContentAsString()).doesNotContain(email);
    }

    @Test
    void loginCsrfSessionAndLogoutWorkTogether() throws Exception {
        http.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        Session session = csrf();
        String email = uniqueEmail();
        register(session, email);

        http.perform(post("/api/v1/auth/login").session(session.cookie()).header("X-CSRF-TOKEN", session.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("email", email, "password", "wrong-password"))))
            .andExpect(status().isUnauthorized());

        MvcResult login = login(session, email);
        MockHttpSession authenticated = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(authenticated).isNotNull();
        http.perform(get("/api/v1/auth/me").session(authenticated)).andExpect(status().isOk());
        http.perform(post("/api/v1/projects").session(authenticated).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("name", "No CSRF"))))
            .andExpect(status().isForbidden());

        http.perform(post("/api/v1/auth/logout").session(authenticated).header("X-CSRF-TOKEN", session.token()))
            .andExpect(status().isNoContent());
        assertThat(authenticated.isInvalid()).isTrue();
        http.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void projectsAreScopedToTheirOwner() throws Exception {
        Session owner = authenticated();
        Session stranger = authenticated();
        MvcResult created = http.perform(post("/api/v1/projects").session(owner.cookie()).header("X-CSRF-TOKEN", owner.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "Payments", "description", "Primary API"))))
            .andExpect(status().isCreated()).andReturn();
        String id = body(created).get("id").asText();

        MvcResult listing = http.perform(get("/api/v1/projects").session(owner.cookie())).andExpect(status().isOk()).andReturn();
        assertThat(body(listing).get("content").toString()).contains(id);
        http.perform(get("/api/v1/projects/" + id).session(stranger.cookie())).andExpect(status().isNotFound());
        http.perform(patch("/api/v1/projects/" + id).session(stranger.cookie()).header("X-CSRF-TOKEN", stranger.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "Taken"))))
            .andExpect(status().isNotFound());
        http.perform(get("/api/v1/projects/" + UUID.randomUUID()).session(owner.cookie())).andExpect(status().isNotFound());
        MvcResult updated = http.perform(patch("/api/v1/projects/" + id).session(owner.cookie()).header("X-CSRF-TOKEN", owner.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "Payments API"))))
            .andExpect(status().isOk()).andReturn();
        assertThat(body(updated).get("name").asText()).isEqualTo("Payments API");
    }

    @Test
    void apiKeySecretIsOnlyReturnedAtCreationAndRevocationInvalidatesIt() throws Exception {
        Session owner = authenticated();
        Session stranger = authenticated();
        MvcResult project = http.perform(post("/api/v1/projects").session(owner.cookie()).header("X-CSRF-TOKEN", owner.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "Integrations"))))
            .andExpect(status().isCreated()).andReturn();
        String projectId = body(project).get("id").asText();
        String path = "/api/v1/projects/" + projectId + "/api-keys";

        MvcResult created = http.perform(post(path).session(owner.cookie()).header("X-CSRF-TOKEN", owner.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "Production"))))
            .andExpect(status().isCreated()).andReturn();
        JsonNode createdBody = body(created);
        String rawKey = createdBody.get("key").asText();
        String prefix = createdBody.get("apiKey").get("keyPrefix").asText();
        String keyId = createdBody.get("apiKey").get("id").asText();
        assertThat(rawKey).startsWith(prefix + "_");
        assertThat(keyRepository.findByKeyPrefix(prefix).orElseThrow().keyHash).isNotEqualTo(rawKey);
        assertThat(validation.validate(rawKey)).contains(UUID.fromString(projectId));

        MvcResult listing = http.perform(get(path).session(owner.cookie())).andExpect(status().isOk()).andReturn();
        assertThat(listing.getResponse().getContentAsString()).contains(prefix).doesNotContain(rawKey).doesNotContain("keyHash");
        http.perform(get(path).session(stranger.cookie())).andExpect(status().isNotFound());
        http.perform(delete(path + "/" + keyId).session(stranger.cookie()).header("X-CSRF-TOKEN", stranger.token()))
            .andExpect(status().isNotFound());
        http.perform(delete(path + "/" + keyId).session(owner.cookie()).header("X-CSRF-TOKEN", owner.token()))
            .andExpect(status().isNoContent());
        assertThat(validation.validate(rawKey)).isEmpty();
    }

    private Session csrf() throws Exception {
        MvcResult result = http.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        HttpSession session = result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return new Session((MockHttpSession) session, body(result).get("token").asText());
    }

    private MvcResult register(Session session, String email) throws Exception {
        return http.perform(post("/api/v1/auth/register").session(session.cookie()).header("X-CSRF-TOKEN", session.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "Test User", "email", email, "password", "strong-password-123"))))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult login(Session session, String email) throws Exception {
        return http.perform(post("/api/v1/auth/login").session(session.cookie()).header("X-CSRF-TOKEN", session.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("email", email, "password", "strong-password-123"))))
            .andExpect(status().isOk()).andReturn();
    }

    private Session authenticated() throws Exception {
        Session session = csrf();
        String email = uniqueEmail();
        register(session, email);
        MvcResult result = login(session, email);
        return new Session((MockHttpSession) result.getRequest().getSession(false), session.token());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private static String uniqueEmail() { return UUID.randomUUID() + "@example.test"; }
    private record Session(MockHttpSession cookie, String token) {}
}

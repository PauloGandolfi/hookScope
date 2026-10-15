package com.hookscope.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookscope.api.adapters.out.persistence.ApiKeyEntity;
import com.hookscope.api.adapters.out.persistence.ApiKeyRepository;
import com.hookscope.api.adapters.out.persistence.ProjectEntity;
import com.hookscope.api.adapters.out.persistence.ProjectRepository;
import com.hookscope.api.adapters.out.persistence.UserEntity;
import com.hookscope.api.adapters.out.persistence.UserRepository;
import com.hookscope.api.application.ApiKeyValidationService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@SpringBootTest(classes = {HookScopeApplication.class, WebSecurityFlowTest.MemoryRepositories.class},
    properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
class WebSecurityFlowTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired ApiKeyValidationService validation;

    @Test
    void sessionCsrfAndPrivateRoutesWorkWithoutAContainer() throws Exception {
        http.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
        MvcResult csrfResult = http.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String token = body(csrfResult).get("token").asText();
        assertThat(session).isNotNull();

        String email = UUID.randomUUID() + "@example.test";
        MvcResult registered = http.perform(post("/api/v1/auth/register").session(session).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "Ana", "email", email, "password", "strong-password-123"))))
            .andExpect(status().isCreated()).andReturn();
        assertThat(body(registered).get("email").asText()).isEqualTo(email);
        MvcResult duplicate = http.perform(post("/api/v1/auth/register").session(session).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "Ana", "email", email, "password", "strong-password-123"))))
            .andExpect(status().isConflict()).andReturn();
        assertThat(duplicate.getResponse().getContentAsString()).doesNotContain(email);

        http.perform(post("/api/v1/auth/login").session(session).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email, "password", "strong-password-123"))))
            .andExpect(status().isForbidden());
        http.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("email", email, "password", "wrong-password"))))
            .andExpect(status().isUnauthorized());
        String anonymousSessionId = session.getId();
        MvcResult loggedIn = http.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("email", email, "password", "strong-password-123"))))
            .andExpect(status().isOk()).andReturn();
        MockHttpSession authenticated = (MockHttpSession) loggedIn.getRequest().getSession(false);
        assertThat(authenticated).isNotNull();
        assertThat(authenticated.getId()).isNotEqualTo(anonymousSessionId);
        http.perform(get("/api/v1/auth/me").session(authenticated)).andExpect(status().isOk());

        MvcResult project = http.perform(post("/api/v1/projects").session(authenticated).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "Payments"))))
            .andExpect(status().isCreated()).andReturn();
        String projectId = body(project).get("id").asText();
        MvcResult key = http.perform(post("/api/v1/projects/" + projectId + "/api-keys").session(authenticated).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "Development"))))
            .andExpect(status().isCreated()).andReturn();
        assertThat(body(key).get("key").asText()).startsWith("hs_");
        MvcResult keys = http.perform(get("/api/v1/projects/" + projectId + "/api-keys").session(authenticated))
            .andExpect(status().isOk()).andReturn();
        assertThat(keys.getResponse().getContentAsString()).doesNotContain(body(key).get("key").asText());

        http.perform(post("/api/v1/auth/logout").session(authenticated).header("X-CSRF-TOKEN", token))
            .andExpect(status().isNoContent());
        assertThat(authenticated.isInvalid()).isTrue();
        http.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()); }

    @Test
    void ownersAreIsolatedAndRevokedKeysFailValidation() throws Exception {
        Session owner = authenticated();
        Session stranger = authenticated();
        MvcResult project = http.perform(post("/api/v1/projects").session(owner.cookie()).header("X-CSRF-TOKEN", owner.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "Private"))))
            .andExpect(status().isCreated()).andReturn();
        String projectId = body(project).get("id").asText();
        http.perform(get("/api/v1/projects/" + projectId).session(stranger.cookie())).andExpect(status().isNotFound());
        http.perform(patch("/api/v1/projects/" + projectId).session(stranger.cookie()).header("X-CSRF-TOKEN", stranger.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "Taken"))))
            .andExpect(status().isNotFound());

        String keyPath = "/api/v1/projects/" + projectId + "/api-keys";
        MvcResult created = http.perform(post(keyPath).session(owner.cookie()).header("X-CSRF-TOKEN", owner.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "Secret"))))
            .andExpect(status().isCreated()).andReturn();
        String rawKey = body(created).get("key").asText();
        String keyId = body(created).get("apiKey").get("id").asText();
        assertThat(validation.validate(rawKey)).contains(UUID.fromString(projectId));
        http.perform(get("/api/v1/projects").header("X-API-Key", rawKey)).andExpect(status().isUnauthorized());
        http.perform(get(keyPath).session(stranger.cookie())).andExpect(status().isNotFound());
        http.perform(delete(keyPath + "/" + keyId).session(stranger.cookie()).header("X-CSRF-TOKEN", stranger.token()))
            .andExpect(status().isNotFound());
        http.perform(delete(keyPath + "/" + keyId).session(owner.cookie()).header("X-CSRF-TOKEN", owner.token()))
            .andExpect(status().isNoContent());
        assertThat(validation.validate(rawKey)).isEmpty();
    }

    private Session authenticated() throws Exception {
        MvcResult csrfResult = http.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String token = body(csrfResult).get("token").asText();
        String email = UUID.randomUUID() + "@example.test";
        http.perform(post("/api/v1/auth/register").session(session).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("name", "User", "email", email, "password", "strong-password-123"))))
            .andExpect(status().isCreated());
        MvcResult loggedIn = http.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("email", email, "password", "strong-password-123"))))
            .andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) loggedIn.getRequest().getSession(false), token);
    }

    private record Session(MockHttpSession cookie, String token) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class MemoryRepositories {
        private final Map<UUID, UserEntity> users = new ConcurrentHashMap<>();
        private final Map<UUID, ProjectEntity> projects = new ConcurrentHashMap<>();
        private final Map<UUID, ApiKeyEntity> keys = new ConcurrentHashMap<>();

        @Bean
        JdbcTemplate jdbcTemplate() {
            return new JdbcTemplate();
        }

        @Bean
        UserRepository userRepository() {
            return proxy(UserRepository.class, (method, args) -> switch (method) {
                case "existsByEmail" -> users.values().stream().anyMatch(user -> user.email.equals(args[0]));
                case "findByEmail" -> users.values().stream().filter(user -> user.email.equals(args[0])).findFirst();
                case "saveAndFlush" -> { UserEntity user = (UserEntity) args[0]; users.put(user.id, user); yield user; }
                default -> throw new UnsupportedOperationException(method);
            });
        }

        @Bean
        ProjectRepository projectRepository() {
            return proxy(ProjectRepository.class, (method, args) -> switch (method) {
                case "save" -> { ProjectEntity project = (ProjectEntity) args[0]; projects.put(project.id, project); yield project; }
                case "findByIdAndOwnerId" -> Optional.ofNullable(projects.get(args[0])).filter(project -> project.ownerId.equals(args[1]));
                case "findAllByOwnerId" -> {
                    Pageable pageable = (Pageable) args[1];
                    var owned = projects.values().stream().filter(project -> project.ownerId.equals(args[0]))
                        .sorted(Comparator.comparing((ProjectEntity project) -> project.createdAt).reversed()).toList();
                    int start = (int) Math.min(pageable.getOffset(), owned.size());
                    int end = Math.min(start + pageable.getPageSize(), owned.size());
                    yield new PageImpl<>(owned.subList(start, end), pageable, owned.size());
                }
                default -> throw new UnsupportedOperationException(method);
            });
        }

        @Bean
        ApiKeyRepository apiKeyRepository() {
            return proxy(ApiKeyRepository.class, (method, args) -> switch (method) {
                case "save" -> { ApiKeyEntity key = (ApiKeyEntity) args[0]; keys.put(key.id, key); yield key; }
                case "findAllByProjectIdOrderByCreatedAtDesc" -> keys.values().stream().filter(key -> key.projectId.equals(args[0]))
                    .sorted(Comparator.comparing((ApiKeyEntity key) -> key.createdAt).reversed()).toList();
                case "findByIdAndProjectId" -> Optional.ofNullable(keys.get(args[0])).filter(key -> key.projectId.equals(args[1]));
                case "findByKeyPrefix" -> keys.values().stream().filter(key -> key.keyPrefix.equals(args[0])).findFirst();
                default -> throw new UnsupportedOperationException(method);
            });
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, RepositoryCall call) {
            InvocationHandler handler = (instance, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> type.getSimpleName() + "MemoryProxy";
                        case "hashCode" -> System.identityHashCode(instance);
                        case "equals" -> instance == args[0];
                        default -> null;
                    };
                }
                return call.invoke(method.getName(), args);
            };
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
        }

        private interface RepositoryCall { Object invoke(String method, Object[] args); }
    }
}

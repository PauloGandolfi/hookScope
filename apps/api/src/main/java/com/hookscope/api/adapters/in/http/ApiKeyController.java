package com.hookscope.api.adapters.in.http;

import com.hookscope.api.adapters.out.persistence.ApiKeyEntity;
import com.hookscope.api.application.ApiKeyService;
import com.hookscope.api.config.AppPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/api-keys")
public class ApiKeyController {
    private final ApiKeyService keys;

    public ApiKeyController(ApiKeyService keys) { this.keys = keys; }

    @PostMapping
    public ResponseEntity<CreatedKeyResponse> create(@AuthenticationPrincipal AppPrincipal principal, @PathVariable UUID projectId,
                                                     @Valid @RequestBody CreateKeyRequest request) {
        var created = keys.create(principal.id(), projectId, request.name(), request.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(new CreatedKeyResponse(KeyResponse.from(created.metadata()), created.value()));
    }

    @GetMapping
    public List<KeyResponse> list(@AuthenticationPrincipal AppPrincipal principal, @PathVariable UUID projectId) {
        return keys.list(principal.id(), projectId).stream().map(KeyResponse::from).toList();
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal AppPrincipal principal, @PathVariable UUID projectId, @PathVariable UUID keyId) {
        keys.revoke(principal.id(), projectId, keyId);
        return ResponseEntity.noContent().build();
    }

    public record CreateKeyRequest(@NotBlank @Size(max = 120) String name, Instant expiresAt) {}
    public record KeyResponse(UUID id, String name, String keyPrefix, Instant createdAt,
                              Instant expiresAt, Instant revokedAt, Instant lastUsedAt) {
        public static KeyResponse from(ApiKeyEntity key) {
            return new KeyResponse(key.id, key.name, key.keyPrefix, key.createdAt, key.expiresAt, key.revokedAt, key.lastUsedAt);
        }
    }
    public record CreatedKeyResponse(KeyResponse apiKey, String key) {
        @Override public String toString() { return "CreatedKeyResponse[apiKeyId=" + apiKey.id() + ", key=REDACTED]"; }
    }
}

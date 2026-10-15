package com.hookscope.api.application;

import com.hookscope.api.adapters.out.persistence.ApiKeyEntity;
import com.hookscope.api.adapters.out.persistence.ApiKeyRepository;
import com.hookscope.api.domain.ApiKeyMaterial;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyService {
    private final ApiKeyRepository keys;
    private final ProjectService projects;

    public ApiKeyService(ApiKeyRepository keys, ProjectService projects) {
        this.keys = keys;
        this.projects = projects;
    }

    @Transactional
    public Created create(UUID ownerId, UUID projectId, String name, Instant expiresAt) {
        projects.owned(ownerId, projectId);
        var generated = ApiKeyMaterial.generate();
        ApiKeyEntity key = new ApiKeyEntity(projectId, name.trim(), generated.prefix(), generated.hash(), expiresAt);
        if (expiresAt != null && !expiresAt.isAfter(key.createdAt)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_expiry", "A validade deve estar no futuro.");
        }
        ApiKeyEntity saved = keys.save(key);
        return new Created(saved, generated.value());
    }

    @Transactional(readOnly = true)
    public List<ApiKeyEntity> list(UUID ownerId, UUID projectId) {
        projects.owned(ownerId, projectId);
        return keys.findAllByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Transactional
    public void revoke(UUID ownerId, UUID projectId, UUID keyId) {
        projects.owned(ownerId, projectId);
        ApiKeyEntity key = keys.findByIdAndProjectId(keyId, projectId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "api_key_not_found", "API Key não encontrada."));
        if (key.revokedAt == null) key.revokedAt = Instant.now();
    }

    public record Created(ApiKeyEntity metadata, String value) {
        @Override public String toString() { return "Created[apiKeyId=" + metadata.id + ", value=REDACTED]"; }
    }
}

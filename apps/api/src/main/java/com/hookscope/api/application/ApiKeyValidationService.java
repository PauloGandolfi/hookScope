package com.hookscope.api.application;

import com.hookscope.api.adapters.out.persistence.ApiKeyRepository;
import com.hookscope.api.domain.ApiKeyMaterial;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyValidationService {
    private final ApiKeyRepository keys;

    public ApiKeyValidationService(ApiKeyRepository keys) { this.keys = keys; }

    @Transactional
    public Optional<UUID> validate(String rawKey) {
        return ApiKeyMaterial.prefixOf(rawKey)
            .flatMap(keys::findByKeyPrefix)
            .filter(key -> key.revokedAt == null && (key.expiresAt == null || key.expiresAt.isAfter(Instant.now())))
            .filter(key -> ApiKeyMaterial.matches(rawKey, key.keyHash))
            .map(key -> {
                key.lastUsedAt = Instant.now();
                return key.projectId;
            });
    }
}

package com.hookscope.api.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_key", schema = "hookscope")
public class ApiKeyEntity {
    @Id public UUID id;
    @Column(name = "project_id", nullable = false) public UUID projectId;
    @Column(nullable = false, length = 120) public String name;
    @Column(name = "key_prefix", nullable = false, length = 32, unique = true) public String keyPrefix;
    @Column(name = "key_hash", nullable = false, length = 64) public String keyHash;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "expires_at") public Instant expiresAt;
    @Column(name = "revoked_at") public Instant revokedAt;
    @Column(name = "last_used_at") public Instant lastUsedAt;

    protected ApiKeyEntity() {}

    public ApiKeyEntity(UUID projectId, String name, String keyPrefix, String keyHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }
}

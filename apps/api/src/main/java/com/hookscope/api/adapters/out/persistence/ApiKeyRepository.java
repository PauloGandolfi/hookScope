package com.hookscope.api.adapters.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {
    List<ApiKeyEntity> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId);
    Optional<ApiKeyEntity> findByIdAndProjectId(UUID id, UUID projectId);
    Optional<ApiKeyEntity> findByKeyPrefix(String keyPrefix);
}

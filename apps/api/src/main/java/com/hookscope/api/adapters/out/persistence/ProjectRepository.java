package com.hookscope.api.adapters.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {
    Page<ProjectEntity> findAllByOwnerId(UUID ownerId, Pageable pageable);
    Optional<ProjectEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
}

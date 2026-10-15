package com.hookscope.api.application;

import com.hookscope.api.adapters.out.persistence.ProjectEntity;
import com.hookscope.api.adapters.out.persistence.ProjectRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    private final ProjectRepository projects;

    public ProjectService(ProjectRepository projects) { this.projects = projects; }

    @Transactional
    public ProjectEntity create(UUID ownerId, String name, String description) {
        return projects.save(new ProjectEntity(ownerId, name.trim(), clean(description)));
    }

    @Transactional(readOnly = true)
    public Page<ProjectEntity> list(UUID ownerId, int page, int size) {
        return projects.findAllByOwnerId(ownerId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional(readOnly = true)
    public ProjectEntity owned(UUID ownerId, UUID projectId) {
        return projects.findByIdAndOwnerId(projectId, ownerId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project_not_found", "Projeto não encontrado."));
    }

    @Transactional
    public ProjectEntity update(UUID ownerId, UUID projectId, String name, String description) {
        ProjectEntity project = owned(ownerId, projectId);
        if (name != null) project.name = name.trim();
        if (description != null) project.description = clean(description);
        project.updatedAt = java.time.Instant.now();
        return project;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

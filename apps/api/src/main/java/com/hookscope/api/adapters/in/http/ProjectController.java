package com.hookscope.api.adapters.in.http;

import com.hookscope.api.adapters.out.persistence.ProjectEntity;
import com.hookscope.api.application.ApiException;
import com.hookscope.api.application.ProjectService;
import com.hookscope.api.config.AppPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService projects;

    public ProjectController(ProjectService projects) { this.projects = projects; }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal AppPrincipal principal, @Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(projects.create(principal.id(), request.name(), request.description())));
    }

    @GetMapping
    public Page<ProjectResponse> list(@AuthenticationPrincipal AppPrincipal principal,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return projects.list(principal.id(), page, size).map(ProjectResponse::from);
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@AuthenticationPrincipal AppPrincipal principal, @PathVariable UUID id) {
        return ProjectResponse.from(projects.owned(principal.id(), id));
    }

    @PatchMapping("/{id}")
    public ProjectResponse update(@AuthenticationPrincipal AppPrincipal principal, @PathVariable UUID id,
                                  @Valid @RequestBody UpdateProjectRequest request) {
        if (request.name() == null && request.description() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error", "Informe ao menos um campo para alterar.");
        }
        if (request.name() != null && request.name().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error", "Nome do projeto inválido.");
        }
        return ProjectResponse.from(projects.update(principal.id(), id, request.name(), request.description()));
    }

    public record CreateProjectRequest(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description) {}
    public record UpdateProjectRequest(@Size(max = 120) String name, @Size(max = 1000) String description) {}
    public record ProjectResponse(UUID id, String name, String description, Instant createdAt, Instant updatedAt) {
        public static ProjectResponse from(ProjectEntity project) {
            return new ProjectResponse(project.id, project.name, project.description, project.createdAt, project.updatedAt);
        }
    }
}

package zpi.project_bird.backend.project;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.validation.Valid;
import zpi.project_bird.backend.security.CanEditOrganization;
import zpi.project_bird.backend.security.CanManageOrganization;
import zpi.project_bird.backend.security.CanViewOrganization;

@RestController
@RequestMapping("/api/organizations/{organizationId}/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    @CanViewOrganization
    public List<Project> findAll(@PathVariable UUID organizationId) {
        return projectService.findAll(organizationId);
    }

    @GetMapping("/{projectId}")
    @CanViewOrganization
    public Project findById(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId) {
        return projectService.findById(organizationId, projectId);
    }

    @GetMapping("/{projectId}/features")
    @CanViewOrganization
    public List<Feature> getFeatures(
        @PathVariable UUID organizationId,
        @PathVariable UUID projectId
    ) {
        return projectService.getFeatures(projectId);
    }

    @PostMapping
    @CanEditOrganization
    public ResponseEntity<Project> create(
        @PathVariable UUID organizationId,
        @Valid @RequestBody ProjectRequest.Create request
    ) {
        Project project = projectService.create(
            organizationId,
            request.name(),
            request.topLeft().toProjectCoordinates(),
            request.bottomRight().toProjectCoordinates());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{projectId}")
            .buildAndExpand(project.id())
            .toUri();

        return ResponseEntity.created(location).body(project);
    }

    @DeleteMapping("/{projectId}")
    @CanManageOrganization
    public ResponseEntity<Void> delete(
        @PathVariable UUID organizationId,
        @PathVariable UUID projectId
    ) {
        projectService.delete(organizationId, projectId);
        return ResponseEntity.noContent().build();
    }
}

package zpi.project_bird.backend.project;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import zpi.project_bird.backend.security.CanAnnotateOrganization;
import zpi.project_bird.backend.security.CanManageOrganization;

@RestController
@RequestMapping("/api/organizations/{organizationId}/projects/{projectId}")
public class AnnotationController {
    private final AnnotationService annotations;
    public AnnotationController(AnnotationService annotations) { this.annotations = annotations; }

    @PostMapping("/detections")
    @CanAnnotateOrganization
    public ResponseEntity<DetectionState> create(@PathVariable UUID organizationId, @PathVariable UUID projectId,
            @Valid @RequestBody AnnotationRequest.Create request, Authentication authentication) {
        var state = annotations.create(organizationId, projectId, request, authentication.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(state.id()).toUri();
        return ResponseEntity.created(location).body(state);
    }

    @PatchMapping("/detections/{detectionId}")
    @CanAnnotateOrganization
    public DetectionState update(@PathVariable UUID organizationId, @PathVariable UUID projectId, @PathVariable UUID detectionId,
            @Valid @RequestBody AnnotationRequest.Update request, Authentication authentication) {
        return annotations.update(organizationId, projectId, detectionId, request, authentication.getName());
    }

    @DeleteMapping("/detections/{detectionId}")
    @CanAnnotateOrganization
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId, @PathVariable UUID projectId, @PathVariable UUID detectionId,
            @RequestParam @Min(0) int expectedVersion, Authentication authentication) {
        annotations.delete(organizationId, projectId, detectionId, expectedVersion, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/detections/{detectionId}/verification")
    @CanAnnotateOrganization
    public DetectionState verify(@PathVariable UUID organizationId, @PathVariable UUID projectId, @PathVariable UUID detectionId,
            @Valid @RequestBody AnnotationRequest.Verification request, Authentication authentication) {
        return annotations.verify(organizationId, projectId, detectionId, request, authentication.getName());
    }

    @GetMapping("/annotation-history")
    @CanManageOrganization
    public List<AnnotationHistoryEntry> history(@PathVariable UUID organizationId, @PathVariable UUID projectId,
            @RequestParam(required = false) UUID detectionId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return annotations.history(organizationId, projectId, detectionId, limit, offset);
    }
}

package zpi.project_bird.backend.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class OrganizationRequest {
    private OrganizationRequest() {
    }

    public record Create(@NotBlank @Size(max = 256) String name) {
    }
}

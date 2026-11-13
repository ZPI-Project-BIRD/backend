package zpi.project_bird.backend.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import zpi.project_bird.backend.user.OrganizationRole;

public final class OrganizationRequest {
    private OrganizationRequest() {
    }

    public record Create(@NotBlank @Size(max = 256) String name) {
    }

    public record AddMembership(@NotNull UUID userId, @NotNull OrganizationRole membershipRole) {
    }

    public record UpdateRole(@NotNull OrganizationRole membershipRole) {
    }
}

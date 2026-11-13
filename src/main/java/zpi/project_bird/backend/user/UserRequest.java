package zpi.project_bird.backend.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UserRequest {
    private UserRequest() {
    }

    public record CreateInOrganization(
            @NotBlank @Size(max = 256) String friendlyName,
            @NotBlank @Size(max = 16) String username,
            @NotNull OrganizationRole membershipRole) {
    }

    public record CreateWithoutOrganization(
            @NotBlank @Size(max = 256) String friendlyName,
            @NotBlank @Size(max = 16) String username,
            boolean isAdmin) {
    }

    public record UpdateFullName(
            @NotBlank @Size(max = 256) String fullName) {
    }

    public record UpdatePassword(
            @NotBlank String newPassword) {
    }

    public record Ban(@NotBlank @Size(max = 2000) String reason) {
    }
}

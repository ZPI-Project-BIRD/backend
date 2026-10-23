package zpi.project_bird.backend.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class MeRequest {
    private MeRequest() {
    }

    public record UpdateFullName(
            @NotBlank @Size(max = 256) String fullName) {
    }

    public record UpdatePassword(
            @NotBlank String currentPassword,
            @NotBlank String newPassword) {
    }
}

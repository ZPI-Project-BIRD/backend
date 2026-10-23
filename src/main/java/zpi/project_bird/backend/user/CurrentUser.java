package zpi.project_bird.backend.user;

import java.util.UUID;

public record CurrentUser(
        UUID id,
        String fullName,
        String username,
        boolean isAdmin) {
}

package zpi.project_bird.backend.user;

import java.time.LocalDateTime;
import java.util.UUID;

public record User(
    UUID id,
    String friendlyName,
    String username,
    boolean isAdmin,
    LocalDateTime bannedAt,
    String banReason,
    String membershipRole) {
}

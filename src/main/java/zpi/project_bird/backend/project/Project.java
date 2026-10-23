package zpi.project_bird.backend.project;

import java.util.UUID;

public record Project(
        UUID id,
        String name,
        UUID organizationId,
        Coordinates topLeft,
        Coordinates bottomRight) {

    public record Coordinates(double longitude, double latitude) {
    }
}

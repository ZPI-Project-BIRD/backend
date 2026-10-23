package zpi.project_bird.backend.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class ProjectRequest {
    private ProjectRequest() {
    }

    public record Create(
            @NotBlank String layer,
            @NotBlank String name,
            @NotNull @Valid Coordinates topLeft,
            @NotNull @Valid Coordinates bottomRight) {
    }

    public record Coordinates(
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude) {

        Project.Coordinates toProjectCoordinates() {
            return new Project.Coordinates(longitude, latitude);
        }
    }
}

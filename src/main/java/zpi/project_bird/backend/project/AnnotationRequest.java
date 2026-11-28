package zpi.project_bird.backend.project;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;

public final class AnnotationRequest {
    private AnnotationRequest() {}

    // Accept legacy polygon arrays and typed polygon/road geometry in the source projected CRS.
    public record Create(@NotBlank String featureId,
            @NotNull JsonNode bounds) {}
    public record Update(@NotNull @Min(0) Integer expectedVersion,
            @NotNull JsonNode bounds) {}
    public record Verification(@NotNull @Min(0) Integer expectedVersion, @NotNull Boolean verified) {}
}

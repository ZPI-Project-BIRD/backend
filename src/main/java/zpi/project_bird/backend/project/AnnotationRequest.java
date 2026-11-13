package zpi.project_bird.backend.project;

import java.util.List;
import jakarta.validation.constraints.*;

public final class AnnotationRequest {
    private AnnotationRequest() {}

    // The existing API uses a multi-polygon coordinate array in the source raster's projected CRS.
    public record Create(@NotBlank String featureId,
            @NotEmpty @Size(max = 10000) List<List<List<List<Double>>>> bounds) {}
    public record Update(@NotNull @Min(0) Integer expectedVersion,
            @NotEmpty @Size(max = 10000) List<List<List<List<Double>>>> bounds) {}
    public record Verification(@NotNull @Min(0) Integer expectedVersion, @NotNull Boolean verified) {}
}

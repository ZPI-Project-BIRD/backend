package zpi.project_bird.backend.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class ProjectRequest {
    private ProjectRequest() {
    }

    public record Create(
            @NotBlank String name,
            @NotNull @Valid Coordinates topLeft,
            @NotNull @Valid Coordinates bottomRight) {
        private static final double EARTH_RADIUS_KILOMETERS = 6_371.0088;
        private static final double MAX_AREA_SQUARE_KILOMETERS = 20.0;

        @AssertTrue(message = "topLeft must be above and to the left of bottomRight")
        public boolean isBoundaryValid() {
            if (!hasCompleteBoundary()) {
                return true; // @NotNull reports these errors
            }

            return topLeft.latitude() > bottomRight.latitude() && topLeft.longitude() < bottomRight.longitude();
        }

        @AssertTrue(message = "project area must not exceed 20 km²")
        public boolean isAreaValid() {
            if (!hasCompleteBoundary() || !isBoundaryValid()) {
                return true;
            }

            double longitudeSpanRadians = Math.toRadians(bottomRight.longitude() - topLeft.longitude());
            double latitudeBand = Math.sin(Math.toRadians(topLeft.latitude())) - Math.sin(Math.toRadians(bottomRight.latitude()));
            double areaSquareKilometers = EARTH_RADIUS_KILOMETERS * EARTH_RADIUS_KILOMETERS * longitudeSpanRadians * latitudeBand;

            return areaSquareKilometers <= MAX_AREA_SQUARE_KILOMETERS;
        }

        private boolean hasCompleteBoundary() {
            return topLeft != null
                    && topLeft.longitude() != null
                    && topLeft.latitude() != null
                    && bottomRight != null
                    && bottomRight.longitude() != null
                    && bottomRight.latitude() != null;
        }
    }

    public record Coordinates(
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude) {

        Project.Coordinates toProjectCoordinates() {
            return new Project.Coordinates(longitude, latitude);
        }
    }
}

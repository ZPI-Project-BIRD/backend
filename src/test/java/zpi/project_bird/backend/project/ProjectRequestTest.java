package zpi.project_bird.backend.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class ProjectRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAreaBelowTwentySquareKilometers() {
        ProjectRequest.Create request = requestWithBounds(
                new ProjectRequest.Coordinates(0.0, 0.04),
                new ProjectRequest.Coordinates(0.04, 0.0));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsAreaAboveTwentySquareKilometers() {
        ProjectRequest.Create request = requestWithBounds(
                new ProjectRequest.Coordinates(0.0, 0.05),
                new ProjectRequest.Coordinates(0.05, 0.0));

        Set<ConstraintViolation<ProjectRequest.Create>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("project area must not exceed 20 km²");
    }

    @Test
    void leavesMissingCoordinateReportingToNotNull() {
        ProjectRequest.Create request = requestWithBounds(
                new ProjectRequest.Coordinates(null, 0.04),
                new ProjectRequest.Coordinates(0.04, 0.0));

        Set<ConstraintViolation<ProjectRequest.Create>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("must not be null");
    }

    private static ProjectRequest.Create requestWithBounds(
            ProjectRequest.Coordinates topLeft,
            ProjectRequest.Coordinates bottomRight) {
        return new ProjectRequest.Create("name", topLeft, bottomRight);
    }
}

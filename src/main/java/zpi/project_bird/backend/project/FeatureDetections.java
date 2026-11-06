package zpi.project_bird.backend.project;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record FeatureDetections(
    String featureId,
    List<Detection> detections
) {
    public record Detection(
        UUID id,
        UUID recognitionJobId,
        @JsonRawValue String bounds
    ) {
    }
}

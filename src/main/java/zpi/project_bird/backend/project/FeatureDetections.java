package zpi.project_bird.backend.project;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record FeatureDetections(
    String featureId,
    List<Detection> detections
) {
    public record Detection(
        UUID id,
        UUID recognitionJobId,
        @JsonRawValue String bounds,
        int version,
        String source,
        String verifiedBy,
        OffsetDateTime verifiedAt
    ) {
        public Detection(UUID id, UUID recognitionJobId, String bounds) {
            this(id, recognitionJobId, bounds, 0, "MODEL", null, null);
        }
    }
}

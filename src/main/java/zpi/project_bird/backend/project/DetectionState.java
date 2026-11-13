package zpi.project_bird.backend.project;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonRawValue;

public record DetectionState(UUID id, UUID projectId, String featureId, UUID recognitionJobId,
        UUID sourceDetectionId, @JsonRawValue String bounds, boolean deleted, int version,
        String updatedBy, OffsetDateTime updatedAt, String verifiedBy, OffsetDateTime verifiedAt) {
    public DetectionState change(String newBounds, boolean deleted, String actor, boolean verified) {
        var now = OffsetDateTime.now();
        return new DetectionState(id, projectId, featureId, recognitionJobId, sourceDetectionId,
            newBounds, deleted, version + 1, actor, now, verified ? actor : null, verified ? now : null);
    }

    public FeatureDetections.Detection toDetection() {
        return new FeatureDetections.Detection(id, recognitionJobId, bounds, version,
            sourceDetectionId == null ? "MANUAL" : "MODEL", verifiedBy, verifiedAt);
    }
}

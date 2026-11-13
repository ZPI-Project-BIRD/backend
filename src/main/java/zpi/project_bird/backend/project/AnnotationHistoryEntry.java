package zpi.project_bird.backend.project;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonRawValue;

public record AnnotationHistoryEntry(UUID id, UUID detectionId, String action, String actor,
        OffsetDateTime occurredAt, @JsonRawValue String before, @JsonRawValue String after) {}

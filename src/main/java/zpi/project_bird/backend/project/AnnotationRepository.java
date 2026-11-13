package zpi.project_bird.backend.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class AnnotationRepository {
    private final DSLContext dsl;
    public AnnotationRepository(DSLContext dsl) { this.dsl = dsl; }

    public Optional<DetectionState> find(UUID projectId, UUID detectionId) {
        var annotation = dsl.fetchOne("select * from geoportal.detection_annotations where project_id = ? and id = ?", projectId, detectionId);
        if (annotation != null) return Optional.of(toState(annotation));
        var raw = dsl.fetchOne("""
            select d.id, d.recognition_job_id, d.bounds, j.feature_id
            from geoportal.detections d join geoportal.recognition_jobs j on j.id = d.recognition_job_id
            where j.project_id = ? and d.id = ?
            """, projectId, detectionId);
        return raw == null ? Optional.empty() : Optional.of(new DetectionState(
            detectionId, projectId, raw.get("feature_id", String.class), raw.get("recognition_job_id", UUID.class),
            detectionId, raw.get("bounds", String.class), false, 0, null, null, null, null));
    }

    public boolean hasFeature(UUID projectId, String featureId) {
        return dsl.fetchOne("select exists(select 1 from geoportal.project_features where project_id = ? and feature_id = ?) as present", projectId, featureId)
            .get("present", Boolean.class);
    }

    public void save(DetectionState state) {
        dsl.execute("""
            insert into geoportal.detection_annotations
                (id, project_id, feature_id, recognition_job_id, source_detection_id, bounds, deleted,
                 version, updated_by, updated_at, verified_by, verified_at)
            values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::timestamptz, ?, ?::timestamptz)
            on conflict (id) do update set bounds = excluded.bounds, deleted = excluded.deleted,
                version = excluded.version, updated_by = excluded.updated_by, updated_at = excluded.updated_at,
                verified_by = excluded.verified_by, verified_at = excluded.verified_at
            """, state.id(), state.projectId(), state.featureId(), state.recognitionJobId(), state.sourceDetectionId(),
                state.bounds() == null ? "null" : state.bounds(), state.deleted(), state.version(),
                state.updatedBy(), state.updatedAt(), state.verifiedBy(), state.verifiedAt());
    }

    public void recordHistory(UUID projectId, UUID detectionId, String action, String actor, String before, String after) {
        dsl.execute("""
            insert into geoportal.annotation_history (project_id, detection_id, action, actor, "before", "after")
            values (?, ?, ?, ?, ?::jsonb, ?::jsonb)
            """, projectId, detectionId, action, actor, before, after);
    }

    public List<AnnotationHistoryEntry> history(UUID projectId, UUID detectionId, int limit, int offset) {
        return dsl.fetch("""
            select * from geoportal.annotation_history
            where project_id = ? and (?::uuid is null or detection_id = ?)
            order by occurred_at desc, id desc limit ? offset ?
            """, projectId, detectionId, detectionId, limit, offset)
            .map(row -> new AnnotationHistoryEntry(row.get("id", UUID.class), row.get("detection_id", UUID.class),
                row.get("action", String.class), row.get("actor", String.class), row.get("occurred_at", OffsetDateTime.class),
                row.get("before", String.class), row.get("after", String.class)));
    }

    public List<DetectionState> findAll(UUID projectId) {
        return dsl.fetch("select * from geoportal.detection_annotations where project_id = ? order by feature_id, id", projectId)
            .map(AnnotationRepository::toState);
    }

    private static DetectionState toState(Record row) {
        return new DetectionState(row.get("id", UUID.class), row.get("project_id", UUID.class), row.get("feature_id", String.class),
            row.get("recognition_job_id", UUID.class), row.get("source_detection_id", UUID.class), row.get("bounds", String.class),
            row.get("deleted", Boolean.class), row.get("version", Integer.class), row.get("updated_by", String.class),
            row.get("updated_at", OffsetDateTime.class), row.get("verified_by", String.class), row.get("verified_at", OffsetDateTime.class));
    }
}

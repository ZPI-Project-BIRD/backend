package zpi.project_bird.backend.project;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessingRepository {
    private final DSLContext dsl;
    public ProcessingRepository(DSLContext dsl) { this.dsl = dsl; }

    public DiscoveryStatus discovery(UUID projectId) {
        var row = dsl.fetchOne("select discovery_status, attempts, last_error, locked_until from project_processing where project_id = ?", projectId);
        return row == null ? new DiscoveryStatus("UNKNOWN", 0, null, null)
            : new DiscoveryStatus(row.get("discovery_status", String.class), row.get("attempts", Integer.class),
                row.get("last_error", String.class), row.get("locked_until", OffsetDateTime.class));
    }

    public void start(UUID projectId, UUID token) {
        dsl.execute("""
            insert into project_processing (project_id, discovery_status, attempts, attempt_token, locked_until)
            values (?, 'RUNNING', 1, ?, now() + interval '15 minutes')
            on conflict (project_id) do update set discovery_status = 'RUNNING',
                attempts = project_processing.attempts + 1, attempt_token = excluded.attempt_token,
                locked_until = excluded.locked_until, last_error = null, updated_at = now()
            """, projectId, token);
    }

    public boolean finish(UUID projectId, UUID token, String error) {
        return dsl.execute("""
            update project_processing set discovery_status = ?, last_error = ?, locked_until = null, updated_at = now()
            where project_id = ? and attempt_token = ? and discovery_status = 'RUNNING'
            """, error == null ? "SUCCEEDED" : "FAILED", error, projectId, token) == 1;
    }

    public int retryFailedJobs(UUID projectId) {
        return dsl.execute("""
            update geoportal.recognition_jobs set status = '0_PENDING', started_at = now(), finished_at = null, error = null
            where project_id = ? and status = '3_FAILED'
            """, projectId);
    }

    public JobStates jobStates(UUID projectId) {
        var row = dsl.fetchOne("""
            select count(*) filter (where status = '0_PENDING') as pending,
                   count(*) filter (where status = '1_STARTED') as running,
                   count(*) filter (where status = '2_FINISHED') as finished,
                   count(*) filter (where status = '3_FAILED') as failed
            from geoportal.recognition_jobs where project_id = ?
            """, projectId);
        return new JobStates(row.get("pending", Integer.class), row.get("running", Integer.class),
            row.get("finished", Integer.class), row.get("failed", Integer.class));
    }

    public record DiscoveryStatus(String status, int attempts, String lastError, OffsetDateTime lockedUntil) {}
    public record JobStates(int pending, int running, int finished, int failed) {}
}

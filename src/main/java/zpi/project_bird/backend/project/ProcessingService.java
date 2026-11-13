package zpi.project_bird.backend.project;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import zpi.project_bird.backend.downloader.DownloaderClient;

@Service
public class ProcessingService {
    private final ProjectRepository projects;
    private final ProcessingRepository processing;
    private final DownloaderClient downloader;
    private final TransactionTemplate transactions;

    public ProcessingService(ProjectRepository projects, ProcessingRepository processing,
            DownloaderClient downloader, TransactionTemplate transactions) {
        this.projects = projects;
        this.processing = processing;
        this.downloader = downloader;
        this.transactions = transactions;
    }

    public ProcessingRepository.DiscoveryStatus discover(UUID organizationId, UUID projectId, boolean retry) {
        UUID token = UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            lock(organizationId, projectId);
            var current = processing.discovery(projectId);
            if (current.status().equals("RUNNING") && current.lockedUntil() != null && current.lockedUntil().isAfter(OffsetDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Feature discovery is already running");
            }
            if (retry && current.status().equals("SUCCEEDED")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Feature discovery has already succeeded");
            }
            processing.start(projectId, token);
        });
        try {
            // Do not hold a database transaction or row lock during the external invocation.
            downloader.discoverFeatures(projectId);
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> processing.finish(projectId, token, "Feature discovery invocation failed"));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Project exists, but feature discovery failed. Retry discovery for this project.", exception);
        }
        Boolean finished = transactions.execute(status -> processing.finish(projectId, token, null));
        if (!Boolean.TRUE.equals(finished)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project was deleted or this discovery attempt was superseded");
        }
        return processing.discovery(projectId);
    }

    @Transactional
    public RetryResult retryDetections(UUID organizationId, UUID projectId) {
        lock(organizationId, projectId);
        return new RetryResult(processing.retryFailedJobs(projectId));
    }

    private void lock(UUID organizationId, UUID projectId) {
        projects.lock(organizationId, projectId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Project was not found in this organization"));
    }

    public record RetryResult(int requeuedJobs) {}
}

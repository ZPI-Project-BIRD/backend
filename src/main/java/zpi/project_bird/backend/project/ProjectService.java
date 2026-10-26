package zpi.project_bird.backend.project;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import zpi.project_bird.backend.downloader.DownloaderClient;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final DownloaderClient downloaderClient;
    private final TransactionTemplate transactions;

    public ProjectService(
        ProjectRepository projects,
        DownloaderClient downloaderClient,
        TransactionTemplate transactions
    ) {
        this.projects = projects;
        this.downloaderClient = downloaderClient;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public List<Project> findAll(UUID organizationId) {
        return projects.findAll(organizationId);
    }

    @Transactional(readOnly = true)
    public Project findById(UUID organizationId, UUID projectId) {
        return projects.findById(organizationId, projectId)
            .orElseThrow(() -> notFound(projectId));
    }

    @Transactional(readOnly = true)
    public List<Feature> getFeatures(UUID projectId) {
        return projects.getFeatures(projectId);
    }

    public Project create(
        UUID organizationId,
        String name,
        Project.Coordinates topLeft,
        Project.Coordinates bottomRight
    ) {
        Project project = Objects.requireNonNull(transactions.execute(status ->
            projects.create(organizationId, name, topLeft, bottomRight)));
        downloaderClient.discoverFeatures(project.id());

        return project;
    }

    @Transactional
    public void delete(UUID organizationId, UUID projectId) {
        if (!projects.delete(organizationId, projectId)) {
            throw notFound(projectId);
        }
    }

    private static ResponseStatusException notFound(UUID projectId) {
        return new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Project %s was not found".formatted(projectId));
    }
}

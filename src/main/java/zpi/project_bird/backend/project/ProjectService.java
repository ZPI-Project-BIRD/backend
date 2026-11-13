package zpi.project_bird.backend.project;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final ProcessingService processing;
    private final ProcessingRepository processingRepository;
    private final AnnotationRepository annotations;
    private final TransactionTemplate transactions;

    public ProjectService(
        ProjectRepository projects,
        ProcessingService processing,
        ProcessingRepository processingRepository,
        AnnotationRepository annotations,
        TransactionTemplate transactions
    ) {
        this.projects = projects;
        this.processing = processing;
        this.processingRepository = processingRepository;
        this.annotations = annotations;
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
    public ProjectProgress getProgress(UUID organizationId, UUID projectId) {
        findById(organizationId, projectId);
        return projects.getProgress(projectId).withProcessing(
            processingRepository.discovery(projectId), processingRepository.jobStates(projectId));
    }

    @Transactional(readOnly = true)
    public List<FeatureDetections> getDetections(UUID organizationId, UUID projectId) {
        findById(organizationId, projectId);
        var overrides = annotations.findAll(projectId);
        var overriddenIds = overrides.stream().map(DetectionState::id).collect(java.util.stream.Collectors.toSet());
        var groups = new java.util.TreeMap<String, List<FeatureDetections.Detection>>();
        for (var feature : projects.getDetections(projectId)) {
            for (var detection : feature.detections()) {
                if (!overriddenIds.contains(detection.id())) {
                    groups.computeIfAbsent(feature.featureId(), key -> new java.util.ArrayList<>()).add(detection);
                }
            }
        }
        for (var annotation : overrides) {
            if (!annotation.deleted()) {
                groups.computeIfAbsent(annotation.featureId(), key -> new java.util.ArrayList<>()).add(annotation.toDetection());
            }
        }
        return groups.entrySet().stream().map(entry -> new FeatureDetections(entry.getKey(), entry.getValue())).toList();
    }

    @Transactional(readOnly = true)
    public List<Feature> getFeatures(UUID organizationId, UUID projectId) {
        findById(organizationId, projectId);
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
        processing.discover(organizationId, project.id(), false);

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

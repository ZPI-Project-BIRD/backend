package zpi.project_bird.backend.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import zpi.project_bird.backend.downloader.DownloaderClient;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTests {
    @Mock
    private ProjectRepository projects;

    @Mock
    private DownloaderClient downloaderClient;

    @Mock
    private TransactionTemplate transactions;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void returnsProgressForProjectInOrganization() {
        UUID organizationId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = new Project(
            projectId,
            "Test project",
            organizationId,
            new Project.Coordinates(16, 51),
            new Project.Coordinates(17, 50));
        ProjectProgress expected = ProjectProgress.fromCounts(4, 4, 1);

        when(projects.findById(organizationId, projectId)).thenReturn(Optional.of(project));
        when(projects.getProgress(projectId)).thenReturn(expected);

        assertEquals(expected, projectService.getProgress(organizationId, projectId));
    }

    @Test
    void doesNotExposeProgressForProjectOutsideOrganization() {
        UUID organizationId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projects.findById(organizationId, projectId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> projectService.getProgress(organizationId, projectId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(projects, never()).getProgress(projectId);
    }

    @Test
    void returnsDetectionsForProjectInOrganization() {
        UUID organizationId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = new Project(
            projectId,
            "Test project",
            organizationId,
            new Project.Coordinates(16, 51),
            new Project.Coordinates(17, 50));
        List<FeatureDetections> expected = List.of(new FeatureDetections(
            "feature-1",
            List.of(new FeatureDetections.Detection(UUID.randomUUID(), UUID.randomUUID(), "{\"x\":1}"))));

        when(projects.findById(organizationId, projectId)).thenReturn(Optional.of(project));
        when(projects.getDetections(projectId)).thenReturn(expected);

        assertEquals(expected, projectService.getDetections(organizationId, projectId));
    }

    @Test
    void doesNotExposeDetectionsForProjectOutsideOrganization() {
        UUID organizationId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projects.findById(organizationId, projectId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> projectService.getDetections(organizationId, projectId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(projects, never()).getDetections(projectId);
    }
}

package zpi.project_bird.backend.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class ProcessingServiceTests {
    @Mock ProjectRepository projects;
    @Mock ProcessingRepository processing;
    @Mock DownloaderClient downloader;
    @Mock TransactionTemplate transactions;
    @InjectMocks ProcessingService service;

    final UUID organizationId = UUID.randomUUID();
    final UUID projectId = UUID.randomUUID();

    void projectExists() {
        when(projects.lock(organizationId, projectId)).thenReturn(Optional.of(new Project(projectId, "Area", organizationId,
            new Project.Coordinates(16, 51), new Project.Coordinates(17, 50))));
    }

    void discoveryFinished() {
        when(processing.discovery(projectId)).thenReturn(new ProcessingRepository.DiscoveryStatus("SUCCEEDED", 1, null, null));
    }

    void processingIdle() {
        projectExists();
        discoveryFinished();
        when(processing.jobStates(projectId)).thenReturn(new ProcessingRepository.JobStates(0, 0, 1, 1));
        when(projects.getProgress(projectId)).thenReturn(ProjectProgress.fromCounts(2, 2, 1));
    }

    @Test void rerunLocksJobsBeforeCheckingAndRequeuingThem() {
        processingIdle();
        when(processing.rerunJobs(projectId)).thenReturn(2);
        assertEquals(2, service.rerunDetections(organizationId, projectId).requeuedJobs());
        var order = inOrder(projects, processing);
        order.verify(projects).lock(organizationId, projectId);
        order.verify(processing).lockJobs(projectId);
        order.verify(processing).jobStates(projectId);
        order.verify(processing).rerunJobs(projectId);
        verify(processing, never()).retryFailedJobs(projectId);
    }

    @Test void rerunCannotCrossOrganizationBoundaries() {
        when(projects.lock(organizationId, projectId)).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class, () -> service.rerunDetections(organizationId, projectId));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(processing, never()).lockJobs(projectId);
        verify(processing, never()).rerunJobs(projectId);
    }

    @Test void rerunRejectsDiscoveryThatCanStillAddJobs() {
        projectExists();
        for (String status : new String[]{"PENDING", "RUNNING"}) {
            when(processing.discovery(projectId)).thenReturn(new ProcessingRepository.DiscoveryStatus(status, 1, null, null));
            assertConflict();
        }
        verify(processing, never()).lockJobs(projectId);
        verify(processing, never()).rerunJobs(projectId);
    }

    @Test void rerunRejectsPendingOrRunningDetection() {
        projectExists();
        discoveryFinished();
        when(projects.getProgress(projectId)).thenReturn(ProjectProgress.fromCounts(2, 2, 1));
        for (var jobs : new ProcessingRepository.JobStates[]{
                new ProcessingRepository.JobStates(1, 0, 1, 0), new ProcessingRepository.JobStates(0, 1, 1, 0)}) {
            when(processing.jobStates(projectId)).thenReturn(jobs);
            assertConflict();
        }
        verify(processing, never()).rerunJobs(projectId);
    }

    @Test void rerunWaitsForAllSourceDownloads() {
        processingIdle();
        when(projects.getProgress(projectId)).thenReturn(ProjectProgress.fromCounts(2, 1, 1));
        assertConflict();
        verify(processing, never()).rerunJobs(projectId);
    }

    @Test void rerunWithoutJobsReturnsAConflict() {
        processingIdle();
        when(processing.jobStates(projectId)).thenReturn(new ProcessingRepository.JobStates(0, 0, 0, 0));
        when(processing.rerunJobs(projectId)).thenReturn(0);
        assertConflict();
    }

    void assertConflict() {
        var error = assertThrows(ResponseStatusException.class, () -> service.rerunDetections(organizationId, projectId));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }
}

package zpi.project_bird.backend.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProjectProgressTests {
    @Test
    void reportsDownloadAndRecognitionProgressConcurrently() {
        ProjectProgress progress = ProjectProgress.fromCounts(9, 5, 3);

        assertEquals(
            new ProjectProgress.StageProgress(5, 9),
            progress.featureDownloads());
        assertEquals(
            new ProjectProgress.StageProgress(3, 9),
            progress.recognitionJobs());
    }

    @Test
    void reportsRecognitionProgressWhileOtherFeaturesAreDownloading() {
        ProjectProgress progress = ProjectProgress.fromCounts(9, 4, 2);

        assertEquals(4, progress.featureDownloads().completed());
        assertEquals(2, progress.recognitionJobs().completed());
    }

    @Test
    void projectWithoutFeaturesIsComplete() {
        assertEquals(
            new ProjectProgress(
                new ProjectProgress.StageProgress(0, 0),
                new ProjectProgress.StageProgress(0, 0)),
            ProjectProgress.fromCounts(0, 0, 0));
    }

    @Test
    void rejectsInconsistentCounts() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ProjectProgress.fromCounts(2, 1, 2));
    }
}

package zpi.project_bird.backend.project;

public record ProjectProgress(
    StageProgress featureDownloads,
    StageProgress recognitionJobs,
    ProcessingRepository.DiscoveryStatus discovery,
    ProcessingRepository.JobStates jobStates,
    ProcessingRepository.QueuePosition queue
) {
    public ProjectProgress(StageProgress featureDownloads, StageProgress recognitionJobs) {
        this(featureDownloads, recognitionJobs, null, null, null);
    }

    public ProjectProgress withProcessing(
        ProcessingRepository.DiscoveryStatus discovery,
        ProcessingRepository.JobStates jobStates,
        ProcessingRepository.QueuePosition queue
    ) {
        return new ProjectProgress(featureDownloads, recognitionJobs, discovery, jobStates, queue);
    }
    public static ProjectProgress fromCounts(
        int totalFeatures,
        int downloadedFeatures,
        int completedRecognitionJobs
    ) {
        validateCounts(totalFeatures, downloadedFeatures, completedRecognitionJobs);

        return new ProjectProgress(
            new StageProgress(downloadedFeatures, totalFeatures),
            new StageProgress(completedRecognitionJobs, totalFeatures));
    }

    private static void validateCounts(
        int totalFeatures,
        int downloadedFeatures,
        int completedRecognitionJobs
    ) {
        if (totalFeatures < 0
            || downloadedFeatures < 0
            || downloadedFeatures > totalFeatures
            || completedRecognitionJobs < 0
            || completedRecognitionJobs > totalFeatures
            || completedRecognitionJobs > downloadedFeatures
        ) {
            throw new IllegalArgumentException(
                "Progress counts must satisfy recognition <= downloads <= total");
        }
    }

    public record StageProgress(int completed, int total) {
    }
}

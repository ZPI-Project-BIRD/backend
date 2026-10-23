package zpi.project_bird.backend.downloader;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import zpi.project_bird.backend.downloader.v1.*;

@Service
public class DownloaderClient {
    private final DownloaderServiceGrpc.DownloaderServiceBlockingStub downloader;

    public DownloaderClient(
        DownloaderServiceGrpc.DownloaderServiceBlockingStub downloader) {
        this.downloader = downloader;
    }

    public CreateDownloadResponse createDownload(UUID projectId, String layer) {
        CreateDownloadRequest request = CreateDownloadRequest.newBuilder()
            .setProjectId(projectId.toString())
            .setLayer(layer)
            .build();

        return downloader
            .withDeadlineAfter(10, TimeUnit.SECONDS)
            .createDownload(request);
    }

    public RetryFeatureResponse.Action retryFeature(
        UUID projectId,
        UUID featureId
    ) {
        RetryFeatureRequest request = RetryFeatureRequest.newBuilder()
            .setProjectId(projectId.toString())
            .setFeatureId(featureId.toString())
            .build();

        return downloader
            .withDeadlineAfter(10, TimeUnit.SECONDS)
            .retryFeature(request)
            .getAction();
    }
}
package zpi.project_bird.backend.downloader;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

@Service
public class DownloaderClient {
    private final LambdaClient lambda;
    private final String featureDiscoveryFunctionName;

    public DownloaderClient(
            LambdaClient lambda,
            @Value("${downloader.feature-discovery-function-name}")
            String featureDiscoveryFunctionName) {
        this.lambda = lambda;
        this.featureDiscoveryFunctionName = featureDiscoveryFunctionName;
    }

    public void discoverFeatures(UUID projectId) {
        InvokeRequest request = InvokeRequest.builder()
            .functionName(featureDiscoveryFunctionName)
            .invocationType(InvocationType.REQUEST_RESPONSE)
            .payload(SdkBytes.fromUtf8String("{\"ProjectID\":\"%s\"}".formatted(projectId)))
            .build();

        InvokeResponse response = lambda.invoke(request);

        if (response.functionError() != null) {
            String errorPayload = response.payload() == null ? "": ": " + response.payload().asUtf8String();
            throw new IllegalStateException("Feature discovery failed%s".formatted(errorPayload));
        }
    }
}

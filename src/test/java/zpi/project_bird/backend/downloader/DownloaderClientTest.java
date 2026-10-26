package zpi.project_bird.backend.downloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

class DownloaderClientTest {
    private static final String FUNCTION_NAME = "feature-discovery";

    private final LambdaClient lambda = mock(LambdaClient.class);
    private final DownloaderClient client = new DownloaderClient(lambda, FUNCTION_NAME);

    @Test
    void invokesFeatureDiscoverySynchronously() {
        UUID projectId = UUID.fromString("78cd8a75-bdd8-49d9-9461-cbe7a8e726ab");
        when(lambda.invoke(any(InvokeRequest.class)))
            .thenReturn(InvokeResponse.builder().statusCode(200).build());

        client.discoverFeatures(projectId);

        ArgumentCaptor<InvokeRequest> requestCaptor =
            ArgumentCaptor.forClass(InvokeRequest.class);
        verify(lambda).invoke(requestCaptor.capture());
        InvokeRequest request = requestCaptor.getValue();
        assertThat(request.functionName()).isEqualTo(FUNCTION_NAME);
        assertThat(request.invocationType()).isEqualTo(InvocationType.REQUEST_RESPONSE);
        assertThat(request.payload().asUtf8String()).isEqualTo(
            "{\"ProjectID\":\"78cd8a75-bdd8-49d9-9461-cbe7a8e726ab\"}");
    }

    @Test
    void reportsFunctionErrors() {
        when(lambda.invoke(any(InvokeRequest.class))).thenReturn(InvokeResponse.builder()
            .statusCode(200)
            .functionError("Handled")
            .payload(SdkBytes.fromUtf8String("{\"errorMessage\":\"project not found\"}"))
            .build());

        assertThatThrownBy(() -> client.discoverFeatures(UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Feature discovery failed")
            .hasMessageContaining("project not found");
    }
}
